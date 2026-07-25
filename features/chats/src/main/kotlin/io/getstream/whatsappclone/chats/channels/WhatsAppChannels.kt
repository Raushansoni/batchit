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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.compose.ui.channels.list.ChannelList
import io.getstream.chat.android.compose.viewmodel.channels.ChannelListViewModel
import io.getstream.chat.android.compose.viewmodel.channels.ChannelViewModelFactory
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.Filters
import io.getstream.chat.android.models.querysort.QuerySortByField
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.chats.friends.BatchItUser
import io.getstream.whatsappclone.chats.theme.WhatsAppChatTheme
import io.getstream.whatsappclone.designsystem.component.BatchItFab
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingIndicator
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons

@Composable
fun WhatsAppChannels(
  whatsChannelsViewModel: WhatsChannelsViewModel = hiltViewModel()
) {
  var showGroupDialog by remember { mutableStateOf(false) }
  var optionsChannel by remember { mutableStateOf<Channel?>(null) }
  val currentUser by ChatClient.instance().clientState.user.collectAsStateWithLifecycle()
  val error by whatsChannelsViewModel.error.collectAsStateWithLifecycle()

  val onChannelClick = remember<(Channel) -> Unit>(whatsChannelsViewModel) {
    { channel -> whatsChannelsViewModel.navigateToMessages(channel.cid) }
  }
  val onOpenFriends = remember(whatsChannelsViewModel) {
    { whatsChannelsViewModel.openFriendsContacts() }
  }

  WhatsAppChatTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      if (currentUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          WhatsAppLoadingIndicator()
        }
      } else {
        val listViewModel: ChannelListViewModel = viewModel(
          factory = ChannelViewModelFactory(
            chatClient = ChatClient.instance(),
            filters = Filters.and(
              Filters.eq("type", "messaging"),
              Filters.`in`("members", listOf(currentUser!!.id))
            ),
            querySort = QuerySortByField.descByName("last_updated")
          )
        )

        ChannelList(
          viewModel = listViewModel,
          onChannelClick = onChannelClick,
          onChannelLongClick = { channel -> optionsChannel = channel }
        )
      }

      Column(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
      ) {
        BatchItFab(
          onClick = { showGroupDialog = true },
          icon = WhatsAppIcons.Groups,
          contentDescription = stringResource(id = R.string.new_group),
          size = 48.dp,
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        BatchItFab(
          onClick = onOpenFriends,
          icon = WhatsAppIcons.Message,
          contentDescription = stringResource(id = R.string.new_chat)
        )
      }
    }
  }

  optionsChannel?.let { channel ->
    val isMuted = currentUser?.channelMutes?.any { it.channel?.cid == channel.cid } == true
    ChannelOptionsDialog(
      channel = channel,
      isPinned = whatsChannelsViewModel.isPinned(channel),
      isMuted = isMuted,
      onDismiss = { optionsChannel = null },
      onMuteToggle = {
        if (isMuted) {
          whatsChannelsViewModel.unmuteChannel(channel)
        } else {
          whatsChannelsViewModel.muteChannel(channel)
        }
        optionsChannel = null
      },
      onArchive = {
        whatsChannelsViewModel.archiveChannel(channel)
        optionsChannel = null
      },
      onPinToggle = {
        whatsChannelsViewModel.togglePin(channel)
        optionsChannel = null
      }
    )
  }

  if (showGroupDialog) {
    NewGroupFriendsDialog(
      viewModel = whatsChannelsViewModel,
      onDismiss = { showGroupDialog = false },
      onCreate = { name, memberIds ->
        showGroupDialog = false
        whatsChannelsViewModel.createGroupChannelByMemberIds(name, memberIds)
      }
    )
  }

  error?.let { message ->
    AlertDialog(
      onDismissRequest = whatsChannelsViewModel::clearError,
      title = { Text(text = stringResource(id = R.string.new_chat)) },
      text = { Text(text = message) },
      confirmButton = {
        TextButton(onClick = whatsChannelsViewModel::clearError) {
          Text(text = stringResource(id = R.string.cancel))
        }
      }
    )
  }
}

@Composable
private fun ChannelOptionsDialog(
  channel: Channel,
  isPinned: Boolean,
  isMuted: Boolean,
  onDismiss: () -> Unit,
  onMuteToggle: () -> Unit,
  onArchive: () -> Unit,
  onPinToggle: () -> Unit
) {
  val title = channel.name.takeIf { !it.isNullOrBlank() }
    ?: channel.members.joinToString { it.user.name }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
      Column {
        TextButton(onClick = onMuteToggle, modifier = Modifier.fillMaxWidth()) {
          Text(
            text = stringResource(
              id = if (isMuted) R.string.channel_unmute else R.string.channel_mute
            )
          )
        }
        TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
          Text(text = stringResource(id = R.string.channel_archive))
        }
        TextButton(onClick = onPinToggle, modifier = Modifier.fillMaxWidth()) {
          Text(
            text = stringResource(
              id = if (isPinned) R.string.channel_unpin else R.string.channel_pin
            )
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = R.string.cancel))
      }
    }
  )
}

@Composable
private fun NewGroupFriendsDialog(
  viewModel: WhatsChannelsViewModel,
  onDismiss: () -> Unit,
  onCreate: (String, List<String>) -> Unit
) {
  var groupName by remember { mutableStateOf("") }
  var selectedIds by remember { mutableStateOf(setOf<String>()) }
  val friends by viewModel.groupFriends.collectAsStateWithLifecycle()
  val isLoading by viewModel.isLoadingGroupFriends.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.loadGroupFriends()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.new_group)) },
    text = {
      Column {
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth(),
          value = groupName,
          onValueChange = { groupName = it },
          label = { Text(text = stringResource(id = R.string.group_name)) },
          singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(id = R.string.group_select_friends),
          style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (isLoading) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator()
          }
        } else if (friends.isEmpty()) {
          Text(
            text = stringResource(id = R.string.group_no_friends),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          LazyColumn(modifier = Modifier.height(220.dp)) {
            items(friends, key = { it.uid }) { friend ->
              GroupFriendCheckboxRow(
                friend = friend,
                checked = friend.uid in selectedIds,
                onCheckedChange = { checked ->
                  selectedIds = if (checked) {
                    selectedIds + friend.uid
                  } else {
                    selectedIds - friend.uid
                  }
                }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (groupName.isNotBlank() && selectedIds.isNotEmpty()) {
            onCreate(groupName.trim(), selectedIds.toList())
          }
        },
        enabled = groupName.isNotBlank() && selectedIds.isNotEmpty()
      ) {
        Text(text = stringResource(id = R.string.create_group))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = R.string.cancel))
      }
    }
  )
}

@Composable
private fun GroupFriendCheckboxRow(
  friend: BatchItUser,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Column(modifier = Modifier.padding(start = 4.dp)) {
      Text(text = friend.name.ifBlank { friend.username })
      if (friend.username.isNotBlank()) {
        Text(
          text = "@${friend.username}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
