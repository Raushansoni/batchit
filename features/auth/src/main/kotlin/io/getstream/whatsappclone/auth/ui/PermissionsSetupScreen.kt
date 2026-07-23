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

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.getstream.whatsappclone.auth.R
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsSetupScreen(
  onFinished: () -> Unit
) {
  val permissions = remember {
    buildList {
      add(Manifest.permission.CAMERA)
      add(Manifest.permission.RECORD_AUDIO)
      add(Manifest.permission.READ_CONTACTS)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
      }
    }
  }
  val permissionsState = rememberMultiplePermissionsState(permissions)
  var requestedOnce by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (!permissionsState.allPermissionsGranted) {
      requestedOnce = true
      permissionsState.launchMultiplePermissionRequest()
    }
  }

  LaunchedEffect(permissionsState.allPermissionsGranted, requestedOnce) {
    if (permissionsState.allPermissionsGranted && requestedOnce) {
      onFinished()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(id = R.string.auth_permissions_title),
      style = MaterialTheme.typography.headlineSmall,
      color = getTitleColor(),
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(id = R.string.auth_permissions_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onTertiary,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = stringResource(id = R.string.auth_permissions_list),
      style = MaterialTheme.typography.bodyMedium,
      color = getTitleColor(),
      textAlign = TextAlign.Start,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(32.dp))

    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = {
        if (permissionsState.allPermissionsGranted) {
          onFinished()
        } else {
          requestedOnce = true
          permissionsState.launchMultiplePermissionRequest()
        }
      },
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
      Text(
        text = stringResource(
          id = if (permissionsState.allPermissionsGranted) {
            R.string.auth_continue
          } else {
            R.string.auth_permissions_allow
          }
        ),
        color = MaterialTheme.colorScheme.onSecondary
      )
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onFinished) {
      Text(
        text = stringResource(id = R.string.auth_permissions_skip),
        color = MaterialTheme.colorScheme.secondary
      )
    }
  }
}

@Preview
@Composable
private fun PermissionsSetupScreenPreview() {
  WhatsAppCloneComposeTheme {
    PermissionsSetupScreen(onFinished = {})
  }
}
