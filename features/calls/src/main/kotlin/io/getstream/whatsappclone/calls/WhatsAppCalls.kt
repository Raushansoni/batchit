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

package io.getstream.whatsappclone.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.component.BatchItFab
import io.getstream.whatsappclone.designsystem.component.WhatsAppError
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingColumn
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.model.WhatsAppUser
import io.getstream.whatsappclone.uistate.WhatsAppUserUiState

@Composable
fun WhatsAppCalls(
  whatsAppCallsViewModel: WhatsAppCallsViewModel = hiltViewModel()
) {
  val whatsAppUsersUiState by whatsAppCallsViewModel.whatsAppUserState.collectAsStateWithLifecycle()
  val onHistoryItemClick = remember(whatsAppCallsViewModel) {
    { user: WhatsAppUser -> whatsAppCallsViewModel.navigateToCallInfo(user) }
  }
  val onVideoCall = remember(whatsAppCallsViewModel) {
    { whatsAppCallsViewModel.openFriendsForCall(videoCall = true) }
  }
  val onAudioCall = remember(whatsAppCallsViewModel) {
    { whatsAppCallsViewModel.openFriendsForCall(videoCall = false) }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    WhatsAppCallsScreen(
      whatsAppUsersUiState = whatsAppUsersUiState,
      onHistoryItemClick = onHistoryItemClick
    )

    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp)
    ) {
      BatchItFab(
        onClick = onVideoCall,
        icon = WhatsAppIcons.Video,
        contentDescription = null,
        size = 48.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.secondary
      )

      Spacer(modifier = Modifier.height(12.dp))

      BatchItFab(
        onClick = onAudioCall,
        icon = WhatsAppIcons.Call,
        contentDescription = null
      )
    }
  }
}

@Composable
private fun WhatsAppCallsScreen(
  whatsAppUsersUiState: WhatsAppUserUiState,
  onHistoryItemClick: (WhatsAppUser) -> Unit
) {
  when (whatsAppUsersUiState) {
    WhatsAppUserUiState.Loading -> WhatsAppLoadingColumn()
    WhatsAppUserUiState.Error -> WhatsAppError()
    is WhatsAppUserUiState.Success -> {
      val users = whatsAppUsersUiState.data.whatsappUserList
      if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          androidx.compose.material3.Text(
            text = "No calls yet — tap a friend to start",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        LazyColumn {
          items(
            items = users,
            key = { "${it.cell}-${it.registrationDate}" },
            contentType = { "call-history" }
          ) { user ->
            WhatsAppCallHistory(whatsAppUser = user) {
              onHistoryItemClick(user)
            }
          }
        }
      }
    }
  }
}
