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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

  AccountProfileContent(
    profile = profile,
    onBackClick = onBackClick,
    onSave = settingsViewModel::updateProfile
  )
}

@Composable
private fun AccountProfileContent(
  profile: UserProfile,
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
}

@Preview
@Composable
private fun AccountProfileScreenPreview() {
  WhatsAppCloneComposeTheme {
    AccountProfileContent(
      profile = UserProfile(),
      onBackClick = {},
      onSave = { _, _ -> }
    )
  }
}
