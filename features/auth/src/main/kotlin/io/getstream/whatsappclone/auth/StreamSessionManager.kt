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

package io.getstream.whatsappclone.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.android.push.firebase.FirebasePushDeviceGenerator
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.models.User
import io.getstream.log.streamLog
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.StreamVideoBuilder
import io.getstream.video.android.core.notifications.NotificationConfig
import io.getstream.video.android.model.User as VideoUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PersistedStreamSession(
  val userId: String,
  val name: String,
  val image: String,
  val token: String
)

@Singleton
class StreamSessionManager @Inject constructor(
  @ApplicationContext private val context: Context
) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val videoConnectMutex = Mutex()

  @Volatile
  private var cachedToken: String? = null

  @Volatile
  private var tokenRefresher: (() -> String)? = null

  @Volatile
  private var pendingVideoUser: VideoUser? = null

  fun getPersistedSession(): PersistedStreamSession? {
    val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
    val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
    return PersistedStreamSession(
      userId = userId,
      name = prefs.getString(KEY_NAME, null).orEmpty(),
      image = prefs.getString(KEY_IMAGE, null).orEmpty(),
      token = token
    )
  }

  suspend fun connectDemoUser(chatClient: ChatClient) {
    val existing = chatClient.getCurrentUser()
    if (existing?.id == DEMO_USER_ID) {
      cachedToken = chatClient.devToken(DEMO_USER_ID)
      scheduleVideoConnect(
        userId = existing.id,
        name = existing.name,
        image = existing.image,
        token = StreamVideo.devToken(existing.id)
      )
      return
    }

    val user = User(
      id = DEMO_USER_ID,
      name = DEMO_USER_NAME,
      image = DEMO_USER_IMAGE
    )
    val token = chatClient.devToken(user.id)
    cachedToken = token

    val result = chatClient.connectUser(user, token).await()
    if (result.isFailure) {
      val message = result.errorOrNull()?.message ?: "Demo chat connect failed"
      streamLog { message }
      error(message)
    }
    streamLog { "Demo chat user connected" }

    scheduleVideoConnect(
      userId = user.id,
      name = user.name,
      image = user.image,
      token = StreamVideo.devToken(user.id)
    )
  }

  suspend fun connectWithFirebaseToken(
    chatClient: ChatClient,
    userId: String,
    name: String,
    image: String,
    token: String,
    tokenRefresher: (() -> String)? = null
  ) {
    cachedToken = token
    this.tokenRefresher = tokenRefresher
    persistSession(userId = userId, name = name, image = image, token = token)

    val existing = chatClient.getCurrentUser()
    if (existing?.id == userId) {
      streamLog { "Firebase chat user already connected: $userId" }
      scheduleVideoConnect(userId = userId, name = name, image = image, token = token)
      return
    }

    val user = User(id = userId, name = name, image = image)
    val result = chatClient.connectUser(
      user = user,
      tokenProvider = object : TokenProvider {
        override fun loadToken(): String {
          return try {
            tokenRefresher?.invoke()?.also { fresh ->
              cachedToken = fresh
              persistSession(userId = userId, name = name, image = image, token = fresh)
            } ?: cachedToken ?: token
          } catch (error: Throwable) {
            streamLog { "Token refresh failed: ${error.message}" }
            cachedToken ?: token
          }
        }
      }
    ).await()

    if (result.isFailure) {
      val message = result.errorOrNull()?.message ?: "Firebase chat connect failed"
      streamLog { message }
      error(message)
    }
    streamLog { "Firebase chat user connected: $userId" }

    // Chat is enough for the home shell; Video connects right after on a background job.
    scheduleVideoConnect(userId = userId, name = name, image = image, token = token)
  }

  /** Ensures Stream Video is ready (e.g. before placing/receiving a call). */
  suspend fun ensureVideoConnected() {
    val pending = pendingVideoUser
    val token = cachedToken ?: getPersistedSession()?.token
    if (pending != null && token != null) {
      connectVideo(
        userId = pending.id,
        name = pending.name.orEmpty(),
        image = pending.image.orEmpty(),
        token = token
      )
      return
    }
    val session = getPersistedSession() ?: return
    connectVideo(
      userId = session.userId,
      name = session.name,
      image = session.image,
      token = session.token
    )
  }

  fun disconnect(chatClient: ChatClient) {
    chatClient.disconnect(flushPersistence = true).enqueue()
    cachedToken = null
    tokenRefresher = null
    pendingVideoUser = null
    prefs.edit().clear().apply()
    try {
      StreamVideo.instance().logOut()
    } catch (error: Throwable) {
      streamLog { "StreamVideo logout skipped: ${error.message}" }
    }
  }

  private fun scheduleVideoConnect(userId: String, name: String, image: String, token: String) {
    pendingVideoUser = VideoUser(
      id = userId,
      name = name,
      image = image,
      role = "user"
    )
    scope.launch {
      connectVideo(userId = userId, name = name, image = image, token = token)
    }
  }

  private suspend fun connectVideo(userId: String, name: String, image: String, token: String) {
    videoConnectMutex.withLock {
      val already = runCatching { StreamVideo.instance().user.id == userId }.getOrDefault(false)
      if (already) {
        pendingVideoUser = null
        return
      }

      try {
        try {
          StreamVideo.instance().logOut()
        } catch (_: Throwable) {
          // Video client may not be installed yet.
        }

        // Same provider name as Chat (`firebase`) so one Stream Dashboard FCM config covers both.
        val notificationConfig = NotificationConfig(
          pushDeviceGenerators = listOf(
            FirebasePushDeviceGenerator(providerName = PUSH_PROVIDER_NAME)
          ),
          // In-app IncomingCallOverlay handles ringing while foregrounded.
          hideRingingNotificationInForeground = true,
          requestPermissionOnAppLaunch = { false }
        )

        StreamVideoBuilder(
          context = context,
          apiKey = BuildConfig.STREAM_API_KEY,
          token = token,
          user = VideoUser(
            id = userId,
            name = name,
            image = image,
            role = "user"
          ),
          notificationConfig = notificationConfig
        ).build()
        pendingVideoUser = null
        streamLog { "StreamVideo connected for $userId" }
      } catch (error: Throwable) {
        // Keep chat usable; calls will fail until the next successful reconnect.
        streamLog { "StreamVideo connect failed: ${error.message}" }
      }
    }
  }

  private fun persistSession(userId: String, name: String, image: String, token: String) {
    prefs.edit()
      .putString(KEY_USER_ID, userId)
      .putString(KEY_NAME, name)
      .putString(KEY_IMAGE, image)
      .putString(KEY_TOKEN, token)
      .apply()
  }

  companion object {
    private const val PREFS_NAME = "batchit_stream_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME = "name"
    private const val KEY_IMAGE = "image"
    private const val KEY_TOKEN = "token"
    private const val DEMO_USER_ID = "batchit_demo"
    private const val DEMO_USER_NAME = "BatchIt User"
    private const val DEMO_USER_IMAGE = "https://placekitten.com/200/300"
    private const val PUSH_PROVIDER_NAME = "firebase"
  }
}
