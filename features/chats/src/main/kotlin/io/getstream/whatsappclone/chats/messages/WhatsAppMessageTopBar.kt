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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.User
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.designsystem.component.BatchItAvatar
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingIndicator
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getChromeContentColor
import io.getstream.whatsappclone.uistate.WhatsAppMessageUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppMessageTopBar(
  messageUiState: WhatsAppMessageUiState,
  navigateToVideoCall: (Boolean) -> Unit,
  onBackClick: () -> Unit,
  onStarLatestMessage: () -> Unit = {},
  onShareLocation: () -> Unit = {}
) {
  val chrome = getChromeContentColor()

  TopAppBar(
    modifier = Modifier.fillMaxWidth(),
    navigationIcon = {
      IconButton(onClick = onBackClick) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = WhatsAppIcons.ArrowBack,
          tint = chrome,
          contentDescription = null
        )
      }
    },
    title = {
      WhatsAppMessageUserInfo(
        messageUiState = messageUiState,
        contentColor = chrome
      )
    },
    actions = {
      IconButton(onClick = onShareLocation) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = Icons.Default.LocationOn,
          tint = chrome,
          contentDescription = stringResource(id = R.string.share_location)
        )
      }
      IconButton(onClick = onStarLatestMessage) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = Icons.Default.Star,
          tint = chrome,
          contentDescription = stringResource(id = R.string.star_message)
        )
      }
      IconButton(onClick = { navigateToVideoCall(true) }) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = WhatsAppIcons.Video,
          tint = chrome,
          contentDescription = null
        )
      }
      IconButton(onClick = { navigateToVideoCall(false) }) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = WhatsAppIcons.Call,
          tint = chrome,
          contentDescription = null
        )
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.primary,
      titleContentColor = chrome,
      navigationIconContentColor = chrome,
      actionIconContentColor = chrome
    )
  )
}

@Composable
private fun WhatsAppMessageUserInfo(
  messageUiState: WhatsAppMessageUiState,
  contentColor: androidx.compose.ui.graphics.Color
) {
  when (messageUiState) {
    WhatsAppMessageUiState.Loading -> WhatsAppLoadingIndicator()
    WhatsAppMessageUiState.Error -> Unit
    is WhatsAppMessageUiState.Success -> {
      val peer = rememberPeer(messageUiState.data)
      Row(verticalAlignment = Alignment.CenterVertically) {
        BatchItAvatar(
          imageUrl = peer.image.takeIf { it.isNotEmpty() }
            ?: io.getstream.whatsappclone.designsystem.R.drawable.stream_logo,
          size = 34.dp
        )

        Text(
          modifier = Modifier.padding(start = 12.dp),
          text = peer.name.takeIf { it.isNotBlank() } ?: peer.id,
          color = contentColor,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1
        )
      }
    }
  }
}

/**
 * Returns the user on the other side of this 1:1 channel so the header shows the
 * peer's name and avatar instead of the signed-in user or empty channel metadata.
 */
@Composable
private fun rememberPeer(channel: Channel): User {
  val currentUser by ChatClient.instance().clientState.user.collectAsStateWithLifecycle()
  val myId = currentUser?.id
  return remember(channel.id, channel.members, channel.name, channel.image, myId) {
    resolvePeerUser(channel = channel, myId = myId)
  }
}

internal fun resolvePeerUser(channel: Channel, myId: String?): User {
  val members = channel.members.map { it.user }
  // Prefer any member that is not me — never fall back to the local user.
  members.firstOrNull { myId != null && it.id != myId }?.let { return it }

  // messaging:{sortedId1}-{sortedId2} — parse the other uid from the channel id.
  val rawId = channel.id.substringAfter(delimiter = ":", missingDelimiterValue = channel.id)
  if (!myId.isNullOrBlank() && rawId.contains('-')) {
    val otherId = rawId.split('-').firstOrNull { it.isNotBlank() && it != myId }
    if (!otherId.isNullOrBlank()) {
      members.firstOrNull { it.id == otherId }?.let { return it }
      return User(
        id = otherId,
        name = channel.name.takeIf { it.isNotBlank() } ?: otherId,
        image = channel.image
      )
    }
  }

  // Group / named channel: use channel metadata rather than the first member (often me).
  if (channel.name.isNotBlank()) {
    return User(id = channel.id, name = channel.name, image = channel.image)
  }

  return members.firstOrNull { !myId.isNullOrBlank() && it.id != myId }
    ?: User(id = channel.id, name = "Chat", image = channel.image)
}

@Preview
@Composable
private fun WhatsAppTopBarPreview() {
  WhatsAppCloneComposeTheme {
    WhatsAppMessageTopBar(
      messageUiState = WhatsAppMessageUiState.Loading,
      navigateToVideoCall = {},
      onBackClick = {}
    )
  }
}

@Preview
@Composable
private fun WhatsAppTopBarDarkPreview() {
  WhatsAppCloneComposeTheme(darkTheme = true) {
    WhatsAppMessageTopBar(
      messageUiState = WhatsAppMessageUiState.Loading,
      navigateToVideoCall = {},
      onBackClick = {}
    )
  }
}
