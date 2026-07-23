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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.settings.PrivacySettings
import io.getstream.whatsappclone.settings.R
import io.getstream.whatsappclone.settings.SettingsViewModel

@Composable
fun PrivacySettingsScreen(
  onBackClick: () -> Unit,
  onBlockedContactsClick: () -> Unit = {},
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val privacy by settingsViewModel.privacySettings.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    settingsViewModel.syncPrivacyFromFirestore()
  }

  PrivacySettingsContent(
    privacy = privacy,
    onBackClick = onBackClick,
    onBlockedContactsClick = onBlockedContactsClick,
    onLastSeenChange = settingsViewModel::setLastSeenVisible,
    onReadReceiptsChange = settingsViewModel::setReadReceiptsEnabled,
    onProfilePhotoChange = settingsViewModel::setProfilePhotoVisible
  )
}

@Composable
private fun PrivacySettingsContent(
  privacy: PrivacySettings,
  onBackClick: () -> Unit,
  onBlockedContactsClick: () -> Unit,
  onLastSeenChange: (Boolean) -> Unit,
  onReadReceiptsChange: (Boolean) -> Unit,
  onProfilePhotoChange: (Boolean) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    SettingsTopBar(
      title = stringResource(id = R.string.settings_privacy_title),
      onBackClick = onBackClick
    )

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

    SettingsNavigationRow(
      title = stringResource(id = R.string.settings_blocked_contacts),
      description = stringResource(id = R.string.settings_blocked_contacts_desc),
      onClick = onBlockedContactsClick
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
  SettingsToggleRow(
    title = title,
    description = description,
    checked = checked,
    onCheckedChange = onCheckedChange
  )
}

@Preview
@Composable
private fun PrivacySettingsScreenPreview() {
  WhatsAppCloneComposeTheme {
    PrivacySettingsContent(
      privacy = PrivacySettings(),
      onBackClick = {},
      onBlockedContactsClick = {},
      onLastSeenChange = {},
      onReadReceiptsChange = {},
      onProfilePhotoChange = {}
    )
  }
}
