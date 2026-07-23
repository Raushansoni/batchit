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

package io.getstream.whatsappclone.chats.starred

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class StarredMessage(
  val messageId: String,
  val channelId: String,
  val previewText: String,
  val starredAt: Long = System.currentTimeMillis()
)

@Singleton
class StarredMessagesStore @Inject constructor(
  @ApplicationContext context: Context
) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun list(): List<StarredMessage> {
    val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
    return runCatching {
      val array = JSONArray(raw)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          add(
            StarredMessage(
              messageId = obj.getString("messageId"),
              channelId = obj.getString("channelId"),
              previewText = obj.getString("previewText"),
              starredAt = obj.optLong("starredAt", System.currentTimeMillis())
            )
          )
        }
      }.sortedByDescending { it.starredAt }
    }.getOrElse { emptyList() }
  }

  fun star(messageId: String, channelId: String, previewText: String) {
    if (messageId.isBlank() || channelId.isBlank()) return
    val current = list().filterNot { it.messageId == messageId && it.channelId == channelId }
    val updated = listOf(
      StarredMessage(
        messageId = messageId,
        channelId = channelId,
        previewText = previewText.take(200),
        starredAt = System.currentTimeMillis()
      )
    ) + current
    persist(updated)
  }

  fun unstar(messageId: String, channelId: String) {
    persist(list().filterNot { it.messageId == messageId && it.channelId == channelId })
  }

  fun isStarred(messageId: String, channelId: String): Boolean =
    list().any { it.messageId == messageId && it.channelId == channelId }

  private fun persist(entries: List<StarredMessage>) {
    val array = JSONArray()
    entries.forEach { entry ->
      array.put(
        JSONObject()
          .put("messageId", entry.messageId)
          .put("channelId", entry.channelId)
          .put("previewText", entry.previewText)
          .put("starredAt", entry.starredAt)
      )
    }
    prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
  }

  companion object {
    private const val PREFS_NAME = "batchit_starred_messages"
    private const val KEY_ENTRIES = "entries"
  }
}
