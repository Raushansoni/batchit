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

package io.getstream.whatsappclone.video

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.getstream.whatsappclone.designsystem.component.BatchItAvatar
import io.getstream.whatsappclone.designsystem.theme.DARK_GREEN300
import io.getstream.whatsappclone.designsystem.theme.GREEN450
import io.getstream.whatsappclone.designsystem.theme.GREEN700

internal val CallScreenBg = Color(0xFF0B141A)
internal val CallControlIdle = Color(0xFF3B4A54)
internal val CallControlActive = Color(0xFFE9EDEF)
internal val CallControlIconOnDark = Color(0xFF111B21)
internal val CallDeclineRed = Color(0xFFF15C6D)
internal val CallAcceptGreen = GREEN450
internal val CallSecondaryText = Color(0xFF8696A0)

@Composable
internal fun WhatsAppCallBackground(content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(GREEN700.copy(alpha = 0.55f), CallScreenBg, DARK_GREEN300)
        )
      )
  ) {
    content()
  }
}

@Composable
internal fun WhatsAppCallPeerHeader(
  name: String,
  status: String,
  imageUrl: String?,
  modifier: Modifier = Modifier,
  showPulse: Boolean = false
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 24.dp, vertical = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "End-to-end encrypted",
      color = CallSecondaryText,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(28.dp))

    val pulse = if (showPulse) {
      val transition = rememberInfiniteTransition(label = "avatarPulse")
      transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
          animation = tween(900, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse
        ),
        label = "avatarPulseScale"
      ).value
    } else {
      1f
    }

    BatchItAvatar(
      imageUrl = imageUrl?.takeIf { it.isNotBlank() },
      size = 128.dp,
      modifier = Modifier.scale(pulse)
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text(
      text = name.ifBlank { "BatchIt User" },
      color = Color.White,
      fontSize = 28.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = status,
      color = CallSecondaryText,
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
internal fun WhatsAppRoundCallButton(
  icon: ImageVector,
  contentDescription: String,
  background: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  iconTint: Color = Color.White,
  size: Dp = 64.dp,
  enabled: Boolean = true
) {
  val interaction = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(if (enabled) background else background.copy(alpha = 0.4f))
      .clickable(
        enabled = enabled,
        interactionSource = interaction,
        indication = ripple(bounded = true, color = Color.White),
        onClick = onClick
      ),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (enabled) iconTint else iconTint.copy(alpha = 0.5f),
      modifier = Modifier.size(size * 0.42f)
    )
  }
}

@Composable
internal fun WhatsAppLabeledCallButton(
  icon: ImageVector,
  label: String,
  background: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  iconTint: Color = Color.White,
  size: Dp = 58.dp,
  enabled: Boolean = true
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    WhatsAppRoundCallButton(
      icon = icon,
      contentDescription = label,
      background = background,
      onClick = onClick,
      iconTint = iconTint,
      size = size,
      enabled = enabled
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = label,
      color = CallSecondaryText,
      fontSize = 12.sp,
      maxLines = 1
    )
  }
}

@Composable
internal fun WhatsAppIncomingActions(
  isVideo: Boolean,
  busy: Boolean,
  onDecline: () -> Unit,
  onAccept: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 36.dp, vertical = 36.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    WhatsAppLabeledCallButton(
      icon = Icons.Filled.CallEnd,
      label = "Decline",
      background = CallDeclineRed,
      onClick = onDecline,
      size = 68.dp,
      enabled = !busy
    )
    WhatsAppLabeledCallButton(
      icon = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
      label = "Accept",
      background = CallAcceptGreen,
      onClick = onAccept,
      size = 68.dp,
      enabled = !busy
    )
  }
}

@Composable
internal fun WhatsAppOutgoingActions(onEnd: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp, vertical = 36.dp),
    horizontalArrangement = Arrangement.Center
  ) {
    WhatsAppLabeledCallButton(
      icon = Icons.Filled.CallEnd,
      label = "End",
      background = CallDeclineRed,
      onClick = onEnd,
      size = 72.dp
    )
  }
}

@Composable
internal fun WhatsAppInCallControls(
  isVideoCall: Boolean,
  isMuted: Boolean,
  isSpeakerOn: Boolean,
  isCameraOn: Boolean,
  onToggleMute: () -> Unit,
  onToggleSpeaker: () -> Unit,
  onToggleCamera: () -> Unit,
  onFlipCamera: () -> Unit,
  onEnd: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 18.dp, vertical = 28.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
  ) {
    WhatsAppLabeledCallButton(
      icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
      label = "Speaker",
      background = if (isSpeakerOn) CallControlActive else CallControlIdle,
      iconTint = if (isSpeakerOn) CallControlIconOnDark else Color.White,
      onClick = onToggleSpeaker,
      size = 54.dp
    )
    if (isVideoCall) {
      WhatsAppLabeledCallButton(
        icon = Icons.Filled.Cameraswitch,
        label = "Flip",
        background = CallControlIdle,
        onClick = onFlipCamera,
        size = 54.dp,
        enabled = isCameraOn
      )
      WhatsAppLabeledCallButton(
        icon = if (isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
        label = "Video",
        background = if (isCameraOn) CallControlActive else CallControlIdle,
        iconTint = if (isCameraOn) CallControlIconOnDark else Color.White,
        onClick = onToggleCamera,
        size = 54.dp
      )
    } else {
      WhatsAppLabeledCallButton(
        icon = Icons.Filled.Videocam,
        label = "Video",
        background = CallControlIdle,
        onClick = onToggleCamera,
        size = 54.dp,
        enabled = false
      )
    }
    WhatsAppLabeledCallButton(
      icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
      label = if (isMuted) "Unmute" else "Mute",
      background = if (isMuted) CallControlActive else CallControlIdle,
      iconTint = if (isMuted) CallControlIconOnDark else Color.White,
      onClick = onToggleMute,
      size = 54.dp
    )
    WhatsAppLabeledCallButton(
      icon = Icons.Filled.CallEnd,
      label = "End",
      background = CallDeclineRed,
      onClick = onEnd,
      size = 64.dp
    )
  }
}

internal fun formatCallDuration(elapsedMs: Long): String {
  val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
  val minutes = totalSec / 60L
  val seconds = totalSec % 60L
  return "%d:%02d".format(minutes, seconds)
}
