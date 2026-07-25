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

package io.getstream.whatsappclone.chats.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelsRequest
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.Filters
import io.getstream.chat.android.models.querysort.QuerySortByField
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppScreens
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatSearchUiState(
  val query: String = "",
  val results: List<Channel> = emptyList(),
  val isSearching: Boolean = false,
  val error: String? = null
)

@HiltViewModel
class ChatSearchViewModel @Inject constructor(
  private val chatClient: ChatClient,
  private val composeNavigator: AppComposeNavigator
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatSearchUiState())
  val uiState: StateFlow<ChatSearchUiState> = _uiState.asStateFlow()

  private var searchJob: Job? = null

  fun onQueryChange(query: String) {
    _uiState.update { it.copy(query = query, error = null) }
    searchJob?.cancel()
    if (query.isBlank()) {
      _uiState.update { it.copy(results = emptyList(), isSearching = false) }
      return
    }
    searchJob = viewModelScope.launch {
      delay(SEARCH_DEBOUNCE_MS)
      searchChannels(query)
    }
  }

  fun navigateUp() {
    composeNavigator.navigateUp()
  }

  fun openChannel(channel: Channel) {
    composeNavigator.navigate(WhatsAppScreens.Messages.createRoute(channel.cid))
  }

  private suspend fun searchChannels(rawQuery: String) {
    val query = rawQuery.trim()
    if (query.isBlank()) return

    val me = chatClient.getCurrentUser()?.id
    if (me == null) {
      _uiState.update { it.copy(isSearching = false, error = "Not connected to chat") }
      return
    }

    _uiState.update { it.copy(isSearching = true, error = null) }

    val filter = Filters.and(
      Filters.eq("type", "messaging"),
      Filters.`in`("members", listOf(me)),
      Filters.or(
        Filters.autocomplete("name", query),
        Filters.autocomplete("member.user.name", query),
        Filters.autocomplete("member.user.id", query)
      )
    )

    val request = QueryChannelsRequest(
      filter = filter,
      offset = 0,
      limit = 30,
      querySort = QuerySortByField.descByName("last_updated")
    )

    val result = chatClient.queryChannels(request).await()
    result.onSuccess { channels ->
      _uiState.update {
        it.copy(
          isSearching = false,
          results = channels,
          error = null
        )
      }
    }.onError { error ->
      _uiState.update {
        it.copy(
          isSearching = false,
          results = emptyList(),
          error = error.message ?: "Search failed"
        )
      }
    }
  }

  companion object {
    private const val SEARCH_DEBOUNCE_MS = 300L
  }
}
