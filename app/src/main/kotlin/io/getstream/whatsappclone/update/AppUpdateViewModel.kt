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

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.log.streamLog
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppUpdateUiState {
  data object Idle : AppUpdateUiState
  data class Checking(val userInitiated: Boolean = false) : AppUpdateUiState
  data class Available(val info: AppUpdateInfo) : AppUpdateUiState
  data class Downloading(val info: AppUpdateInfo) : AppUpdateUiState
  data class ReadyToInstall(val info: AppUpdateInfo, val file: File) : AppUpdateUiState
  data object UpToDate : AppUpdateUiState
  data class Error(val message: String) : AppUpdateUiState
  data object Dismissed : AppUpdateUiState
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repository: AppUpdateRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
  val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

  private var downloadId: Long = -1L
  private var pendingInfo: AppUpdateInfo? = null
  private var receiverRegistered = false

  private val downloadReceiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
      val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
      if (id != downloadId) return
      val info = pendingInfo ?: return
      val file = resolveDownloadedFile(id)
      if (file != null && file.exists()) {
        _uiState.value = AppUpdateUiState.ReadyToInstall(info, file)
        installApk(file)
      } else {
        _uiState.value = AppUpdateUiState.Error("Download finished but APK was not found")
      }
    }
  }

  fun checkForUpdate(userInitiated: Boolean = false) {
    when (_uiState.value) {
      is AppUpdateUiState.Available,
      is AppUpdateUiState.Downloading,
      is AppUpdateUiState.ReadyToInstall,
      is AppUpdateUiState.Checking -> return
      else -> Unit
    }
    viewModelScope.launch {
      _uiState.value = AppUpdateUiState.Checking(userInitiated)
      val latest = repository.fetchLatestUpdate()
      if (latest == null) {
        _uiState.value = if (userInitiated) {
          AppUpdateUiState.Error("Could not check for updates. Try again later.")
        } else {
          AppUpdateUiState.Idle
        }
        return@launch
      }
      val current = repository.currentVersionCode()
      _uiState.value = when {
        latest.versionCode.toLong() > current -> AppUpdateUiState.Available(latest)
        userInitiated -> AppUpdateUiState.UpToDate
        else -> AppUpdateUiState.Idle
      }
    }
  }

  fun dismiss() {
    val state = _uiState.value
    if (state is AppUpdateUiState.Available && state.info.forceUpdate) return
    _uiState.value = AppUpdateUiState.Dismissed
  }

  fun startDownload(info: AppUpdateInfo) {
    try {
      ensureReceiver()
      pendingInfo = info
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val request = DownloadManager.Request(Uri.parse(info.apkUrl))
        .setTitle("BatchIt ${info.versionName}")
        .setDescription("Downloading update…")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(
          context,
          Environment.DIRECTORY_DOWNLOADS,
          "batchit-update-${info.versionCode}.apk"
        )
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
      downloadId = dm.enqueue(request)
      _uiState.value = AppUpdateUiState.Downloading(info)
    } catch (error: Throwable) {
      streamLog { "APK download failed: ${error.message}" }
      _uiState.value = AppUpdateUiState.Error(error.message ?: "Download failed")
    }
  }

  fun installApk(file: File) {
    try {
      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(intent)
    } catch (error: Throwable) {
      streamLog { "APK install intent failed: ${error.message}" }
      _uiState.value = AppUpdateUiState.Error(
        "Cannot open installer. Allow “Install unknown apps” for BatchIt."
      )
    }
  }

  private fun ensureReceiver() {
    if (receiverRegistered) return
    val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
    ContextCompat.registerReceiver(
      context,
      downloadReceiver,
      filter,
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    receiverRegistered = true
  }

  private fun resolveDownloadedFile(id: Long): File? {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query().setFilterById(id)
    val cursor: Cursor = dm.query(query) ?: return null
    cursor.use {
      if (!it.moveToFirst()) return null
      val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
      if (statusIdx >= 0 && it.getInt(statusIdx) != DownloadManager.STATUS_SUCCESSFUL) {
        return null
      }
      val localUriIdx = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
      if (localUriIdx < 0) return null
      val localUri = it.getString(localUriIdx) ?: return null
      return Uri.parse(localUri).path?.let { path -> File(path) }
    }
  }

  override fun onCleared() {
    if (receiverRegistered) {
      runCatching { context.unregisterReceiver(downloadReceiver) }
      receiverRegistered = false
    }
    super.onCleared()
  }
}
