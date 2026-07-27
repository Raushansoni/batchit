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

package io.getstream.whatsappclone.chats.attachments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import io.getstream.chat.android.compose.ui.attachments.AttachmentFactory
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.whatsappclone.designsystem.theme.GREEN450

internal const val LOCATION_ATTACHMENT_TYPE = "location"

fun locationAttachmentFactory(): AttachmentFactory = AttachmentFactory(
  canHandle = { attachments ->
    attachments.any { it.type == LOCATION_ATTACHMENT_TYPE }
  },
  content = { modifier, attachmentState ->
    val attachment = attachmentState.message.attachments.first {
      it.type == LOCATION_ATTACHMENT_TYPE
    }
    LocationAttachmentContent(
      modifier = modifier,
      imageUrl = attachment.imageUrl.orEmpty(),
      mapsUrl = attachment.titleLink
        ?: attachment.extraData["maps_url"]?.toString().orEmpty(),
      latitude = attachment.extraData["latitude"]?.toString(),
      longitude = attachment.extraData["longitude"]?.toString()
    )
  },
  previewContent = { modifier, attachments, onRemove ->
    val attachment = attachments.first { it.type == LOCATION_ATTACHMENT_TYPE }
    LocationAttachmentContent(
      modifier = modifier,
      imageUrl = attachment.imageUrl.orEmpty(),
      mapsUrl = attachment.titleLink
        ?: attachment.extraData["maps_url"]?.toString().orEmpty(),
      latitude = attachment.extraData["latitude"]?.toString(),
      longitude = attachment.extraData["longitude"]?.toString(),
      compact = true,
      onRemove = { onRemove(attachment) }
    )
  },
  textFormatter = { "📍 Location" }
)

@Composable
private fun LocationAttachmentContent(
  modifier: Modifier = Modifier,
  imageUrl: String,
  mapsUrl: String,
  latitude: String?,
  longitude: String?,
  compact: Boolean = false,
  onRemove: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val height = if (compact) 120.dp else 168.dp
  val openMaps = {
    val uri = mapsUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
      ?: if (latitude != null && longitude != null) {
        Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
      } else {
        null
      }
    if (uri != null) {
      runCatching {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(ChatTheme.colors.appBackground)
      .clickable(onClick = openMaps)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(height)
        .background(
          Brush.verticalGradient(
            listOf(Color(0xFFDCE9D5), Color(0xFFB7D0A8), Color(0xFF8FBC8F))
          )
        )
    ) {
      if (imageUrl.isNotBlank()) {
        GlideImage(
          imageModel = { imageUrl },
          imageOptions = ImageOptions(contentScale = ContentScale.Crop),
          modifier = Modifier.matchParentSize()
        )
      }
      Icon(
        imageVector = Icons.Filled.LocationOn,
        contentDescription = null,
        tint = Color(0xFFE53935),
        modifier = Modifier
          .align(Alignment.Center)
          .size(40.dp)
      )
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(
        text = "Shared location",
        color = ChatTheme.colors.textHighEmphasis,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = when {
          latitude != null && longitude != null ->
            "%.5f, %.5f".format(latitude.toDoubleOrNull() ?: 0.0, longitude.toDoubleOrNull() ?: 0.0)
          else -> "Open in Maps"
        },
        color = ChatTheme.colors.textLowEmphasis,
        fontSize = 12.sp
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Open in Google Maps",
        color = GREEN450,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
      )
      if (onRemove != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Remove",
          color = ChatTheme.colors.errorAccent,
          fontSize = 12.sp,
          modifier = Modifier.clickable(onClick = onRemove)
        )
      }
    }
  }
}
