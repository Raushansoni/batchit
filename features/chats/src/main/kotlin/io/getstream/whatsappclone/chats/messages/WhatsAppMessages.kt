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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.chat.android.compose.ui.messages.MessagesScreen
import io.getstream.chat.android.compose.viewmodel.messages.MessagesViewModelFactory
import io.getstream.whatsappclone.chats.theme.WhatsAppChatTheme

@Composable
fun WhatsAppMessages(
  channelId: String,
  whatsAppMessagesViewModel: WhatsAppMessagesViewModel = hiltViewModel()
) {
  val messageUiState by whatsAppMessagesViewModel.messageUiSate.collectAsStateWithLifecycle()
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

  WhatsAppChatTheme {
    Column(Modifier.fillMaxSize()) {
      WhatsAppMessageTopBar(
        messageUiState = messageUiState,
        navigateToVideoCall = onVideoCall,
        onBackClick = onBack
      )

      MessagesScreen(
        viewModelFactory = factory,
        showHeader = false,
        onBackPressed = onBack
      )
    }
  }
}
