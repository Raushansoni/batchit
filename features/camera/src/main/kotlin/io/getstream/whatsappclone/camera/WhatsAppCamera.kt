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

package io.getstream.whatsappclone.camera

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.DARK_GREEN300
import io.getstream.whatsappclone.designsystem.theme.GREEN500

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WhatsAppCamera() {
  val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

  LaunchedEffect(Unit) {
    if (!cameraPermission.status.isGranted) {
      cameraPermission.launchPermissionRequest()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(DARK_GREEN300),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(24.dp)
    ) {
      Box(
        modifier = Modifier
          .size(88.dp)
          .background(GREEN500, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = WhatsAppIcons.Camera,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(40.dp)
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = if (cameraPermission.status.isGranted) {
          "Camera ready — capture photos from chat attachments or Status."
        } else {
          "Allow camera access to take photos and videos for chats and Status."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        textAlign = TextAlign.Center
      )
    }
  }
}
