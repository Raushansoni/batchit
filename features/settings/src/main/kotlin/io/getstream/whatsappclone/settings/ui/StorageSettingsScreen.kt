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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.settings.R
import io.getstream.whatsappclone.settings.SettingsViewModel
import io.getstream.whatsappclone.settings.StorageSettings

@Composable
fun StorageSettingsScreen(
  onBackClick: () -> Unit,
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val settings by settingsViewModel.storageSettings.collectAsStateWithLifecycle()

  StorageSettingsContent(
    settings = settings,
    onBackClick = onBackClick,
    onAutoDownloadWifiChange = settingsViewModel::setAutoDownloadWifi,
    onAutoDownloadCellularChange = settingsViewModel::setAutoDownloadCellular,
    onMediaQualityHighChange = settingsViewModel::setMediaQualityHigh
  )
}

@Composable
private fun StorageSettingsContent(
  settings: StorageSettings,
  onBackClick: () -> Unit,
  onAutoDownloadWifiChange: (Boolean) -> Unit,
  onAutoDownloadCellularChange: (Boolean) -> Unit,
  onMediaQualityHighChange: (Boolean) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    SettingsTopBar(
      title = stringResource(id = R.string.settings_storage_title),
      onBackClick = onBackClick
    )

    SettingsToggleRow(
      title = stringResource(id = R.string.settings_storage_wifi),
      description = stringResource(id = R.string.settings_storage_wifi_desc),
      checked = settings.autoDownloadWifi,
      onCheckedChange = onAutoDownloadWifiChange
    )
    SettingsToggleRow(
      title = stringResource(id = R.string.settings_storage_cellular),
      description = stringResource(id = R.string.settings_storage_cellular_desc),
      checked = settings.autoDownloadCellular,
      onCheckedChange = onAutoDownloadCellularChange
    )
    SettingsToggleRow(
      title = stringResource(id = R.string.settings_storage_quality),
      description = stringResource(id = R.string.settings_storage_quality_desc),
      checked = settings.mediaQualityHigh,
      onCheckedChange = onMediaQualityHighChange
    )
  }
}

@Preview
@Composable
private fun StorageSettingsScreenPreview() {
  WhatsAppCloneComposeTheme {
    StorageSettingsContent(
      settings = StorageSettings(),
      onBackClick = {},
      onAutoDownloadWifiChange = {},
      onAutoDownloadCellularChange = {},
      onMediaQualityHighChange = {}
    )
  }
}
