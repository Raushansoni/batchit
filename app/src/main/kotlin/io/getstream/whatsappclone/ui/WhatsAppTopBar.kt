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

package io.getstream.whatsappclone.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.getstream.whatsappclone.R
import io.getstream.whatsappclone.designsystem.icon.WhatsAppIcons
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getChromeContentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppTopBar(
  onSettingsClick: () -> Unit = {},
  onSearchClick: () -> Unit = {}
) {
  val chrome = getChromeContentColor()

  TopAppBar(
    modifier = Modifier.fillMaxWidth(),
    title = {
      Text(
        text = stringResource(id = R.string.app_name),
        color = chrome,
        style = MaterialTheme.typography.titleLarge
      )
    },
    actions = {
      IconButton(onClick = onSearchClick) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = WhatsAppIcons.Search,
          tint = chrome,
          contentDescription = "Search"
        )
      }
      IconButton(onClick = onSettingsClick) {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = WhatsAppIcons.MoreVert,
          tint = chrome,
          contentDescription = stringResource(id = R.string.settings)
        )
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.primary,
      titleContentColor = chrome,
      actionIconContentColor = chrome
    )
  )
}

@Preview
@Composable
private fun WhatsAppTopBarPreview() {
  WhatsAppCloneComposeTheme(darkTheme = false) {
    WhatsAppTopBar()
  }
}

@Preview
@Composable
private fun WhatsAppTopBarDarkPreview() {
  WhatsAppCloneComposeTheme(darkTheme = true) {
    WhatsAppTopBar()
  }
}
