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

package io.getstream.whatsappclone.chats.initializer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.startup.Initializer
import io.getstream.android.push.firebase.FirebasePushDeviceGenerator
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.logger.ChatLogLevel
import io.getstream.chat.android.client.notifications.handler.NotificationConfig
import io.getstream.chat.android.client.notifications.handler.NotificationHandlerFactory
import io.getstream.chat.android.offline.plugin.factory.StreamOfflinePluginFactory
import io.getstream.chat.android.state.plugin.config.StatePluginConfig
import io.getstream.chat.android.state.plugin.factory.StreamStatePluginFactory
import io.getstream.log.streamLog
import io.getstream.whatsappclone.chats.BuildConfig

/**
 * Initializes Stream ChatClient only. User connection happens after auth via StreamSessionManager.
 */
class StreamChatInitializer : Initializer<Unit> {

  override fun create(context: Context) {
    streamLog { "StreamChatInitializer is initialized" }

    // ALL floods the main thread during scroll/sync and causes stutter in debug.
    val logLevel = if (BuildConfig.DEBUG) ChatLogLevel.ERROR else ChatLogLevel.NOTHING
    val offlinePluginFactory = StreamOfflinePluginFactory(
      appContext = context
    )
    val statePluginFactory = StreamStatePluginFactory(
      config = StatePluginConfig(
        backgroundSyncEnabled = true,
        userPresence = false
      ),
      appContext = context
    )

    val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    // Push is best-effort: placeholder Firebase configs must not crash app open.
    val notificationConfig = try {
      NotificationConfig(
        pushNotificationsEnabled = true,
        pushDeviceGenerators = listOf(
          FirebasePushDeviceGenerator(
            providerName = "firebase"
          )
        ),
        shouldShowNotificationOnPush = {
          prefs.getBoolean(KEY_MSG_NOTIFICATIONS, true)
        }
      )
    } catch (error: Throwable) {
      streamLog { "Firebase push disabled: ${error.message}" }
      NotificationConfig(pushNotificationsEnabled = false)
    }

    // Stream Chat SDK overload has no notificationTextFormatter; preview text uses SDK defaults.
    val notificationHandler = try {
      NotificationHandlerFactory.createNotificationHandler(
        context = context,
        notificationConfig = notificationConfig,
        newMessageIntent = { _, channel ->
          openChatIntent(context, channel.cid)
        },
        notificationChannel = {
          NotificationChannel(
            PUSH_CHANNEL_ID,
            "Chat messages",
            NotificationManager.IMPORTANCE_HIGH
          ).apply {
            description = "New message alerts"
            enableVibration(true)
          }
        }
      )
    } catch (error: Throwable) {
      streamLog { "Custom notification handler unavailable: ${error.message}" }
      null
    }

    val builder = ChatClient.Builder(BuildConfig.STREAM_API_KEY, context)
      .withPlugins(offlinePluginFactory, statePluginFactory)
      .logLevel(logLevel)

    if (notificationHandler != null) {
      builder.notifications(notificationConfig, notificationHandler)
    } else {
      builder.notifications(notificationConfig)
    }

    builder.build()
  }

  override fun dependencies(): List<Class<out Initializer<*>>> =
    listOf(StreamLogInitializer::class.java)

  private fun openChatIntent(context: Context, channelCid: String): Intent {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return launch.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_OPEN_CHANNEL, channelCid)
    }
  }

  companion object {
    // Must match SettingsRepository / MainActivity extras.
    private const val PREFS_SETTINGS = "batchit_settings"
    private const val KEY_MSG_NOTIFICATIONS = "notifications_messages"
    private const val PUSH_CHANNEL_ID = "batchit_messages"
    private const val EXTRA_OPEN_CHANNEL = "batchit_open_channel"
  }
}
