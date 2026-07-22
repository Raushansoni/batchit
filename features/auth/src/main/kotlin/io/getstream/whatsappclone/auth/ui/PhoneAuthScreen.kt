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

import android.app.Activity
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.getstream.whatsappclone.auth.BatchItAuthConfig
import io.getstream.whatsappclone.auth.R
import io.getstream.whatsappclone.designsystem.theme.GREEN500
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor

@Composable
fun PhoneAuthScreen(
  isSubmitting: Boolean,
  onContinue: (phone: String, activity: Activity) -> Unit,
  onDemoContinue: () -> Unit
) {
  var phone by remember { mutableStateOf("") }
  val context = LocalContext.current
  val activity = context as? Activity

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(id = R.string.auth_welcome_title),
      style = MaterialTheme.typography.headlineSmall,
      color = getTitleColor()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(id = R.string.auth_welcome_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onTertiary
    )
    Spacer(modifier = Modifier.height(32.dp))

    OutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      value = phone,
      onValueChange = { phone = it },
      label = { Text(text = stringResource(id = R.string.auth_phone_hint)) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      enabled = !isSubmitting
    )

    Spacer(modifier = Modifier.height(24.dp))

    if (isSubmitting) {
      CircularProgressIndicator(color = GREEN500)
    } else {
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          if (activity != null && phone.isNotBlank()) {
            onContinue(phone, activity)
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = GREEN500),
        enabled = phone.isNotBlank()
      ) {
        Text(text = stringResource(id = R.string.auth_continue), color = Color.White)
      }

      if (BatchItAuthConfig.USE_DEMO_AUTH) {
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDemoContinue) {
          Text(
            text = stringResource(id = R.string.auth_demo_continue),
            color = GREEN500
          )
        }
      }
    }
  }
}

@Preview
@Composable
private fun PhoneAuthScreenPreview() {
  WhatsAppCloneComposeTheme {
    PhoneAuthScreen(
      isSubmitting = false,
      onContinue = { _, _ -> },
      onDemoContinue = {}
    )
  }
}
