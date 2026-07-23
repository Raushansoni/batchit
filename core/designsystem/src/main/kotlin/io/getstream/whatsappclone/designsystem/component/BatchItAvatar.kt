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

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import io.getstream.whatsappclone.designsystem.R

@Composable
fun BatchItAvatar(
  imageUrl: Any?,
  modifier: Modifier = Modifier,
  size: Dp = 48.dp
) {
  val px = with(LocalDensity.current) { size.roundToPx() }
  GlideImage(
    modifier = modifier
      .size(size)
      .clip(CircleShape),
    imageModel = { imageUrl },
    imageOptions = ImageOptions(contentScale = ContentScale.Crop),
    requestOptions = {
      RequestOptions()
        .override(px, px)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .dontAnimate()
    },
    previewPlaceholder = painterResource(id = R.drawable.placeholder),
    failure = {
      androidx.compose.foundation.Image(
        painter = painterResource(id = R.drawable.placeholder),
        contentDescription = null,
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop
      )
    }
  )
}
