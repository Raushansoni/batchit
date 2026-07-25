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

package io.getstream.whatsappclone.chats.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.Channel
import io.getstream.whatsappclone.chats.friends.BatchItUser
import io.getstream.whatsappclone.chats.friends.FriendsRepository
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppScreens
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WhatsChannelsViewModel @Inject constructor(
  private val composeNavigator: AppComposeNavigator,
  private val chatClient: ChatClient,
  private val friendsRepository: FriendsRepository,
  private val channelPinStore: ChannelPinStore
) : ViewModel() {

  private val user = chatClient.clientState.user

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private val _groupFriends = MutableStateFlow<List<BatchItUser>>(emptyList())
  val groupFriends: StateFlow<List<BatchItUser>> = _groupFriends.asStateFlow()

  private val _isLoadingGroupFriends = MutableStateFlow(false)
  val isLoadingGroupFriends: StateFlow<Boolean> = _isLoadingGroupFriends.asStateFlow()

  fun navigateToMessages(channelId: String) {
    composeNavigator.navigate(WhatsAppScreens.Messages.createRoute(channelId))
  }

  fun openFriendsContacts() {
    composeNavigator.navigate(WhatsAppScreens.FriendsContacts.createRoute())
  }

  fun isPinned(channel: Channel): Boolean = channelPinStore.isPinned(channel.cid)

  fun muteChannel(channel: Channel) {
    viewModelScope.launch {
      chatClient.channel(channel.cid).mute().await()
        .onError { _error.value = it.message ?: "Could not mute chat" }
    }
  }

  fun unmuteChannel(channel: Channel) {
    viewModelScope.launch {
      chatClient.channel(channel.cid).unmute().await()
        .onError { _error.value = it.message ?: "Could not unmute chat" }
    }
  }

  fun archiveChannel(channel: Channel) {
    viewModelScope.launch {
      chatClient.channel(channel.cid).hide().await()
        .onError { _error.value = it.message ?: "Could not archive chat" }
    }
  }

  fun togglePin(channel: Channel) {
    channelPinStore.togglePin(channel.cid)
  }

  fun createDirectChannelByUsername(rawUsername: String) {
    viewModelScope.launch {
      friendsRepository.resolveUsername(rawUsername)
        .onSuccess { batchUser ->
          friendsRepository.addFriend(batchUser)
          createDirectChannel(batchUser.uid, displayName = batchUser.username)
        }
        .onFailure { error ->
          _error.value = error.message ?: "User not found"
        }
    }
  }

  fun createDirectChannel(userId: String?, displayName: String? = null) {
    viewModelScope.launch {
      val me = user.value ?: return@launch
      val members = buildList {
        add(me.id)
        if (!userId.isNullOrBlank()) add(userId)
      }
      val channelId = if (userId.isNullOrBlank()) {
        "channel${Random.nextInt(10000)}"
      } else {
        listOf(me.id, userId).sorted().joinToString("-")
      }
      val extra = if (!displayName.isNullOrBlank()) {
        mapOf("name" to displayName)
      } else {
        emptyMap()
      }
      val result = chatClient.createChannel(
        channelType = "messaging",
        channelId = channelId,
        memberIds = members,
        extraData = extra
      ).await()
      if (result.isSuccess) {
        result.getOrNull()?.let { navigateToMessages(it.cid) }
      } else {
        _error.value = result.errorOrNull()?.message ?: "Could not start chat"
      }
    }
  }

  fun createGroupChannel(name: String, memberUsernames: List<String>) {
    viewModelScope.launch {
      val me = user.value ?: return@launch
      val memberIds = mutableListOf(me.id)
      memberUsernames.forEach { username ->
        friendsRepository.resolveUsername(username)
          .onSuccess { memberIds.add(it.uid) }
          .onFailure { error ->
            _error.value = error.message ?: "Unknown user @$username"
            return@launch
          }
      }
      createGroupChannelByMemberIds(name, memberIds.distinct())
    }
  }

  fun loadGroupFriends() {
    viewModelScope.launch {
      _isLoadingGroupFriends.value = true
      _groupFriends.value = friendsRepository.listFriends().getOrElse { emptyList() }
      _isLoadingGroupFriends.value = false
    }
  }

  fun createGroupChannelByMemberIds(name: String, memberIds: List<String>) {
    viewModelScope.launch {
      val me = user.value ?: return@launch
      val members = (memberIds + me.id).distinct()
      if (members.size < 2) {
        _error.value = "Select at least one friend"
        return@launch
      }
      val result = chatClient.createChannel(
        channelType = "messaging",
        channelId = "group${Random.nextInt(100000)}",
        memberIds = members,
        extraData = mapOf("name" to name)
      ).await()
      if (result.isSuccess) {
        result.getOrNull()?.let { navigateToMessages(it.cid) }
      } else {
        _error.value = result.errorOrNull()?.message ?: "Could not create group"
      }
    }
  }

  fun clearError() {
    _error.value = null
  }
}
