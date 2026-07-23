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

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import io.getstream.whatsappclone.status.model.StatusItem
import io.getstream.whatsappclone.status.model.StatusType
import kotlinx.coroutines.delay

private const val AUTO_ADVANCE_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewerScreen(
  statuses: List<StatusItem>,
  initialIndex: Int = 0,
  isOwnStatus: Boolean = false,
  viewerNames: Map<String, String> = emptyMap(),
  isLoadingViewers: Boolean = false,
  onClose: () -> Unit,
  onStatusViewed: (String) -> Unit,
  onShowViewers: (StatusItem) -> Unit = {}
) {
  if (statuses.isEmpty()) return

  var index by remember {
    mutableIntStateOf(initialIndex.coerceIn(0, statuses.lastIndex.coerceAtLeast(0)))
  }
  var showViewersSheet by remember { mutableStateOf(false) }
  val current = statuses[index]
  var videoCompleted by remember(current.id) { mutableStateOf(false) }

  LaunchedEffect(current.id) {
    onStatusViewed(current.id)
    videoCompleted = false
  }

  LaunchedEffect(index, current.id, current.type, videoCompleted) {
    if (current.type == StatusType.VIDEO) {
      if (videoCompleted) {
        advanceOrClose(index, statuses.lastIndex, onClose) { index = it }
      }
      return@LaunchedEffect
    }
    val duration = AUTO_ADVANCE_MS
    delay(duration)
    advanceOrClose(index, statuses.lastIndex, onClose) { index = it }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    when (current.type) {
      StatusType.TEXT -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF075E54)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            modifier = Modifier.padding(32.dp),
            text = current.text,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
          )
        }
      }
      StatusType.IMAGE -> {
        GlideImage(
          modifier = Modifier.fillMaxSize(),
          imageModel = { current.mediaUrl },
          imageOptions = ImageOptions(contentScale = ContentScale.Fit),
          previewPlaceholder = painterResource(
            id = io.getstream.whatsappclone.designsystem.R.drawable.placeholder
          )
        )
        if (current.text.isNotBlank()) {
          Text(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(24.dp),
            text = current.text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center
          )
        }
      }
      StatusType.VIDEO -> {
        StatusVideoPlayer(
          mediaUrl = current.mediaUrl,
          onComplete = { videoCompleted = true },
          modifier = Modifier.fillMaxSize()
        )
        if (current.text.isNotBlank()) {
          Text(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(24.dp),
            text = current.text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center
          )
        }
      }
    }

    Row(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
          ) {
            if (index > 0) {
              index -= 1
            } else {
              onClose()
            }
          }
      )
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
          ) {
            if (index < statuses.lastIndex) {
              index += 1
            } else {
              onClose()
            }
          }
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
      StatusProgressBars(
        count = statuses.size,
        currentIndex = index,
        key = current.id,
        durationMs = if (current.type == StatusType.VIDEO) AUTO_ADVANCE_MS * 3 else AUTO_ADVANCE_MS
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        GlideImage(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape),
          imageModel = {
            current.userImage.ifBlank { "https://i.pravatar.cc/150?u=${current.userId}" }
          },
          previewPlaceholder = painterResource(
            id = io.getstream.whatsappclone.designsystem.R.drawable.placeholder
          )
        )
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
          Text(
            text = current.userName,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
          )
        }
        if (isOwnStatus) {
          IconButton(
            onClick = {
              onShowViewers(current)
              showViewersSheet = true
            }
          ) {
            Icon(
              imageVector = Icons.Default.Visibility,
              contentDescription = stringResource(id = R.string.status_viewers),
              tint = Color.White
            )
          }
        }
        IconButton(onClick = onClose) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(id = R.string.status_close),
            tint = Color.White
          )
        }
      }
    }

    if (isOwnStatus && showViewersSheet) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
        onDismissRequest = { showViewersSheet = false },
        sheetState = sheetState
      ) {
        StatusViewersSheet(
          status = current,
          viewerNames = viewerNames,
          isLoading = isLoadingViewers
        )
      }
    }
  }
}

@Composable
private fun StatusViewersSheet(
  status: StatusItem,
  viewerNames: Map<String, String>,
  isLoading: Boolean
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 24.dp)
  ) {
    Text(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      text = stringResource(id = R.string.status_viewed_by_count, status.viewedBy.size),
      style = MaterialTheme.typography.titleMedium
    )
    if (isLoading) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
    } else if (status.viewedBy.isEmpty()) {
      Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        text = stringResource(id = R.string.status_no_viewers),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      LazyColumn {
        items(status.viewedBy.distinct(), key = { it }) { viewerId ->
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            text = viewerNames[viewerId] ?: viewerId,
            style = MaterialTheme.typography.bodyLarge
          )
        }
      }
    }
  }
}

@Composable
private fun StatusVideoPlayer(
  mediaUrl: String,
  onComplete: () -> Unit,
  modifier: Modifier = Modifier
) {
  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      VideoView(ctx).apply {
        setOnCompletionListener { onComplete() }
        setOnErrorListener { _, _, _ ->
          onComplete()
          true
        }
      }
    },
    update = { videoView ->
      val uri = Uri.parse(mediaUrl)
      if (videoView.tag != mediaUrl) {
        videoView.tag = mediaUrl
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { it.start() }
      }
    }
  )

  DisposableEffect(mediaUrl) {
    onDispose { }
  }
}

private fun advanceOrClose(
  index: Int,
  lastIndex: Int,
  onClose: () -> Unit,
  setIndex: (Int) -> Unit
) {
  if (index < lastIndex) {
    setIndex(index + 1)
  } else {
    onClose()
  }
}

@Composable
private fun StatusProgressBars(
  count: Int,
  currentIndex: Int,
  key: String,
  durationMs: Long = AUTO_ADVANCE_MS
) {
  val progress = remember(key) { Animatable(0f) }

  LaunchedEffect(key) {
    progress.snapTo(0f)
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(
        durationMillis = durationMs.toInt(),
        easing = LinearEasing
      )
    )
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    repeat(count) { i ->
      val value = when {
        i < currentIndex -> 1f
        i == currentIndex -> progress.value
        else -> 0f
      }
      LinearProgressIndicator(
        progress = { value },
        modifier = Modifier
          .weight(1f)
          .height(3.dp)
          .clip(RoundedCornerShape(50)),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.35f)
      )
    }
  }
}
