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
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor
import io.getstream.whatsappclone.settings.NotificationSettings
import io.getstream.whatsappclone.settings.R
import io.getstream.whatsappclone.settings.SettingsViewModel

@Composable
fun NotificationsSettingsScreen(
  onBackClick: () -> Unit,
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val settings by settingsViewModel.notificationSettings.collectAsStateWithLifecycle()

  NotificationsSettingsContent(
    settings = settings,
    onBackClick = onBackClick,
    onMessageNotificationsChange = settingsViewModel::setMessageNotifications,
    onCallNotificationsChange = settingsViewModel::setCallNotifications,
    onNotificationPreviewChange = settingsViewModel::setNotificationPreview
  )
}

@Composable
private fun NotificationsSettingsContent(
  settings: NotificationSettings,
  onBackClick: () -> Unit,
  onMessageNotificationsChange: (Boolean) -> Unit,
  onCallNotificationsChange: (Boolean) -> Unit,
  onNotificationPreviewChange: (Boolean) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    SettingsTopBar(
      title = stringResource(id = R.string.settings_notifications_title),
      onBackClick = onBackClick
    )

    SettingsToggleRow(
      title = stringResource(id = R.string.settings_notifications_messages),
      description = stringResource(id = R.string.settings_notifications_messages_desc),
      checked = settings.messageNotifications,
      onCheckedChange = onMessageNotificationsChange
    )
    SettingsToggleRow(
      title = stringResource(id = R.string.settings_notifications_calls),
      description = stringResource(id = R.string.settings_notifications_calls_desc),
      checked = settings.callNotifications,
      onCheckedChange = onCallNotificationsChange
    )
    SettingsToggleRow(
      title = stringResource(id = R.string.settings_notifications_preview),
      description = stringResource(id = R.string.settings_notifications_preview_desc),
      checked = settings.notificationPreview,
      onCheckedChange = onNotificationPreviewChange
    )
  }
}

@Preview
@Composable
private fun NotificationsSettingsScreenPreview() {
  WhatsAppCloneComposeTheme {
    NotificationsSettingsContent(
      settings = NotificationSettings(),
      onBackClick = {},
      onMessageNotificationsChange = {},
      onCallNotificationsChange = {},
      onNotificationPreviewChange = {}
    )
  }
}
