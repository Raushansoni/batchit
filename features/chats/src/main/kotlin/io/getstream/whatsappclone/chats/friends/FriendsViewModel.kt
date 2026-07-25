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

package io.getstream.whatsappclone.chats.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppScreens
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FriendsPickerMode {
  Chat,
  CallAudio,
  CallVideo
}

data class FriendsUiState(
  val isLoading: Boolean = true,
  val friends: List<BatchItUser> = emptyList(),
  val contacts: List<ContactMatch> = emptyList(),
  val query: String = "",
  val error: String? = null,
  val info: String? = null,
  val myUsername: String = "",
  val mode: FriendsPickerMode = FriendsPickerMode.Chat
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
  private val friendsRepository: FriendsRepository,
  private val chatClient: ChatClient,
  private val composeNavigator: AppComposeNavigator,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val pickerMode = when (
    savedStateHandle.get<String>(WhatsAppScreens.FriendsContacts.KEY_MODE)
  ) {
    WhatsAppScreens.FriendsContacts.MODE_CALL_AUDIO -> FriendsPickerMode.CallAudio
    WhatsAppScreens.FriendsContacts.MODE_CALL_VIDEO -> FriendsPickerMode.CallVideo
    else -> FriendsPickerMode.Chat
  }

  private val _uiState = MutableStateFlow(
    FriendsUiState(
      myUsername = friendsRepository.myUsername(),
      mode = pickerMode
    )
  )
  val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      _uiState.update {
        it.copy(isLoading = true, error = null, myUsername = friendsRepository.myUsername())
      }
      val friends = friendsRepository.listFriends().getOrElse { emptyList() }
      val contacts = friendsRepository.loadContactMatches().getOrElse { emptyList() }
      _uiState.update {
        it.copy(
          isLoading = false,
          friends = friends,
          contacts = contacts,
          error = null
        )
      }
    }
  }

  fun onQueryChange(value: String) {
    _uiState.update { it.copy(query = value, error = null, info = null) }
  }

  fun addFriendByUsername(rawUsername: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null, info = null) }
      friendsRepository.resolveUsername(rawUsername)
        .onSuccess { user ->
          friendsRepository.addFriend(user)
            .onSuccess {
              _uiState.update {
                it.copy(
                  isLoading = false,
                  info = "Added @${user.username}",
                  query = ""
                )
              }
              refresh()
              when (pickerMode) {
                FriendsPickerMode.Chat -> startChatWithUser(user)
                FriendsPickerMode.CallAudio -> startCallWithUser(user, videoCall = false)
                FriendsPickerMode.CallVideo -> startCallWithUser(user, videoCall = true)
              }
            }
            .onFailure { error ->
              _uiState.update {
                it.copy(isLoading = false, error = error.message ?: "Could not add friend")
              }
            }
        }
        .onFailure { error ->
          _uiState.update {
            it.copy(isLoading = false, error = error.message ?: "User not found")
          }
        }
    }
  }

  fun startChatWithUser(user: BatchItUser) {
    viewModelScope.launch {
      val me = chatClient.getCurrentUser() ?: return@launch
      val members = listOf(me.id, user.uid).distinct()
      val channelId = listOf(me.id, user.uid).sorted().joinToString("-")
      val result = chatClient.createChannel(
        channelType = "messaging",
        channelId = channelId,
        memberIds = members,
        extraData = mapOf("name" to user.username)
      ).await()
      if (result.isSuccess) {
        val channel = result.getOrNull()
        if (channel != null) {
          composeNavigator.navigate(WhatsAppScreens.Messages.createRoute(channel.cid))
        }
      } else {
        _uiState.update {
          it.copy(error = result.errorOrNull()?.message ?: "Could not start chat")
        }
      }
    }
  }

  fun startCallWithUser(user: BatchItUser, videoCall: Boolean) {
    composeNavigator.navigate(
      WhatsAppScreens.VideoCall.createRoute(
        callId = UUID.randomUUID().toString(),
        videoCall = videoCall,
        members = user.uid
      )
    )
  }

  fun onFriendSelected(user: BatchItUser) {
    when (pickerMode) {
      FriendsPickerMode.Chat -> startChatWithUser(user)
      FriendsPickerMode.CallAudio -> startCallWithUser(user, videoCall = false)
      FriendsPickerMode.CallVideo -> startCallWithUser(user, videoCall = true)
    }
  }

  fun addFriendAndChat(user: BatchItUser) {
    viewModelScope.launch {
      friendsRepository.addFriend(user)
      onFriendSelected(user)
      refresh()
    }
  }

  fun inviteMessage(): String =
    friendsRepository.inviteMessage(_uiState.value.myUsername)

  fun clearMessages() {
    _uiState.update { it.copy(error = null, info = null) }
  }

  fun navigateUp() {
    composeNavigator.navigateUp()
  }
}
