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

import android.content.Context
import androidx.startup.Initializer
import io.getstream.android.push.firebase.FirebasePushDeviceGenerator
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.logger.ChatLogLevel
import io.getstream.chat.android.client.notifications.handler.NotificationConfig
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

    val logLevel = if (BuildConfig.DEBUG) ChatLogLevel.ALL else ChatLogLevel.NOTHING
    val offlinePluginFactory = StreamOfflinePluginFactory(
      appContext = context
    )
    val statePluginFactory = StreamStatePluginFactory(
      config = StatePluginConfig(
        backgroundSyncEnabled = true,
        userPresence = true
      ),
      appContext = context
    )

    val notificationConfig = NotificationConfig(
      pushNotificationsEnabled = true,
      pushDeviceGenerators = listOf(
        FirebasePushDeviceGenerator(
          context = context,
          providerName = "firebase"
        )
      )
    )

    ChatClient.Builder(BuildConfig.STREAM_API_KEY, context)
      .withPlugins(offlinePluginFactory, statePluginFactory)
      .notifications(notificationConfig)
      .logLevel(logLevel)
      .build()
  }

  override fun dependencies(): List<Class<out Initializer<*>>> =
    listOf(StreamLogInitializer::class.java)
}
