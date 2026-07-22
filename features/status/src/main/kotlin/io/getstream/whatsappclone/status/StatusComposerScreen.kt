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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import io.getstream.whatsappclone.designsystem.theme.GREEN500

@Composable
fun StatusComposerScreen(
  isSaving: Boolean,
  onClose: () -> Unit,
  onPostText: (String) -> Unit,
  onPostImage: (Uri, String) -> Unit
) {
  var text by remember { mutableStateOf("") }
  var selectedImage by remember { mutableStateOf<Uri?>(null) }

  val imagePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      selectedImage = uri
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0B141A))
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(onClick = onClose, enabled = !isSaving) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(id = R.string.status_close),
            tint = Color.White
          )
        }
        Text(
          text = stringResource(id = R.string.status_compose_title),
          style = MaterialTheme.typography.titleMedium,
          color = Color.White
        )
        Spacer(modifier = Modifier.size(48.dp))
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        val image = selectedImage
        if (image != null) {
          GlideImage(
            modifier = Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(8.dp)),
            imageModel = { image },
            imageOptions = ImageOptions(contentScale = ContentScale.Fit)
          )
        } else {
          OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = text,
            onValueChange = { text = it },
            placeholder = {
              Text(
                text = stringResource(id = R.string.status_type_hint),
                color = Color.White.copy(alpha = 0.6f)
              )
            },
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White),
            enabled = !isSaving
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        TextButton(
          onClick = { imagePicker.launch("image/*") },
          enabled = !isSaving
        ) {
          Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = Color.White
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = stringResource(id = R.string.status_pick_image),
            color = Color.White
          )
        }

        Button(
          onClick = {
            val image = selectedImage
            when {
              image != null -> onPostImage(image, text)
              text.isNotBlank() -> onPostText(text)
            }
          },
          enabled = !isSaving && (selectedImage != null || text.isNotBlank()),
          colors = ButtonDefaults.buttonColors(containerColor = GREEN500),
          shape = CircleShape,
          modifier = Modifier.size(48.dp)
        ) {
          if (isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
          } else {
            Icon(
              imageVector = Icons.Default.Send,
              contentDescription = stringResource(id = R.string.status_post),
              tint = Color.White
            )
          }
        }
      }
    }
  }
}
