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

package io.getstream.whatsappclone.update

import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.R

@Composable
fun AppUpdateHost(
  enabled: Boolean,
  viewModel: AppUpdateViewModel? = null
) {
  val activity = LocalContext.current as ComponentActivity
  val resolvedViewModel = viewModel ?: hiltViewModel(activity)
  val state by resolvedViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(enabled) {
    if (enabled) {
      resolvedViewModel.checkForUpdate(userInitiated = false)
    }
  }

  when (val s = state) {
    is AppUpdateUiState.Checking -> {
      if (s.userInitiated) {
        AlertDialog(
          onDismissRequest = {},
          title = { Text(text = stringResource(R.string.update_checking_title)) },
          text = { Text(text = stringResource(R.string.update_checking_message)) },
          confirmButton = {}
        )
      }
    }

    is AppUpdateUiState.Available -> {
      AlertDialog(
        onDismissRequest = {
          if (!s.info.forceUpdate) resolvedViewModel.dismiss()
        },
        title = {
          Text(text = stringResource(R.string.update_available_title))
        },
        text = {
          val notes = s.info.notes.ifBlank {
            stringResource(R.string.update_available_default_notes)
          }
          Text(
            text = stringResource(
              R.string.update_available_message,
              s.info.versionName.ifBlank { s.info.versionCode.toString() },
              notes
            )
          )
        },
        confirmButton = {
          TextButton(onClick = { resolvedViewModel.startDownload(s.info) }) {
            Text(text = stringResource(R.string.update_button))
          }
        },
        dismissButton = {
          if (!s.info.forceUpdate) {
            TextButton(onClick = { resolvedViewModel.dismiss() }) {
              Text(text = stringResource(R.string.update_later))
            }
          }
        }
      )
    }

    is AppUpdateUiState.Downloading -> {
      AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(R.string.update_downloading_title)) },
        text = {
          Text(
            text = stringResource(
              R.string.update_downloading_message,
              s.info.versionName
            )
          )
        },
        confirmButton = {}
      )
    }

    is AppUpdateUiState.ReadyToInstall -> {
      AlertDialog(
        onDismissRequest = {
          if (!s.info.forceUpdate) resolvedViewModel.dismiss()
        },
        title = { Text(text = "Update ready") },
        text = {
          Text(text = "The update has downloaded. Tap Install to continue with Android's installer.")
        },
        confirmButton = {
          TextButton(onClick = { resolvedViewModel.installApk(s.file) }) {
            Text(text = "Install")
          }
        },
        dismissButton = {
          if (!s.info.forceUpdate) {
            TextButton(onClick = { resolvedViewModel.dismiss() }) {
              Text(text = stringResource(R.string.update_later))
            }
          }
        }
      )
    }

    AppUpdateUiState.UpToDate -> {
      AlertDialog(
        onDismissRequest = { resolvedViewModel.dismiss() },
        title = { Text(text = stringResource(R.string.update_up_to_date_title)) },
        text = { Text(text = stringResource(R.string.update_up_to_date_message)) },
        confirmButton = {
          TextButton(onClick = { resolvedViewModel.dismiss() }) {
            Text(text = stringResource(android.R.string.ok))
          }
        }
      )
    }

    is AppUpdateUiState.Error -> {
      AlertDialog(
        onDismissRequest = { resolvedViewModel.dismiss() },
        title = { Text(text = stringResource(R.string.update_error_title)) },
        text = { Text(text = s.message) },
        confirmButton = {
          TextButton(onClick = { resolvedViewModel.dismiss() }) {
            Text(text = stringResource(android.R.string.ok))
          }
        }
      )
    }

    else -> Unit
  }
}
