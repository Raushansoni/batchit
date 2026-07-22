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
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.models.User
import io.getstream.log.streamLog
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.StreamVideoBuilder
import io.getstream.video.android.model.User as VideoUser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamSessionManager @Inject constructor(
  @ApplicationContext private val context: Context
) {

  @Volatile
  private var cachedToken: String? = null

  fun connectDemoUser(chatClient: ChatClient) {
    val user = User(
      id = DEMO_USER_ID,
      name = DEMO_USER_NAME,
      image = DEMO_USER_IMAGE
    )
    val token = chatClient.devToken(user.id)
    cachedToken = token

    chatClient.connectUser(user, token).enqueue { result ->
      if (result.isSuccess) {
        streamLog { "Demo chat user connected" }
      } else {
        streamLog { "Demo chat connect failed: ${result.errorOrNull()?.message}" }
      }
    }

    connectVideo(
      userId = user.id,
      name = user.name,
      image = user.image,
      token = StreamVideo.devToken(user.id)
    )
  }

  fun connectWithFirebaseToken(
    chatClient: ChatClient,
    userId: String,
    name: String,
    image: String,
    token: String
  ) {
    cachedToken = token
    val user = User(id = userId, name = name, image = image)

    chatClient.connectUser(
      user = user,
      tokenProvider = object : TokenProvider {
        override fun loadToken(): String {
          return refreshToken() ?: cachedToken ?: token
        }
      }
    ).enqueue { result ->
      if (result.isSuccess) {
        streamLog { "Firebase chat user connected: $userId" }
      } else {
        streamLog { "Firebase chat connect failed: ${result.errorOrNull()?.message}" }
      }
    }

    connectVideo(userId = userId, name = name, image = image, token = token)
  }

  fun disconnect(chatClient: ChatClient) {
    chatClient.disconnect(flushPersistence = true).enqueue()
    cachedToken = null
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

  private fun refreshToken(): String? {
    return try {
      val latch = CountDownLatch(1)
      val tokenRef = AtomicReference<String?>(null)
      Firebase.functions
        .getHttpsCallable(FUNCTION_GET_STREAM_TOKEN)
        .call()
        .addOnSuccessListener { result ->
          val data = result.getData() as? Map<*, *>
          tokenRef.set(data?.get("token") as? String)
          latch.countDown()
        }
        .addOnFailureListener {
          latch.countDown()
        }
      latch.await(15, TimeUnit.SECONDS)
      tokenRef.get()?.also { cachedToken = it }
    } catch (error: Throwable) {
      streamLog { "Token refresh failed: ${error.message}" }
      null
    }
  }

  companion object {
    private const val DEMO_USER_ID = "batchit_demo"
    private const val DEMO_USER_NAME = "BatchIt User"
    private const val DEMO_USER_IMAGE = "https://placekitten.com/200/300"
    private const val FUNCTION_GET_STREAM_TOKEN = "getStreamUserToken"
  }
}
