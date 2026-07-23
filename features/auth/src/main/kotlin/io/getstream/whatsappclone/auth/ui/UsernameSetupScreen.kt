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

package io.getstream.whatsappclone.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.getstream.whatsappclone.auth.AuthRepository
import io.getstream.whatsappclone.auth.R
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor

@Composable
fun UsernameSetupScreen(
  isSubmitting: Boolean,
  onSave: (username: String) -> Unit
) {
  var username by remember { mutableStateOf("") }
  val normalized = AuthRepository.normalizeUsername(username)
  val isValid = Regex("^[a-zA-Z0-9_]{3,20}$").matches(normalized)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(id = R.string.auth_username_title),
      style = MaterialTheme.typography.headlineSmall,
      color = getTitleColor()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(id = R.string.auth_username_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onTertiary
    )
    Spacer(modifier = Modifier.height(32.dp))

    OutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      value = username,
      onValueChange = { value ->
        if (value.length <= 20 && value.all { it.isLetterOrDigit() || it == '_' }) {
          username = value
        }
      },
      label = { Text(text = stringResource(id = R.string.auth_username_hint)) },
      supportingText = {
        Text(text = stringResource(id = R.string.auth_username_rules))
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
      enabled = !isSubmitting
    )

    Spacer(modifier = Modifier.height(24.dp))

    if (isSubmitting) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
    } else {
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onSave(normalized) },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        enabled = isValid
      ) {
        Text(text = stringResource(id = R.string.auth_continue), color = MaterialTheme.colorScheme.onSecondary)
      }
    }
  }
}

@Preview
@Composable
private fun UsernameSetupScreenPreview() {
  WhatsAppCloneComposeTheme {
    UsernameSetupScreen(
      isSubmitting = false,
      onSave = {}
    )
  }
}
