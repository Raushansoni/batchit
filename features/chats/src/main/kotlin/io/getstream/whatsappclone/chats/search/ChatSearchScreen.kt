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

package io.getstream.whatsappclone.chats.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.chat.android.models.Channel
import io.getstream.whatsappclone.chats.R
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.getTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
  viewModel: ChatSearchViewModel = hiltViewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = R.string.chat_search_title)) },
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
      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.query,
        onValueChange = viewModel::onQueryChange,
        singleLine = true,
        label = { Text(text = stringResource(id = R.string.chat_search_hint)) }
      )

      Spacer(modifier = Modifier.height(12.dp))

      state.error?.let { message ->
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
      }

      when {
        state.isSearching -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
          }
        }

        state.query.isBlank() -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = stringResource(id = R.string.chat_search_prompt),
              color = MaterialTheme.colorScheme.onTertiary
            )
          }
        }

        state.results.isEmpty() -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = stringResource(id = R.string.chat_search_empty),
              color = MaterialTheme.colorScheme.onTertiary
            )
          }
        }

        else -> {
          LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
          ) {
            items(state.results, key = { it.cid }) { channel ->
              ChatSearchResultRow(
                channel = channel,
                onClick = { viewModel.openChannel(channel) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ChatSearchResultRow(
  channel: Channel,
  onClick: () -> Unit
) {
  val title = channel.name.takeIf { !it.isNullOrBlank() }
    ?: channel.members.joinToString { it.user.name }
  val subtitle = channel.messages.lastOrNull()?.text.orEmpty()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp)
  ) {
    Text(
      text = title,
      color = getTitleColor(),
      fontWeight = FontWeight.Medium
    )
    if (subtitle.isNotBlank()) {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiary,
        maxLines = 1
      )
    }
  }
}
