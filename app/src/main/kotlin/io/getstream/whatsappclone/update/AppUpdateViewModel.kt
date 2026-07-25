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
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.log.streamLog
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface AppUpdateUiState {
  data object Idle : AppUpdateUiState
  data class Checking(val userInitiated: Boolean = false) : AppUpdateUiState
  data class Available(val info: AppUpdateInfo) : AppUpdateUiState
  data class Downloading(
    val info: AppUpdateInfo,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val progress: Float = 0f
  ) : AppUpdateUiState
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
  private var pendingInstallFile: File? = null
  private var receiverRegistered = false
  private var progressJob: Job? = null

  private val downloadReceiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
      val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
      if (id != downloadId) return
      progressJob?.cancel()
      val info = pendingInfo ?: return
      val file = resolveDownloadedFile(id)
      if (file != null && file.exists()) {
        pendingInstallFile = file
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
    if (state is AppUpdateUiState.ReadyToInstall && state.info.forceUpdate) return
    _uiState.value = AppUpdateUiState.Dismissed
  }

  fun startDownload(info: AppUpdateInfo) {
    try {
      ensureReceiver()
      pendingInfo = info
      clearStaleApk(info.versionCode)
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
      startProgressPolling(info)
    } catch (error: Throwable) {
      streamLog { "APK download failed: ${error.message}" }
      _uiState.value = AppUpdateUiState.Error(error.message ?: "Download failed")
    }
  }

  /** Called when the host activity resumes (e.g. after granting unknown-sources). */
  fun onHostResumed() {
    val file = pendingInstallFile ?: return
    if (!file.exists()) return
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      !context.packageManager.canRequestPackageInstalls()
    ) {
      return
    }
    if (_uiState.value is AppUpdateUiState.ReadyToInstall) {
      installApk(file)
    }
  }

  fun installApk(file: File) {
    try {
      pendingInstallFile = file
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
      ) {
        val info = pendingInfo
        if (info != null) {
          _uiState.value = AppUpdateUiState.ReadyToInstall(info, file)
        }
        context.startActivity(
          Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
          ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
      }
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
        "Cannot install update. Allow “Install unknown apps” for BatchIt, " +
          "and make sure you are not mixing debug and release builds."
      )
    }
  }

  private fun startProgressPolling(info: AppUpdateInfo) {
    progressJob?.cancel()
    progressJob = viewModelScope.launch {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      while (isActive) {
        val progress = queryProgress(dm, downloadId) ?: break
        if (_uiState.value !is AppUpdateUiState.Downloading) break
        _uiState.value = AppUpdateUiState.Downloading(
          info = info,
          downloadedBytes = progress.downloaded,
          totalBytes = progress.total,
          progress = progress.fraction
        )
        if (progress.status == DownloadManager.STATUS_SUCCESSFUL ||
          progress.status == DownloadManager.STATUS_FAILED
        ) {
          break
        }
        delay(PROGRESS_POLL_MS)
      }
    }
  }

  private data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val fraction: Float,
    val status: Int
  )

  private fun queryProgress(dm: DownloadManager, id: Long): DownloadProgress? {
    if (id < 0L) return null
    val cursor: Cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
    cursor.use {
      if (!it.moveToFirst()) return null
      val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
      val downloadedIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
      val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
      val status = if (statusIdx >= 0) it.getInt(statusIdx) else -1
      val downloaded = if (downloadedIdx >= 0) it.getLong(downloadedIdx) else 0L
      val total = if (totalIdx >= 0) it.getLong(totalIdx) else -1L
      val fraction = when {
        total > 0L -> (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        else -> 0f
      }
      return DownloadProgress(downloaded, total, fraction, status)
    }
  }

  private fun clearStaleApk(versionCode: Int) {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
    val target = File(dir, "batchit-update-$versionCode.apk")
    if (target.exists()) {
      runCatching { target.delete() }
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
    progressJob?.cancel()
    if (receiverRegistered) {
      runCatching { context.unregisterReceiver(downloadReceiver) }
      receiverRegistered = false
    }
    super.onCleared()
  }

  companion object {
    private const val PROGRESS_POLL_MS = 250L
  }
}
