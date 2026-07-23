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

package io.getstream.whatsappclone.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.component.BatchItAvatar
import io.getstream.whatsappclone.designsystem.component.BatchItFab
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.BatchItMotion
import io.getstream.whatsappclone.designsystem.theme.GREEN400
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor
import io.getstream.whatsappclone.status.model.StatusItem
import java.text.DateFormat
import java.util.Date

private sealed interface StatusOverlay {
  data object None : StatusOverlay
  data object Composer : StatusOverlay
  data class Viewer(val userId: String, val startIndex: Int = 0) : StatusOverlay
}

@Composable
fun WhatsAppStatus(
  isActive: Boolean = true,
  viewModel: StatusViewModel = hiltViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var overlay by remember { mutableStateOf<StatusOverlay>(StatusOverlay.None) }

  LaunchedEffect(isActive) {
    if (isActive) viewModel.onTabActive()
  }

  Box(modifier = Modifier.fillMaxSize()) {
    StatusListScreen(
      uiState = uiState,
      onMyStatusClick = {
        if (uiState.myStatuses.isNotEmpty()) {
          overlay = StatusOverlay.Viewer(
            userId = uiState.myStatuses.first().userId
          )
        } else {
          overlay = StatusOverlay.Composer
        }
      },
      onContactClick = { status ->
        overlay = StatusOverlay.Viewer(userId = status.userId)
      },
      onFabClick = { overlay = StatusOverlay.Composer }
    )

    AnimatedContent(
      targetState = overlay,
      transitionSpec = {
        (fadeIn(BatchItMotion.MediumTween) + slideInVertically { it / 8 }) togetherWith
          (fadeOut(BatchItMotion.FastTween) + slideOutVertically { it / 10 })
      },
      label = "statusOverlay"
    ) { current ->
      when (current) {
        StatusOverlay.None -> Unit
        StatusOverlay.Composer -> {
          StatusComposerScreen(
            isSaving = uiState.isSaving,
            onClose = { overlay = StatusOverlay.None },
            onPostText = { text ->
              viewModel.createTextStatus(text) {
                overlay = StatusOverlay.None
              }
            },
            onPostImage = { uri, caption ->
              viewModel.createImageStatus(uri, caption) {
                overlay = StatusOverlay.None
              }
            }
          )
        }
        is StatusOverlay.Viewer -> {
          val statuses = viewModel.statusesForUser(current.userId)
          if (statuses.isNotEmpty()) {
            StatusViewerScreen(
              statuses = statuses,
              initialIndex = current.startIndex.coerceIn(0, statuses.lastIndex),
              onClose = { overlay = StatusOverlay.None },
              onStatusViewed = viewModel::markViewed
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatusListScreen(
  uiState: StatusUiState,
  onMyStatusClick: () -> Unit,
  onContactClick: (StatusItem) -> Unit,
  onFabClick: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 88.dp)
    ) {
      item {
        MyStatusRow(
          myStatuses = uiState.myStatuses,
          onClick = onMyStatusClick
        )
      }

      item {
        Text(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          text = stringResource(id = R.string.recent_updates),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onTertiary
        )
      }

      if (uiState.recentContacts.isEmpty()) {
        item(key = "empty-recent") {
          Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(id = R.string.status_no_recent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        items(
          items = uiState.recentContacts,
          key = { it.userId },
          contentType = { "contact-status" }
        ) { status ->
          ContactStatusRow(
            status = status,
            onClick = { onContactClick(status) }
          )
        }
      }
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp)
    ) {
      BatchItFab(
        modifier = Modifier.padding(bottom = 12.dp),
        onClick = onFabClick,
        icon = Icons.Default.Edit,
        contentDescription = stringResource(id = R.string.status_compose_text),
        size = 48.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.secondary
      )

      BatchItFab(
        onClick = onFabClick,
        icon = WhatsAppIcons.Camera,
        contentDescription = stringResource(id = R.string.status_add)
      )
    }
  }
}

@Composable
private fun MyStatusRow(
  myStatuses: List<StatusItem>,
  onClick: () -> Unit
) {
  val hasStatus = myStatuses.isNotEmpty()
  val imageUrl = myStatuses.firstOrNull()?.userImage?.takeIf { it.isNotBlank() }
  val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box {
      BatchItAvatar(
        imageUrl = imageUrl
          ?: io.getstream.whatsappclone.designsystem.R.drawable.placeholder,
        size = 56.dp,
        modifier = if (hasStatus) {
          Modifier.border(2.dp, GREEN400, CircleShape)
        } else {
          Modifier
        }
      )
      if (!hasStatus) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "+",
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.labelSmall
          )
        }
      }
    }

    Column(modifier = Modifier.padding(start = 12.dp)) {
      Text(
        text = stringResource(id = R.string.status_mine),
        style = MaterialTheme.typography.titleMedium,
        color = getTitleColor()
      )
      Spacer(modifier = Modifier.size(4.dp))
      Text(
        text = if (hasStatus) {
          timeFormatter.format(Date(myStatuses.maxOf { it.createdAt }))
        } else {
          stringResource(id = R.string.status_desc)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun ContactStatusRow(
  status: StatusItem,
  onClick: () -> Unit
) {
  val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
  val imageUrl = status.userImage.ifBlank {
    io.getstream.whatsappclone.designsystem.R.drawable.placeholder
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    BatchItAvatar(
      imageUrl = imageUrl,
      size = 56.dp,
      modifier = Modifier.border(2.dp, GREEN400, CircleShape)
    )

    Column(modifier = Modifier.padding(start = 12.dp)) {
      Text(
        text = status.userName,
        style = MaterialTheme.typography.titleMedium,
        color = getTitleColor()
      )
      Spacer(modifier = Modifier.size(4.dp))
      Text(
        text = timeFormatter.format(Date(status.createdAt)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Preview
@Composable
private fun WhatsAppStatusPreview() {
  WhatsAppCloneComposeTheme {
    StatusListScreen(
      uiState = StatusUiState(
        isLoading = false,
        contactStatuses = listOf(
          StatusItem(
            id = "1",
            userId = "a",
            userName = "Alice",
            userImage = "",
            text = "Hello"
          )
        )
      ),
      onMyStatusClick = {},
      onContactClick = {},
      onFabClick = {}
    )
  }
}
