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

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.getTitleColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FriendsContactsScreen(
  viewModel: FriendsViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)

  LaunchedEffect(contactsPermission.status.isGranted) {
    if (contactsPermission.status.isGranted) {
      viewModel.refresh()
    }
  }

  val filteredFriends = remember(state.friends, state.query) {
    val q = state.query.trim().lowercase().removePrefix("@")
    if (q.isBlank()) state.friends
    else state.friends.filter {
      it.username.contains(q) || it.name.contains(q, ignoreCase = true)
    }
  }
  val filteredContacts = remember(state.contacts, state.query) {
    val q = state.query.trim().lowercase().removePrefix("@")
    if (q.isBlank()) state.contacts
    else state.contacts.filter {
      it.contact.name.contains(q, ignoreCase = true) ||
        it.batchItUser?.username?.contains(q) == true
    }
  }

  val screenTitle = when (state.mode) {
    FriendsPickerMode.CallAudio -> stringResource(id = R.string.friends_title_call_audio)
    FriendsPickerMode.CallVideo -> stringResource(id = R.string.friends_title_call_video)
    FriendsPickerMode.Chat -> stringResource(id = R.string.friends_title)
  }
  val primaryActionLabel = when (state.mode) {
    FriendsPickerMode.CallAudio -> stringResource(id = R.string.friends_call_audio)
    FriendsPickerMode.CallVideo -> stringResource(id = R.string.friends_call_video)
    FriendsPickerMode.Chat -> stringResource(id = R.string.friends_chat)
  }
  val addButtonLabel = when (state.mode) {
    FriendsPickerMode.CallAudio -> stringResource(id = R.string.friends_add_and_call_audio)
    FriendsPickerMode.CallVideo -> stringResource(id = R.string.friends_add_and_call_video)
    FriendsPickerMode.Chat -> stringResource(id = R.string.friends_add_and_chat)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = screenTitle) },
        navigationIcon = {
          IconButton(onClick = viewModel::navigateUp) {
            Icon(
              imageVector = WhatsAppIcons.ArrowBack,
              contentDescription = stringResource(id = R.string.cancel)
            )
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
    ) {
      Text(
        text = if (state.myUsername.isNotBlank()) {
          stringResource(id = R.string.friends_your_username, state.myUsername)
        } else {
          stringResource(id = R.string.friends_subtitle)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onTertiary
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.query,
        onValueChange = viewModel::onQueryChange,
        singleLine = true,
        label = { Text(text = stringResource(id = R.string.friends_username_hint)) }
      )

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { viewModel.addFriendByUsername(state.query) },
        enabled = state.query.isNotBlank() && !state.isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
      ) {
        Text(text = addButtonLabel, color = MaterialTheme.colorScheme.onSecondary)
      }

      state.error?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = it, color = MaterialTheme.colorScheme.error)
      }
      state.info?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = it, color = MaterialTheme.colorScheme.secondary)
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          if (filteredFriends.isNotEmpty()) {
            item {
              SectionHeader(text = stringResource(id = R.string.friends_section_friends))
            }
            items(filteredFriends, key = { "friend-${it.uid}" }) { friend ->
              FriendRow(
                title = friend.name.ifBlank { friend.username },
                subtitle = "@${friend.username}",
                actionLabel = primaryActionLabel,
                onAction = { viewModel.onFriendSelected(friend) }
              )
            }
          }

          item {
            SectionHeader(text = stringResource(id = R.string.friends_section_contacts))
          }

          if (!contactsPermission.status.isGranted) {
            item {
              Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                  text = stringResource(id = R.string.friends_contacts_permission),
                  color = getTitleColor()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                  onClick = { contactsPermission.launchPermissionRequest() },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                  )
                ) {
                  Text(
                    text = stringResource(id = R.string.friends_allow_contacts),
                    color = MaterialTheme.colorScheme.onSecondary
                  )
                }
              }
            }
          } else if (filteredContacts.isEmpty()) {
            item {
              Text(
                text = stringResource(id = R.string.friends_no_contacts),
                color = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.padding(vertical = 8.dp)
              )
            }
          } else {
            items(filteredContacts, key = { "contact-${it.contact.id}" }) { match ->
              val onBatchIt = match.batchItUser
              if (onBatchIt != null && onBatchIt.username.isNotBlank()) {
                FriendRow(
                  title = match.contact.name,
                  subtitle = stringResource(
                    id = R.string.friends_on_batchit,
                    onBatchIt.username
                  ),
                  actionLabel = primaryActionLabel,
                  onAction = { viewModel.addFriendAndChat(onBatchIt) }
                )
              } else {
                FriendRow(
                  title = match.contact.name,
                  subtitle = match.contact.phones.firstOrNull()
                    ?: match.contact.emails.firstOrNull()
                    ?: "",
                  actionLabel = stringResource(id = R.string.friends_invite),
                  onAction = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_TEXT, viewModel.inviteMessage())
                      match.contact.phones.firstOrNull()?.let { phone ->
                        putExtra("address", phone)
                      }
                    }
                    context.startActivity(
                      Intent.createChooser(
                        share,
                        context.getString(R.string.friends_invite)
                      )
                    )
                  }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.secondary,
    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
  )
}

@Composable
private fun FriendRow(
  title: String,
  subtitle: String,
  actionLabel: String,
  onAction: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onAction)
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = WhatsAppIcons.Message,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, color = getTitleColor(), fontWeight = FontWeight.Medium)
      if (subtitle.isNotBlank()) {
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
      }
    }
    TextButton(onClick = onAction) {
      Text(text = actionLabel, color = MaterialTheme.colorScheme.secondary)
    }
  }
}
