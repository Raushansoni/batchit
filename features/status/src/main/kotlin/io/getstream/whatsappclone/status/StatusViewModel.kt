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

package io.getstream.whatsappclone.status

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.whatsappclone.status.model.StatusItem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatusUiState(
  val myStatuses: List<StatusItem> = emptyList(),
  val contactStatuses: List<StatusItem> = emptyList(),
  val recentContacts: List<StatusItem> = emptyList(),
  val isLoading: Boolean = true,
  val isSaving: Boolean = false,
  val errorMessage: String? = null
)

data class StatusViewerInfo(
  val viewerNames: Map<String, String> = emptyMap(),
  val isLoadingViewers: Boolean = false
)

@HiltViewModel
class StatusViewModel @Inject constructor(
  private val statusRepository: StatusRepository
) : ViewModel() {

  private val _isSaving = MutableStateFlow(false)
  private val _errorMessage = MutableStateFlow<String?>(null)
  private val _isLoading = MutableStateFlow(true)
  private val _viewerInfo = MutableStateFlow(StatusViewerInfo())
  val viewerInfo: StateFlow<StatusViewerInfo> = _viewerInfo
  private var hasLoadedOnce = false
  private var isRefreshing = false
  private var lastRefreshAtMillis = 0L

  val uiState: StateFlow<StatusUiState> = combine(
    statusRepository.myStatuses,
    statusRepository.contactStatuses,
    _isLoading,
    _isSaving,
    _errorMessage
  ) { mine, contacts, loading, saving, error ->
    StatusUiState(
      myStatuses = mine,
      contactStatuses = contacts,
      recentContacts = contacts
        .groupBy { it.userId }
        .mapNotNull { (_, items) -> items.maxByOrNull { it.createdAt } }
        .sortedByDescending { it.createdAt },
      isLoading = loading,
      isSaving = saving,
      errorMessage = error
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = StatusUiState()
  )

  fun onTabActive() {
    if (!hasLoadedOnce || System.currentTimeMillis() - lastRefreshAtMillis >= STATUS_REFRESH_INTERVAL_MS) {
      refresh()
    }
  }

  fun refresh() {
    if (isRefreshing) return
    viewModelScope.launch {
      isRefreshing = true
      try {
        _isLoading.value = !hasLoadedOnce
        statusRepository.refresh()
          .onFailure { _errorMessage.value = it.message ?: "Could not load statuses" }
        hasLoadedOnce = true
        lastRefreshAtMillis = System.currentTimeMillis()
      } finally {
        _isLoading.value = false
        isRefreshing = false
      }
    }
  }

  fun createTextStatus(text: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _isSaving.value = true
      _errorMessage.value = null
      statusRepository.createTextStatus(text)
        .onSuccess { onDone() }
        .onFailure { _errorMessage.value = it.message }
      _isSaving.value = false
    }
  }

  fun createImageStatus(uri: Uri, caption: String = "", onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _isSaving.value = true
      _errorMessage.value = null
      statusRepository.createImageStatus(uri, caption)
        .onSuccess { onDone() }
        .onFailure { _errorMessage.value = it.message }
      _isSaving.value = false
    }
  }

  fun createVideoStatus(uri: Uri, caption: String = "", onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _isSaving.value = true
      _errorMessage.value = null
      statusRepository.createVideoStatus(uri, caption)
        .onSuccess { onDone() }
        .onFailure { _errorMessage.value = it.message }
      _isSaving.value = false
    }
  }

  fun markViewed(statusId: String) {
    viewModelScope.launch {
      statusRepository.markViewed(statusId)
    }
  }

  fun loadViewersForStatus(status: StatusItem) {
    val myId = statusRepository.currentUserId() ?: return
    if (status.userId != myId) return
    viewModelScope.launch {
      _viewerInfo.value = StatusViewerInfo(isLoadingViewers = true)
      val names = statusRepository.resolveViewerNames(status.viewedBy)
      _viewerInfo.value = StatusViewerInfo(viewerNames = names, isLoadingViewers = false)
    }
  }

  fun clearViewerInfo() {
    _viewerInfo.value = StatusViewerInfo()
  }

  fun statusesForUser(userId: String): List<StatusItem> {
    val state = uiState.value
    return (state.myStatuses + state.contactStatuses)
      .filter { it.userId == userId }
      .sortedBy { it.createdAt }
  }

  fun isOwnStatusUser(userId: String): Boolean =
    statusRepository.currentUserId() == userId

  fun clearError() {
    _errorMessage.value = null
  }

  private companion object {
    const val STATUS_REFRESH_INTERVAL_MS = 30_000L
  }
}
