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

package io.getstream.whatsappclone.chats.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelRequest
import io.getstream.chat.android.models.Message
import io.getstream.whatsappclone.chats.starred.StarredMessagesStore
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppScreens
import io.getstream.whatsappclone.uistate.WhatsAppMessageUiState
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WhatsAppMessagesViewModel @Inject constructor(
  private val chatClient: ChatClient,
  private val composeNavigator: AppComposeNavigator,
  private val starredMessagesStore: StarredMessagesStore,
  savedStateHandle: SavedStateHandle
) : ViewModel() {
  private val messageMutableUiState =
    MutableStateFlow<WhatsAppMessageUiState>(WhatsAppMessageUiState.Loading)
  val messageUiSate: StateFlow<WhatsAppMessageUiState> = messageMutableUiState

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage

  private val channelId = savedStateHandle.get<String>("channelId")

  init {
    if (channelId != null) {
      fetchChannelHeader(channelId = channelId)
    }
  }

  fun handleEvents(whatsAppMessageEvent: WhatsAppMessageEvent) {
    when (whatsAppMessageEvent) {
      is WhatsAppMessageEvent.NavigateUp -> composeNavigator.navigateUp()
    }
  }

  fun clearActionMessage() {
    _actionMessage.value = null
  }

  fun navigateToVideoCall(channelId: String, videoCall: Boolean) {
    viewModelScope.launch {
      val me = chatClient.getCurrentUser()?.id
      val request = QueryChannelRequest().withState()
      val channelResult = chatClient.channel(channelId).query(request).await()
      val memberIds = channelResult.getOrNull()?.members
        ?.mapNotNull { it.user.id }
        ?.filter { it != me }
        .orEmpty()
      val members = memberIds.joinToString(",")

      composeNavigator.navigate(
        WhatsAppScreens.VideoCall.createRoute(
          callId = UUID.randomUUID().toString(),
          videoCall = videoCall,
          members = members
        )
      )
    }
  }

  fun starLatestMessage() {
    val cid = channelId ?: return
    viewModelScope.launch {
      val request = QueryChannelRequest().withMessages(1)
      val result = chatClient.channel(cid).query(request).await()
      val latest = result.getOrNull()?.messages?.lastOrNull()
      if (latest == null) {
        _actionMessage.value = "No messages to star"
        return@launch
      }
      starredMessagesStore.star(
        messageId = latest.id,
        channelId = cid,
        previewText = latest.text
      )
      _actionMessage.value = "Message starred"
    }
  }

  fun shareLocationPlaceholder() {
    val cid = channelId ?: return
    viewModelScope.launch {
      val text = "Location sharing: open Maps and paste a link"
      chatClient.channel(cid).sendMessage(Message(text = text)).await()
        .onSuccess { _actionMessage.value = "Location hint sent" }
        .onError { _actionMessage.value = it.message ?: "Could not send location" }
    }
  }

  /**
   * Lightweight header fetch — do not call channel.watch() (MessagesScreen owns that).
   */
  private fun fetchChannelHeader(channelId: String) {
    viewModelScope.launch {
      val request = QueryChannelRequest().withMessages(0)
      val result = chatClient.channel(channelId).query(request).await()
      result.onSuccess {
        messageMutableUiState.value = WhatsAppMessageUiState.Success(result.getOrThrow())
      }.onError {
        messageMutableUiState.value = WhatsAppMessageUiState.Error
      }
    }
  }
}

sealed interface WhatsAppMessageEvent {
  data object NavigateUp : WhatsAppMessageEvent
}
