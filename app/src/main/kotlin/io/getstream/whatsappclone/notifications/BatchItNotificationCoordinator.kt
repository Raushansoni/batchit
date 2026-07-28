/*
 * Copyright 2023 Stream.IO, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.whatsappclone.notifications

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.events.NewMessageEvent
import io.getstream.chat.android.client.utils.observable.Disposable
import io.getstream.log.streamLog
import io.getstream.video.android.core.RingingState
import io.getstream.video.android.core.StreamVideo
import io.getstream.whatsappclone.auth.StreamSessionManager
import io.getstream.whatsappclone.data.repository.CallHistoryRepository
import io.getstream.whatsappclone.settings.SettingsRepository
import io.getstream.whatsappclone.video.resolveIsVideoCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * WhatsApp-style tray notifications for new messages and call status while the app process is alive.
 * Background/killed delivery still relies on Stream FCM push configured in [io.getstream.whatsappclone.chats.initializer.StreamChatInitializer].
 */
@Singleton
class BatchItNotificationCoordinator @Inject constructor(
  private val chatClient: ChatClient,
  private val streamSessionManager: StreamSessionManager,
  private val settingsRepository: SettingsRepository,
  private val callHistoryRepository: CallHistoryRepository,
  private val notifier: BatchItNotifier
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val activeChannelCid = MutableStateFlow<String?>(null)
  private val appInForeground = MutableStateFlow(false)

  private var messageDisposable: Disposable? = null
  private var callWatchJob: Job? = null
  private var videoConnectionWatchJob: Job? = null
  private var historyWatchJob: Job? = null
  private var started = false
  private var lastRingingCallId: String? = null
  private val notifiedMissedCallIds = mutableSetOf<String>()

  private val lifecycleObserver = object : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
      appInForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
      appInForeground.value = false
    }
  }

  fun start() {
    if (started) return
    started = true
    ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    subscribeMessages()
    watchIncomingCalls()
    watchVideoConnection()
    watchMissedCalls()
    streamLog { "BatchItNotificationCoordinator started" }
  }

  fun stop() {
    if (!started) return
    started = false
    ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    messageDisposable?.dispose()
    messageDisposable = null
    callWatchJob?.cancel()
    callWatchJob = null
    videoConnectionWatchJob?.cancel()
    videoConnectionWatchJob = null
    historyWatchJob?.cancel()
    historyWatchJob = null
    lastRingingCallId = null
  }

  fun setActiveChannelCid(cid: String?) {
    activeChannelCid.value = cid
    if (!cid.isNullOrBlank()) {
      notifier.cancelMessageNotification(cid)
    }
  }

  private fun subscribeMessages() {
    messageDisposable?.dispose()
    messageDisposable = chatClient.subscribeFor(NewMessageEvent::class.java) { event ->
      val newMessageEvent = event as? NewMessageEvent ?: return@subscribeFor
      scope.launch {
        handleNewMessage(newMessageEvent)
      }
    }
  }

  private suspend fun handleNewMessage(event: NewMessageEvent) {
    val settings = settingsRepository.currentNotificationSettings()
    if (!settings.messageNotifications) return

    val me = chatClient.getCurrentUser()?.id ?: return
    val message = event.message
    if (message.user.id == me) return
    if (message.type != "regular" && message.type != "reply") return

    val cid = event.cid
    if (cid == activeChannelCid.value && appInForeground.value) return

    val muted = chatClient.getCurrentUser()
      ?.channelMutes
      ?.any { it.channel?.cid == cid } == true
    if (muted) return

    val senderName = message.user.name.ifBlank { message.user.id }
    val body = when {
      message.text.isNotBlank() -> message.text
      message.attachments.isNotEmpty() -> message.attachments.firstOrNull()?.title
        ?: message.attachments.firstOrNull()?.type
        ?: "Attachment"
      else -> ""
    }

    notifier.showMessageNotification(
      channelCid = cid,
      senderName = senderName,
      body = body,
      showPreview = settings.notificationPreview
    )
  }

  private fun watchIncomingCalls() {
    callWatchJob?.cancel()
    callWatchJob = scope.launch {
      streamVideoInstances()
        .flatMapLatest { video ->
          video?.state?.ringingCall ?: flowOf(null)
        }
        .collectLatest { call ->
          val settings = settingsRepository.currentNotificationSettings()
          if (call == null) {
            lastRingingCallId?.let { notifier.cancelIncomingCallNotification(it) }
            lastRingingCallId = null
            return@collectLatest
          }

          if (!settings.callNotifications) return@collectLatest

          val callId = call.id
          if (callId == lastRingingCallId) return@collectLatest
          lastRingingCallId = callId

          val me = runCatching { StreamVideo.instance().user.id }.getOrNull().orEmpty()
          val ringingState = call.state.ringingState.value
          val createdById = call.state.createdBy.value?.id
          if (ringingState is RingingState.Outgoing || createdById == me) return@collectLatest

          // Foreground overlay already covers the ringing UI; still notify when backgrounded.
          if (appInForeground.value) return@collectLatest

          val peer = call.state.members.value.map { it.user }.firstOrNull { it.id != me }
          val isVideo = call.resolveIsVideoCall()

          notifier.showIncomingCallNotification(
            callId = callId,
            callerName = peer?.name.orEmpty().ifBlank { peer?.id.orEmpty() },
            isVideo = isVideo
          )
        }
    }
  }

  private fun watchVideoConnection() {
    videoConnectionWatchJob?.cancel()
    videoConnectionWatchJob = scope.launch {
      while (started) {
        if (runCatching { StreamVideo.instance() }.isFailure) {
          streamSessionManager.ensureVideoConnected()
        }
        kotlinx.coroutines.delay(30_000)
      }
    }
  }

  private fun streamVideoInstances() = flow {
    while (started) {
      emit(runCatching { StreamVideo.instance() }.getOrNull())
      kotlinx.coroutines.delay(1_500)
    }
  }.distinctUntilChanged()

  private fun watchMissedCalls() {
    historyWatchJob?.cancel()
    historyWatchJob = scope.launch {
      callHistoryRepository.observeRecords()
        .distinctUntilChanged()
        .collectLatest { records ->
          val settings = settingsRepository.currentNotificationSettings()
          if (!settings.callNotifications) return@collectLatest

          records
            .filter { it.missed && !it.outgoing && it.callId !in notifiedMissedCallIds }
            .forEach { record ->
              notifiedMissedCallIds.add(record.callId)
              // Skip historical entries loaded at cold start (older than ~15s).
              if (System.currentTimeMillis() - record.startedAt > 15_000) return@forEach
              notifier.cancelIncomingCallNotification(record.callId)
              notifier.showMissedCallNotification(
                callId = record.callId,
                peerName = record.peerName.ifBlank { record.peerId },
                isVideo = record.video
              )
            }
        }
    }
  }
}
