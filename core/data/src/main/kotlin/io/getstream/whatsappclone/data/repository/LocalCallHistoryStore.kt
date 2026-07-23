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

package io.getstream.whatsappclone.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.whatsappclone.model.CallRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class LocalCallHistoryStore @Inject constructor(
  @ApplicationContext context: Context
) {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private val _records = MutableStateFlow(read())
  val records: StateFlow<List<CallRecord>> = _records.asStateFlow()

  fun add(record: CallRecord) {
    _records.update { current ->
      (listOf(record) + current.filterNot { it.callId == record.callId })
        .sortedByDescending { it.startedAt }
        .take(MAX)
        .also { persist(it) }
    }
  }

  fun clear() {
    prefs.edit().remove(KEY).apply()
    _records.value = emptyList()
  }

  private fun read(): List<CallRecord> {
    val raw = prefs.getString(KEY, null) ?: return emptyList()
    return runCatching { json.decodeFromString<List<CallRecord>>(raw) }.getOrDefault(emptyList())
  }

  private fun persist(list: List<CallRecord>) {
    prefs.edit().putString(KEY, json.encodeToString(list)).apply()
  }

  companion object {
    private const val PREFS = "batchit_call_history"
    private const val KEY = "records"
    private const val MAX = 100
  }
}
