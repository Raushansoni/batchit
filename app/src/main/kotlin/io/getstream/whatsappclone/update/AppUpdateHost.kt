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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.R
import io.getstream.whatsappclone.designsystem.theme.GREEN450
import java.util.Locale

@Composable
fun AppUpdateHost(
  enabled: Boolean,
  viewModel: AppUpdateViewModel? = null
) {
  val activity = LocalContext.current as ComponentActivity
  val resolvedViewModel = viewModel ?: hiltViewModel(activity)
  val state by resolvedViewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  LaunchedEffect(enabled) {
    if (enabled) {
      resolvedViewModel.checkForUpdate(userInitiated = false)
    }
  }

  DisposableEffect(lifecycleOwner, resolvedViewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        resolvedViewModel.onHostResumed()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = stringResource(
                R.string.update_downloading_message,
                s.info.versionName
              )
            )
            if (s.totalBytes > 0L) {
              LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth(),
                color = GREEN450,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
              )
            } else {
              LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = GREEN450,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
              )
            }
            Spacer(modifier = Modifier.height(0.dp))
            Text(
              text = formatDownloadProgress(s.downloadedBytes, s.totalBytes, s.progress),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        confirmButton = {}
      )
    }

    is AppUpdateUiState.ReadyToInstall -> {
      AlertDialog(
        onDismissRequest = {
          if (!s.info.forceUpdate) resolvedViewModel.dismiss()
        },
        title = { Text(text = stringResource(R.string.update_ready_title)) },
        text = {
          Text(text = stringResource(R.string.update_ready_message))
        },
        confirmButton = {
          TextButton(onClick = { resolvedViewModel.installApk(s.file) }) {
            Text(text = stringResource(R.string.update_install))
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

private fun formatDownloadProgress(downloaded: Long, total: Long, progress: Float): String {
  return if (total > 0L) {
    val percent = (progress * 100f).toInt().coerceIn(0, 100)
    String.format(
      Locale.US,
      "%d%% · %s / %s",
      percent,
      formatBytes(downloaded),
      formatBytes(total)
    )
  } else if (downloaded > 0L) {
    formatBytes(downloaded)
  } else {
    "Starting…"
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
  val mb = kb / 1024.0
  return String.format(Locale.US, "%.1f MB", mb)
}
