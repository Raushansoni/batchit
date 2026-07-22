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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.landscapist.glide.GlideImage
import io.getstream.whatsappclone.designsystem.theme.GREEN500
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor
import io.getstream.whatsappclone.settings.R
import io.getstream.whatsappclone.settings.SettingsViewModel
import io.getstream.whatsappclone.settings.UserProfile

@Composable
fun BatchItSettingsScreen(
  onPrivacyClick: () -> Unit,
  onAccountClick: () -> Unit = {},
  onNotificationsClick: () -> Unit = {},
  onStorageClick: () -> Unit = {},
  onHelpClick: () -> Unit = {},
  onDeleteAccountClick: () -> Unit = {},
  onSignOutClick: () -> Unit = {},
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val profile by settingsViewModel.userProfile.collectAsStateWithLifecycle()

  BatchItSettingsContent(
    profile = profile,
    onPrivacyClick = onPrivacyClick,
    onAccountClick = onAccountClick,
    onNotificationsClick = onNotificationsClick,
    onStorageClick = onStorageClick,
    onHelpClick = onHelpClick,
    onDeleteAccountClick = onDeleteAccountClick,
    onSignOutClick = onSignOutClick
  )
}

@Composable
private fun BatchItSettingsContent(
  profile: UserProfile,
  onPrivacyClick: () -> Unit,
  onAccountClick: () -> Unit,
  onNotificationsClick: () -> Unit,
  onStorageClick: () -> Unit,
  onHelpClick: () -> Unit,
  onDeleteAccountClick: () -> Unit,
  onSignOutClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
  ) {
    Text(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      text = stringResource(id = R.string.settings_title),
      style = MaterialTheme.typography.headlineSmall,
      color = getTitleColor()
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onAccountClick)
        .padding(horizontal = 16.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      GlideImage(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape),
        imageModel = { profile.imageUrl },
        previewPlaceholder = painterResource(
          id = io.getstream.whatsappclone.designsystem.R.drawable.placeholder
        )
      )
      Spacer(modifier = Modifier.width(16.dp))
      Column {
        Text(
          text = profile.name,
          style = MaterialTheme.typography.titleMedium,
          color = getTitleColor()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = profile.about,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onTertiary
        )
      }
    }

    Divider()

    SettingsRow(
      icon = Icons.Outlined.AccountCircle,
      title = stringResource(id = R.string.settings_account),
      onClick = onAccountClick
    )
    SettingsRow(
      icon = Icons.Outlined.Lock,
      title = stringResource(id = R.string.settings_privacy),
      onClick = onPrivacyClick
    )
    SettingsRow(
      icon = Icons.Outlined.Notifications,
      title = stringResource(id = R.string.settings_notifications),
      onClick = onNotificationsClick
    )
    SettingsRow(
      icon = Icons.Outlined.Storage,
      title = stringResource(id = R.string.settings_storage),
      onClick = onStorageClick
    )
    SettingsRow(
      icon = Icons.Outlined.HelpOutline,
      title = stringResource(id = R.string.settings_help),
      onClick = onHelpClick
    )

    Divider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsRow(
      icon = Icons.Outlined.Delete,
      title = stringResource(id = R.string.settings_delete_account),
      onClick = onDeleteAccountClick,
      tint = MaterialTheme.colorScheme.error
    )
    SettingsRow(
      icon = Icons.Outlined.Logout,
      title = stringResource(id = R.string.settings_sign_out),
      onClick = onSignOutClick,
      tint = GREEN500
    )
  }
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  title: String,
  onClick: () -> Unit,
  tint: androidx.compose.ui.graphics.Color = GREEN500
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint
    )
    Spacer(modifier = Modifier.width(24.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      color = getTitleColor()
    )
  }
}

@Preview
@Composable
private fun BatchItSettingsScreenPreview() {
  WhatsAppCloneComposeTheme {
    BatchItSettingsContent(
      profile = UserProfile(),
      onPrivacyClick = {},
      onAccountClick = {},
      onNotificationsClick = {},
      onStorageClick = {},
      onHelpClick = {},
      onDeleteAccountClick = {},
      onSignOutClick = {}
    )
  }
}
