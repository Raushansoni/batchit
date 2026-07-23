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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.getstream.log.streamLog
import io.getstream.video.android.core.StreamVideo
import io.getstream.whatsappclone.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles Accept / Decline actions from the incoming-call notification.
 */
class CallActionReceiver : BroadcastReceiver() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
    if (callId.isBlank()) return

    val pendingResult = goAsync()
    scope.launch {
      try {
        when (action) {
          ACTION_ACCEPT -> handleAccept(context, callId, intent.getBooleanExtra(EXTRA_VIDEO, true))
          ACTION_DECLINE -> handleDecline(context, callId)
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  private suspend fun handleAccept(context: Context, callId: String, isVideo: Boolean) {
    val call = runCatching {
      StreamVideo.instance().state.ringingCall.value
        ?: StreamVideo.instance().call(type = "default", id = callId)
    }.getOrNull()

    if (call != null) {
      runCatching { call.accept() }
      runCatching { call.join() }
    }

    BatchItNotifier(context.applicationContext).cancelIncomingCallNotification(callId)
    context.startActivity(
      MainActivity.openVideoCallIntent(context, callId, isVideo).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      )
    )
  }

  private suspend fun handleDecline(context: Context, callId: String) {
    val call = runCatching {
      StreamVideo.instance().state.ringingCall.value
        ?: StreamVideo.instance().call(type = "default", id = callId)
    }.getOrNull()

    if (call != null) {
      runCatching { call.reject() }
      runCatching { call.leave() }
    }

    BatchItNotifier(context.applicationContext).cancelIncomingCallNotification(callId)
    streamLog { "Declined incoming call from notification: $callId" }
  }

  companion object {
    const val ACTION_ACCEPT = "com.batchit.app.action.ACCEPT_CALL"
    const val ACTION_DECLINE = "com.batchit.app.action.DECLINE_CALL"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_VIDEO = "video"

    fun acceptIntent(context: Context, callId: String, isVideo: Boolean): Intent =
      Intent(context, CallActionReceiver::class.java).apply {
        action = ACTION_ACCEPT
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_VIDEO, isVideo)
      }

    fun declineIntent(context: Context, callId: String): Intent =
      Intent(context, CallActionReceiver::class.java).apply {
        action = ACTION_DECLINE
        putExtra(EXTRA_CALL_ID, callId)
      }
  }
}
