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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.compose.ui.channels.ChannelsScreen
import io.getstream.chat.android.models.Channel
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.chats.theme.WhatsAppChatTheme
import io.getstream.whatsappclone.designsystem.component.BatchItFab
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingIndicator
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons

@Composable
fun WhatsAppChannels(
  whatsChannelsViewModel: WhatsChannelsViewModel = hiltViewModel()
) {
  var showGroupDialog by remember { mutableStateOf(false) }
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
        ChannelsScreen(
          isShowingHeader = false,
          onChannelClick = onChannelClick
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

  if (showGroupDialog) {
    NewGroupDialog(
      onDismiss = { showGroupDialog = false },
      onCreate = { name, memberUsernames ->
        showGroupDialog = false
        whatsChannelsViewModel.createGroupChannel(name, memberUsernames)
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
private fun NewGroupDialog(
  onDismiss: () -> Unit,
  onCreate: (String, List<String>) -> Unit
) {
  var groupName by remember { mutableStateOf("") }
  var memberUsernames by remember { mutableStateOf("") }

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
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth(),
          value = memberUsernames,
          onValueChange = { memberUsernames = it },
          label = { Text(text = stringResource(id = R.string.member_usernames_hint)) },
          singleLine = true
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (groupName.isNotBlank()) {
            val members = memberUsernames.split(",")
              .map { it.trim().removePrefix("@") }
              .filter { it.isNotEmpty() }
            onCreate(groupName.trim(), members)
          }
        },
        enabled = groupName.isNotBlank()
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
