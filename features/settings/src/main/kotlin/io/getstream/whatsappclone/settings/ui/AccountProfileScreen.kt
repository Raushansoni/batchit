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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.designsystem.component.BatchItAvatar
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor
import io.getstream.whatsappclone.settings.R
import io.getstream.whatsappclone.settings.SettingsViewModel
import io.getstream.whatsappclone.settings.UserProfile

@Composable
fun AccountProfileScreen(
  onBackClick: () -> Unit,
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val profile by settingsViewModel.userProfile.collectAsStateWithLifecycle()
  val uploading by settingsViewModel.imageUploading.collectAsStateWithLifecycle()
  val message by settingsViewModel.profileMessage.collectAsStateWithLifecycle()

  val pickImage = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri -> if (uri != null) settingsViewModel.uploadProfileImage(uri) }

  AccountProfileContent(
    profile = profile,
    uploading = uploading,
    message = message,
    onPickImage = { pickImage.launch("image/*") },
    onDismissMessage = settingsViewModel::clearProfileMessage,
    onBackClick = onBackClick,
    onSave = settingsViewModel::updateProfile
  )
}

@Composable
private fun AccountProfileContent(
  profile: UserProfile,
  uploading: Boolean,
  message: String?,
  onPickImage: () -> Unit,
  onDismissMessage: () -> Unit,
  onBackClick: () -> Unit,
  onSave: (String, String) -> Unit
) {
  var name by rememberSaveable(profile.name) { mutableStateOf(profile.name) }
  var about by rememberSaveable(profile.about) { mutableStateOf(profile.about) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
  ) {
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
        text = stringResource(id = R.string.settings_account_title),
        style = MaterialTheme.typography.titleLarge,
        color = getTitleColor()
      )
    }

    // WhatsApp-style profile photo with change affordance.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 24.dp),
      contentAlignment = Alignment.Center
    ) {
      BatchItAvatar(
        imageUrl = profile.imageUrl.takeIf { it.isNotEmpty() }
          ?: io.getstream.whatsappclone.designsystem.R.drawable.stream_logo,
        size = 120.dp
      )
      if (uploading) {
        CircularProgressIndicator(
          modifier = Modifier.size(120.dp),
          strokeWidth = 2.dp
        )
      } else {
        Surface(
          modifier = Modifier
            .size(36.dp)
            .align(Alignment.BottomEnd)
            .padding(end = 56.dp),
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.primary,
          onClick = onPickImage
        ) {
          Icon(
            imageVector = Icons.Rounded.PhotoCamera,
            contentDescription = stringResource(id = R.string.settings_account_change_photo),
            tint = MaterialTheme.colorScheme.onPrimary
          )
        }
      }
    }

    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      text = stringResource(id = R.string.settings_account_change_photo_hint),
      style = MaterialTheme.typography.bodySmall
    )

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedTextField(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      value = name,
      onValueChange = { name = it },
      label = { Text(stringResource(id = R.string.settings_account_name)) },
      singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      value = about,
      onValueChange = { about = it },
      label = { Text(stringResource(id = R.string.settings_account_about)) },
      minLines = 2
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      onClick = { onSave(name, about) }
    ) {
      Text(text = stringResource(id = R.string.settings_account_save))
    }
  }

  if (message != null) {
    AlertDialog(
      onDismissRequest = onDismissMessage,
      text = { Text(text = message) },
      confirmButton = {
        TextButton(onClick = onDismissMessage) {
          Text(text = stringResource(id = R.string.cancel))
        }
      }
    )
  }
}

@Preview
@Composable
private fun AccountProfileScreenPreview() {
  WhatsAppCloneComposeTheme {
    AccountProfileContent(
      profile = UserProfile(),
      uploading = false,
      message = null,
      onPickImage = {},
      onDismissMessage = {},
      onBackClick = {},
      onSave = { _, _ -> }
    )
  }
}
