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

package io.getstream.whatsappclone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.notifications.NotificationDeepLink
import io.getstream.whatsappclone.notifications.NotificationDeepLinkBus
import io.getstream.whatsappclone.ui.WhatsAppCloneMain
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject
  internal lateinit var appComposeNavigator: AppComposeNavigator

  @Inject
  internal lateinit var deepLinkBus: NotificationDeepLinkBus

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, true)

    setContent { WhatsAppCloneMain(appComposeNavigator) }
    dispatchNotificationIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    dispatchNotificationIntent(intent)
  }

  private fun dispatchNotificationIntent(intent: Intent?) {
    if (intent == null) return
    val channelCid = intent.getStringExtra(EXTRA_OPEN_CHANNEL)
    if (!channelCid.isNullOrBlank()) {
      deepLinkBus.emit(NotificationDeepLink.OpenChat(channelCid))
      return
    }
    val callId = intent.getStringExtra(EXTRA_OPEN_CALL)
    if (!callId.isNullOrBlank()) {
      // Only open the in-call screen after an explicit Accept. Notification taps /
      // full-screen intent just bring the app up so IncomingCallOverlay can ring.
      val alreadyAccepted = intent.getBooleanExtra(EXTRA_CALL_ACCEPTED, false)
      if (alreadyAccepted) {
        deepLinkBus.emit(
          NotificationDeepLink.OpenVideoCall(
            callId = callId,
            video = intent.getBooleanExtra(EXTRA_CALL_VIDEO, true)
          )
        )
      }
      return
    }
    if (intent.getBooleanExtra(EXTRA_OPEN_CALLS_TAB, false)) {
      deepLinkBus.emit(NotificationDeepLink.OpenCallsTab)
    }
  }

  companion object {
    const val EXTRA_OPEN_CHANNEL = "batchit_open_channel"
    const val EXTRA_OPEN_CALL = "batchit_open_call"
    const val EXTRA_CALL_VIDEO = "batchit_call_video"
    const val EXTRA_CALL_ACCEPTED = "batchit_call_accepted"
    const val EXTRA_OPEN_CALLS_TAB = "batchit_open_calls_tab"

    fun openAppIntent(context: Context): Intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

    fun openChatIntent(context: Context, channelCid: String): Intent =
      openAppIntent(context).apply {
        putExtra(EXTRA_OPEN_CHANNEL, channelCid)
      }

    /** Brings app to foreground for ringing UI — does not answer the call. */
    fun openIncomingCallIntent(context: Context, callId: String, isVideo: Boolean): Intent =
      openAppIntent(context).apply {
        putExtra(EXTRA_OPEN_CALL, callId)
        putExtra(EXTRA_CALL_VIDEO, isVideo)
        putExtra(EXTRA_CALL_ACCEPTED, false)
      }

    /** Opens active call UI after the user (or notification Accept) answered. */
    fun openVideoCallIntent(context: Context, callId: String, isVideo: Boolean): Intent =
      openAppIntent(context).apply {
        putExtra(EXTRA_OPEN_CALL, callId)
        putExtra(EXTRA_CALL_VIDEO, isVideo)
        putExtra(EXTRA_CALL_ACCEPTED, true)
      }

    fun openCallsTabIntent(context: Context): Intent =
      openAppIntent(context).apply {
        putExtra(EXTRA_OPEN_CALLS_TAB, true)
      }
  }
}
