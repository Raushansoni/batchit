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

package io.getstream.whatsappclone.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor
import io.getstream.whatsappclone.settings.R
import io.getstream.whatsappclone.settings.SettingsViewModel

@Composable
fun BlockedContactsScreen(
  onBackClick: () -> Unit,
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val blockedIds by settingsViewModel.blockedUserIds.collectAsStateWithLifecycle()

  BlockedContactsContent(
    blockedIds = blockedIds.toList().sorted(),
    onBackClick = onBackClick,
    onUnblock = settingsViewModel::unblockUser
  )
}

@Composable
private fun BlockedContactsContent(
  blockedIds: List<String>,
  onBackClick: () -> Unit,
  onUnblock: (String) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    SettingsTopBar(
      title = stringResource(id = R.string.settings_blocked_title),
      onBackClick = onBackClick
    )

    if (blockedIds.isEmpty()) {
      Text(
        modifier = Modifier.padding(16.dp),
        text = stringResource(id = R.string.settings_blocked_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(blockedIds, key = { it }) { userId ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              modifier = Modifier.weight(1f),
              text = userId,
              style = MaterialTheme.typography.bodyLarge,
              color = getTitleColor()
            )
            TextButton(onClick = { onUnblock(userId) }) {
              Text(text = stringResource(id = R.string.settings_blocked_unblock))
            }
          }
        }
      }
    }
  }
}

@Preview
@Composable
private fun BlockedContactsScreenPreview() {
  WhatsAppCloneComposeTheme {
    BlockedContactsContent(
      blockedIds = listOf("user_123", "user_456"),
      onBackClick = {},
      onUnblock = {}
    )
  }
}
