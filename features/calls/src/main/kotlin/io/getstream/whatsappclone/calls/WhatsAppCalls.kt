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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.component.WhatsAppError
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingColumn
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.GREEN500
import io.getstream.whatsappclone.model.WhatsAppUser
import io.getstream.whatsappclone.uistate.WhatsAppUserUiState
import java.util.UUID

@Composable
fun WhatsAppCalls(
  whatsAppCallsViewModel: WhatsAppCallsViewModel = hiltViewModel()
) {
  val whatsAppUsersUiState by whatsAppCallsViewModel.whatsAppUserState.collectAsStateWithLifecycle()

  Box(modifier = Modifier.fillMaxSize()) {
    WhatsAppCallsScreen(
      whatsAppUsersUiState = whatsAppUsersUiState,
      onHistoryItemClick = whatsAppCallsViewModel::navigateToCallInfo
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
        onClick = {
          whatsAppCallsViewModel.startCall(
            callId = UUID.randomUUID().toString(),
            videoCall = true
          )
        }
      ) {
        Icon(
          imageVector = WhatsAppIcons.Video,
          contentDescription = null,
          tint = Color.White
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      FloatingActionButton(
        modifier = Modifier.size(58.dp),
        containerColor = GREEN500,
        shape = CircleShape,
        onClick = {
          whatsAppCallsViewModel.startCall(
            callId = UUID.randomUUID().toString(),
            videoCall = false
          )
        }
      ) {
        Icon(
          imageVector = WhatsAppIcons.Call,
          contentDescription = null,
          tint = Color.White
        )
      }
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
      LazyColumn {
        items(
          items = whatsAppUsersUiState.data.whatsappUserList,
          key = { it.name }
        ) {
          WhatsAppCallHistory(whatsAppUser = it) {
            onHistoryItemClick(it)
          }
        }
      }
    }
  }
}
