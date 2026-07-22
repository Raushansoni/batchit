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

package io.getstream.whatsappclone.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.theme.GREEN500
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor
import io.getstream.whatsappclone.settings.PrivacySettings
import io.getstream.whatsappclone.settings.R
import io.getstream.whatsappclone.settings.SettingsViewModel

@Composable
fun PrivacySettingsScreen(
  onBackClick: () -> Unit,
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val privacy by settingsViewModel.privacySettings.collectAsStateWithLifecycle()

  PrivacySettingsContent(
    privacy = privacy,
    onBackClick = onBackClick,
    onLastSeenChange = settingsViewModel::setLastSeenVisible,
    onReadReceiptsChange = settingsViewModel::setReadReceiptsEnabled,
    onProfilePhotoChange = settingsViewModel::setProfilePhotoVisible
  )
}

@Composable
private fun PrivacySettingsContent(
  privacy: PrivacySettings,
  onBackClick: () -> Unit,
  onLastSeenChange: (Boolean) -> Unit,
  onReadReceiptsChange: (Boolean) -> Unit,
  onProfilePhotoChange: (Boolean) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onBackClick) {
        Icon(
          imageVector = Icons.Rounded.ArrowBack,
          contentDescription = null,
          tint = getTitleColor()
        )
      }
      Text(
        text = stringResource(id = R.string.settings_privacy_title),
        style = MaterialTheme.typography.titleLarge,
        color = getTitleColor()
      )
    }

    PrivacyToggleRow(
      title = stringResource(id = R.string.settings_last_seen),
      description = stringResource(id = R.string.settings_last_seen_desc),
      checked = privacy.lastSeenVisible,
      onCheckedChange = onLastSeenChange
    )
    PrivacyToggleRow(
      title = stringResource(id = R.string.settings_read_receipts),
      description = stringResource(id = R.string.settings_read_receipts_desc),
      checked = privacy.readReceiptsEnabled,
      onCheckedChange = onReadReceiptsChange
    )
    PrivacyToggleRow(
      title = stringResource(id = R.string.settings_profile_photo),
      description = stringResource(id = R.string.settings_profile_photo_desc),
      checked = privacy.profilePhotoVisible,
      onCheckedChange = onProfilePhotoChange
    )
  }
}

@Composable
private fun PrivacyToggleRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = getTitleColor()
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiary
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = GREEN500,
        checkedTrackColor = GREEN500.copy(alpha = 0.45f)
      )
    )
  }
}

@Preview
@Composable
private fun PrivacySettingsScreenPreview() {
  WhatsAppCloneComposeTheme {
    PrivacySettingsContent(
      privacy = PrivacySettings(),
      onBackClick = {},
      onLastSeenChange = {},
      onReadReceiptsChange = {},
      onProfilePhotoChange = {}
    )
  }
}
