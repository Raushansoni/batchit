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

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class StreamTokenPayload(
  val token: String,
  val userId: String,
  val name: String,
  val image: String
)

@Singleton
class StreamTokenClient @Inject constructor() {

  private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  private val jsonMedia = "application/json; charset=utf-8".toMediaType()

  suspend fun mintToken(
    firebaseIdToken: String,
    name: String,
    image: String = ""
  ): StreamTokenPayload = withContext(Dispatchers.IO) {
    val base = BuildConfig.STREAM_TOKEN_URL.trim().trimEnd('/')
    require(base.isNotBlank() && base != "REPLACE_ME") {
      "Set STREAM_TOKEN_URL in secrets.properties to your Cloudflare Worker URL"
    }

    val body = JSONObject()
      .put("name", name)
      .put("image", image)
      .toString()
      .toRequestBody(jsonMedia)

    val request = Request.Builder()
      .url("$base/token")
      .addHeader("Authorization", "Bearer $firebaseIdToken")
      .addHeader("Content-Type", "application/json")
      .post(body)
      .build()

    http.newCall(request).execute().use { response ->
      val raw = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        val message = runCatching {
          JSONObject(raw).optString("error").ifBlank { raw }
        }.getOrDefault(raw)
        error(message.ifBlank { "Token Worker error ${response.code}" })
      }
      val json = JSONObject(raw)
      StreamTokenPayload(
        token = json.getString("token"),
        userId = json.optString("userId"),
        name = json.optString("name", name),
        image = json.optString("image", image)
      )
    }
  }
}
