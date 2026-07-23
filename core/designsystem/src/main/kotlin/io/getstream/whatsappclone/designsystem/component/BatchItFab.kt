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

package io.getstream.whatsappclone.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.getstream.whatsappclone.designsystem.theme.BatchItMotion

@Composable
fun BatchItFab(
  onClick: () -> Unit,
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = 58.dp,
  containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary,
  contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondary
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.92f else 1f,
    animationSpec = BatchItMotion.SnappySpring,
    label = "fabScale"
  )

  FloatingActionButton(
    modifier = modifier
      .size(size)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      },
    onClick = onClick,
    shape = CircleShape,
    containerColor = containerColor,
    contentColor = contentColor,
    elevation = FloatingActionButtonDefaults.elevation(
      defaultElevation = 4.dp,
      pressedElevation = 8.dp
    ),
    interactionSource = interactionSource
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = contentColor
    )
  }
}
