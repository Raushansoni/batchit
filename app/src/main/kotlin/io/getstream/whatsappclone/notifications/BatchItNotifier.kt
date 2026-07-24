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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.whatsappclone.MainActivity
import io.getstream.whatsappclone.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatchItNotifier @Inject constructor(
  @ApplicationContext private val context: Context
) {

  private val manager = NotificationManagerCompat.from(context)

  init {
    ensureChannels()
  }

  fun showMessageNotification(
    channelCid: String,
    senderName: String,
    body: String,
    showPreview: Boolean
  ) {
    val title = senderName.ifBlank { context.getString(R.string.notification_new_message) }
    val text = if (showPreview) {
      body.ifBlank { context.getString(R.string.notification_message_attachment) }
    } else {
      context.getString(R.string.notification_new_message)
    }

    val contentIntent = PendingIntent.getActivity(
      context,
      channelCid.hashCode(),
      MainActivity.openChatIntent(context, channelCid),
      pendingFlags()
    )

    val sender = Person.Builder().setName(title).setImportant(true).build()
    val style = NotificationCompat.MessagingStyle(sender)
      .setConversationTitle(title)
      .addMessage(text, System.currentTimeMillis(), sender)

    val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
      .setSmallIcon(R.drawable.ic_message_black_24dp)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(style)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .setGroup(GROUP_MESSAGES)
      .setNumber(1)
      .build()

    notifySafely(notificationIdForMessage(channelCid), notification)
    showMessageSummary()
  }

  fun showIncomingCallNotification(
    callId: String,
    callerName: String,
    isVideo: Boolean
  ) {
    val title = callerName.ifBlank { context.getString(R.string.notification_incoming_call) }
    val text = if (isVideo) {
      context.getString(R.string.notification_incoming_video_call)
    } else {
      context.getString(R.string.notification_incoming_voice_call)
    }

    val contentIntent = PendingIntent.getActivity(
      context,
      callId.hashCode(),
      MainActivity.openIncomingCallIntent(context, callId, isVideo),
      pendingFlags()
    )

    val acceptIntent = PendingIntent.getBroadcast(
      context,
      callId.hashCode() + 1,
      CallActionReceiver.acceptIntent(context, callId, isVideo),
      pendingFlags()
    )
    val declineIntent = PendingIntent.getBroadcast(
      context,
      callId.hashCode() + 2,
      CallActionReceiver.declineIntent(context, callId),
      pendingFlags()
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
      .setSmallIcon(R.drawable.ic_phone_black_24dp)
      .setContentTitle(title)
      .setContentText(text)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setOngoing(true)
      .setAutoCancel(false)
      .setContentIntent(contentIntent)
      .setFullScreenIntent(contentIntent, true)
      .addAction(
        R.drawable.ic_phone_black_24dp,
        context.getString(R.string.notification_call_accept),
        acceptIntent
      )
      .addAction(
        R.drawable.ic_phone_black_24dp,
        context.getString(R.string.notification_call_decline),
        declineIntent
      )
      .setTimeoutAfter(60_000)
      .build()

    notifySafely(notificationIdForCall(callId), notification)
  }

  fun showMissedCallNotification(
    callId: String,
    peerName: String,
    isVideo: Boolean
  ) {
    val title = peerName.ifBlank { context.getString(R.string.notification_missed_call) }
    val text = if (isVideo) {
      context.getString(R.string.notification_missed_video_call)
    } else {
      context.getString(R.string.notification_missed_voice_call)
    }

    val contentIntent = PendingIntent.getActivity(
      context,
      callId.hashCode() + 10,
      MainActivity.openCallsTabIntent(context),
      pendingFlags()
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
      .setSmallIcon(R.drawable.ic_phone_black_24dp)
      .setContentTitle(title)
      .setContentText(text)
      .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .build()

    notifySafely(notificationIdForMissedCall(callId), notification)
  }

  fun cancelMessageNotification(channelCid: String) {
    manager.cancel(notificationIdForMessage(channelCid))
  }

  fun cancelIncomingCallNotification(callId: String) {
    manager.cancel(notificationIdForCall(callId))
  }

  private fun showMessageSummary() {
    val summary = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
      .setSmallIcon(R.drawable.ic_message_black_24dp)
      .setContentTitle(context.getString(R.string.app_name))
      .setContentText(context.getString(R.string.notification_new_messages))
      .setGroup(GROUP_MESSAGES)
      .setGroupSummary(true)
      .setAutoCancel(true)
      .setContentIntent(
        PendingIntent.getActivity(
          context,
          SUMMARY_ID,
          MainActivity.openAppIntent(context),
          pendingFlags()
        )
      )
      .build()
    notifySafely(SUMMARY_ID, summary)
  }

  private fun ensureChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val systemManager = context.getSystemService(NotificationManager::class.java) ?: return

    val messages = NotificationChannel(
      CHANNEL_MESSAGES,
      context.getString(R.string.notification_channel_messages),
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = context.getString(R.string.notification_channel_messages_desc)
      enableVibration(true)
      setShowBadge(true)
    }

    val calls = NotificationChannel(
      CHANNEL_CALLS,
      context.getString(R.string.notification_channel_calls),
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = context.getString(R.string.notification_channel_calls_desc)
      enableVibration(true)
      setShowBadge(true)
    }

    systemManager.createNotificationChannel(messages)
    systemManager.createNotificationChannel(calls)
  }

  private fun notifySafely(id: Int, notification: android.app.Notification) {
    runCatching { manager.notify(id, notification) }
  }

  private fun pendingFlags(): Int =
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

  companion object {
    const val CHANNEL_MESSAGES = "batchit_messages"
    const val CHANNEL_CALLS = "batchit_calls"
    private const val GROUP_MESSAGES = "batchit_messages_group"
    private const val SUMMARY_ID = 9001

    fun notificationIdForMessage(channelCid: String): Int =
      10_000 + (channelCid.hashCode() and 0xffff)

    fun notificationIdForCall(callId: String): Int =
      20_000 + (callId.hashCode() and 0xffff)

    fun notificationIdForMissedCall(callId: String): Int =
      30_000 + (callId.hashCode() and 0xffff)
  }
}
