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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.getstream.chat.android.compose.ui.channels.ChannelsScreen
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.chats.theme.WhatsAppChatTheme
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.GREEN500

@Composable
fun WhatsAppChannels(
  whatsChannelsViewModel: WhatsChannelsViewModel = hiltViewModel()
) {
  var showNewChatDialog by remember { mutableStateOf(false) }
  var showGroupDialog by remember { mutableStateOf(false) }

  WhatsAppChatTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      ChannelsScreen(
        isShowingHeader = false,
        onChannelClick = { channel ->
          whatsChannelsViewModel.navigateToMessages(channel.cid)
        }
      )

      Column(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
      ) {
        FloatingActionButton(
          modifier = Modifier.size(48.dp),
          containerColor = GREEN500,
          shape = CircleShape,
          onClick = { showGroupDialog = true }
        ) {
          Icon(
            imageVector = WhatsAppIcons.Groups,
            contentDescription = stringResource(id = R.string.new_group),
            tint = Color.White
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        FloatingActionButton(
          modifier = Modifier.size(58.dp),
          containerColor = GREEN500,
          shape = CircleShape,
          onClick = { showNewChatDialog = true }
        ) {
          Icon(
            imageVector = WhatsAppIcons.Message,
            contentDescription = stringResource(id = R.string.new_chat),
            tint = Color.White
          )
        }
      }
    }
  }

  if (showNewChatDialog) {
    NewChatDialog(
      onDismiss = { showNewChatDialog = false },
      onCreate = { userId ->
        showNewChatDialog = false
        whatsChannelsViewModel.createDirectChannel(userId)
      }
    )
  }

  if (showGroupDialog) {
    NewGroupDialog(
      onDismiss = { showGroupDialog = false },
      onCreate = { name, memberIds ->
        showGroupDialog = false
        whatsChannelsViewModel.createGroupChannel(name, memberIds)
      }
    )
  }
}

@Composable
private fun NewChatDialog(
  onDismiss: () -> Unit,
  onCreate: (String) -> Unit
) {
  var userId by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.new_chat)) },
    text = {
      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = userId,
        onValueChange = { userId = it },
        label = { Text(text = stringResource(id = R.string.contact_user_id)) },
        singleLine = true
      )
    },
    confirmButton = {
      TextButton(
        onClick = { if (userId.isNotBlank()) onCreate(userId.trim()) },
        enabled = userId.isNotBlank()
      ) {
        Text(text = stringResource(id = R.string.start_chat))
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
private fun NewGroupDialog(
  onDismiss: () -> Unit,
  onCreate: (String, List<String>) -> Unit
) {
  var groupName by remember { mutableStateOf("") }
  var memberIds by remember { mutableStateOf("") }

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
          value = memberIds,
          onValueChange = { memberIds = it },
          label = { Text(text = stringResource(id = R.string.member_ids_hint)) },
          singleLine = true
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (groupName.isNotBlank()) {
            val members = memberIds.split(",")
              .map { it.trim() }
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
