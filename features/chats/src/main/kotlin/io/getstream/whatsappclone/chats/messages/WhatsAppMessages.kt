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

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.chat.android.compose.ui.messages.MessagesScreen
import io.getstream.chat.android.compose.viewmodel.messages.MessagesViewModelFactory
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.chats.theme.WhatsAppChatTheme

@Composable
fun WhatsAppMessages(
  channelId: String,
  whatsAppMessagesViewModel: WhatsAppMessagesViewModel = hiltViewModel()
) {
  val messageUiState by whatsAppMessagesViewModel.messageUiSate.collectAsStateWithLifecycle()
  val actionMessage by whatsAppMessagesViewModel.actionMessage.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val factory = remember(channelId, context) {
    MessagesViewModelFactory(
      context = context,
      channelId = channelId,
      messageLimit = 25
    )
  }
  val onBack = remember(whatsAppMessagesViewModel) {
    { whatsAppMessagesViewModel.handleEvents(WhatsAppMessageEvent.NavigateUp) }
  }
  val onVideoCall = remember(whatsAppMessagesViewModel, channelId) {
    { video: Boolean -> whatsAppMessagesViewModel.navigateToVideoCall(channelId, video) }
  }

  val requestLocation = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    val granted = result.values.any { it }
    if (granted) {
      whatsAppMessagesViewModel.shareLocationPlaceholder()
    } else {
      whatsAppMessagesViewModel.showLocationPermissionDenied()
    }
  }
  val onShareLocation = remember(whatsAppMessagesViewModel) {
    {
      if (whatsAppMessagesViewModel.hasLocationPermission()) {
        whatsAppMessagesViewModel.shareLocationPlaceholder()
      } else {
        requestLocation.launch(
          arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      }
    }
  }

  WhatsAppChatTheme {
    Column(Modifier.fillMaxSize()) {
      WhatsAppMessageTopBar(
        messageUiState = messageUiState,
        navigateToVideoCall = onVideoCall,
        onBackClick = onBack,
        onStarLatestMessage = whatsAppMessagesViewModel::starLatestMessage,
        onShareLocation = onShareLocation
      )

      MessagesScreen(
        viewModelFactory = factory,
        showHeader = false,
        onBackPressed = onBack
      )
    }
  }

  actionMessage?.let { message ->
    val openLocation = message == WhatsAppMessagesViewModel.ACTION_OPEN_LOCATION
    AlertDialog(
      onDismissRequest = whatsAppMessagesViewModel::clearActionMessage,
      text = {
        Text(
          text = if (openLocation) {
            "Turn on location to share your position"
          } else {
            message
          }
        )
      },
      confirmButton = {
        if (openLocation) {
          TextButton(
            onClick = {
              whatsAppMessagesViewModel.clearActionMessage()
              runCatching {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
              }
            }
          ) {
            Text(text = "Open location")
          }
        } else {
          TextButton(onClick = whatsAppMessagesViewModel::clearActionMessage) {
            Text(text = stringResource(id = R.string.cancel))
          }
        }
      },
      dismissButton = if (openLocation) {
        {
          TextButton(onClick = whatsAppMessagesViewModel::clearActionMessage) {
            Text(text = stringResource(id = R.string.cancel))
          }
        }
      } else {
        null
      }
    )
  }
}
