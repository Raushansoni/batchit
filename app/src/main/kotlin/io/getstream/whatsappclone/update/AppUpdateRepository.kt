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

package io.getstream.whatsappclone.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.log.streamLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Singleton
class AppUpdateRepository @Inject constructor(
  @ApplicationContext private val context: Context
) {

  fun currentVersionCode(): Long {
    val pm = context.packageManager
    val pkg = context.packageName
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).longVersionCode
    } else {
      @Suppress("DEPRECATION")
      pm.getPackageInfo(pkg, 0).versionCode.toLong()
    }
  }

  suspend fun fetchLatestUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
    fetchFromFirestore() ?: fetchFromGitHubLatest()
  }

  private suspend fun fetchFromFirestore(): AppUpdateInfo? {
    return try {
      val snap = Firebase.firestore.document(DOC_PATH).get().await()
      if (!snap.exists()) return null
      val code = (snap.getLong("versionCode") ?: return null).toInt()
      val url = snap.getString("apkUrl").orEmpty()
      if (url.isBlank()) return null
      AppUpdateInfo(
        versionCode = code,
        versionName = snap.getString("versionName").orEmpty(),
        apkUrl = url,
        forceUpdate = snap.getBoolean("forceUpdate") ?: false,
        notes = snap.getString("notes").orEmpty()
      )
    } catch (error: Throwable) {
      streamLog { "App update Firestore read failed: ${error.message}" }
      null
    }
  }

  private fun fetchFromGitHubLatest(): AppUpdateInfo? {
    return try {
      val conn = (URL(GITHUB_LATEST_API).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        connectTimeout = 10_000
        readTimeout = 10_000
      }
      if (conn.responseCode !in 200..299) return null
      val body = conn.inputStream.bufferedReader().readText()
      val json = JSONObject(body)
      val assets = json.optJSONArray("assets") ?: return null

      var updateJsonUrl: String? = null
      var apkUrl: String? = null
      for (i in 0 until assets.length()) {
        val asset = assets.getJSONObject(i)
        val name = asset.optString("name")
        val browserUrl = asset.optString("browser_download_url")
        when {
          name == "app_update.json" -> updateJsonUrl = browserUrl
          name.endsWith(".apk", ignoreCase = true) -> apkUrl = browserUrl
        }
      }

      if (updateJsonUrl != null) {
        val metaConn = (URL(updateJsonUrl).openConnection() as HttpURLConnection).apply {
          connectTimeout = 10_000
          readTimeout = 10_000
        }
        val meta = JSONObject(metaConn.inputStream.bufferedReader().readText())
        return AppUpdateInfo(
          versionCode = meta.optInt("versionCode"),
          versionName = meta.optString("versionName"),
          apkUrl = meta.optString("apkUrl").ifBlank { apkUrl.orEmpty() },
          forceUpdate = meta.optBoolean("forceUpdate"),
          notes = meta.optString("notes")
        ).takeIf { it.versionCode > 0 && it.apkUrl.isNotBlank() }
      }

      // Fallback: parse tag like v1.1.1+7
      val tag = json.optString("tag_name")
      val code = Regex("""\+(\d+)$""").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
      if (code != null && !apkUrl.isNullOrBlank()) {
        AppUpdateInfo(
          versionCode = code,
          versionName = tag.removePrefix("v").substringBefore("+"),
          apkUrl = apkUrl,
          notes = json.optString("body")
        )
      } else {
        null
      }
    } catch (error: Throwable) {
      streamLog { "App update GitHub read failed: ${error.message}" }
      null
    }
  }

  companion object {
    private const val DOC_PATH = "config/app_update"
    private const val GITHUB_LATEST_API =
      "https://api.github.com/repos/Raushansoni/batchit/releases/latest"
  }
}
