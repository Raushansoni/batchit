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
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.models.User
import io.getstream.log.streamLog
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.StreamVideoBuilder
import io.getstream.video.android.model.User as VideoUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamSessionManager @Inject constructor(
  @ApplicationContext private val context: Context
) {

  @Volatile
  private var cachedToken: String? = null

  @Volatile
  private var tokenRefresher: (() -> String)? = null

  suspend fun connectDemoUser(chatClient: ChatClient) {
    val existing = chatClient.getCurrentUser()
    if (existing?.id == DEMO_USER_ID) {
      cachedToken = chatClient.devToken(DEMO_USER_ID)
      connectVideo(
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

    connectVideo(
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
    val user = User(id = userId, name = name, image = image)

    val result = chatClient.connectUser(
      user = user,
      tokenProvider = object : TokenProvider {
        override fun loadToken(): String {
          return try {
            tokenRefresher?.invoke()?.also { cachedToken = it }
              ?: cachedToken
              ?: token
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

    connectVideo(userId = userId, name = name, image = image, token = token)
  }

  fun disconnect(chatClient: ChatClient) {
    chatClient.disconnect(flushPersistence = true).enqueue()
    cachedToken = null
    tokenRefresher = null
    try {
      StreamVideo.instance().logOut()
    } catch (error: Throwable) {
      streamLog { "StreamVideo logout skipped: ${error.message}" }
    }
  }

  private fun connectVideo(userId: String, name: String, image: String, token: String) {
    try {
      try {
        StreamVideo.instance().logOut()
      } catch (_: Throwable) {
        // Video client may not be installed yet.
      }

      StreamVideoBuilder(
        context = context,
        apiKey = BuildConfig.STREAM_API_KEY,
        token = token,
        user = VideoUser(
          id = userId,
          name = name,
          image = image,
          role = "user"
        )
      ).build()
      streamLog { "StreamVideo connected for $userId" }
    } catch (error: Throwable) {
      streamLog { "StreamVideo connect failed: ${error.message}" }
    }
  }

  companion object {
    private const val DEMO_USER_ID = "batchit_demo"
    private const val DEMO_USER_NAME = "BatchIt User"
    private const val DEMO_USER_IMAGE = "https://placekitten.com/200/300"
  }
}
