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

package io.getstream.whatsappclone.chats.starred

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.getTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
  viewModel: StarredMessagesViewModel = hiltViewModel()
) {
  val entries by viewModel.entries.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.refresh()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = R.string.starred_messages_title)) },
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
    if (entries.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(id = R.string.starred_messages_empty),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentPadding = PaddingValues(vertical = 8.dp)
      ) {
        items(entries, key = { "${it.channelId}:${it.messageId}" }) { entry ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { viewModel.openChannel(entry.channelId) }
              .padding(horizontal = 16.dp, vertical = 12.dp)
          ) {
            Text(
              text = entry.previewText.ifBlank {
                stringResource(id = R.string.starred_messages_no_preview)
              },
              style = MaterialTheme.typography.bodyLarge,
              color = getTitleColor(),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              modifier = Modifier.padding(top = 4.dp),
              text = entry.channelId,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { viewModel.unstar(entry) }) {
              Text(text = stringResource(id = R.string.starred_messages_unstar))
            }
          }
        }
      }
    }
  }
}
