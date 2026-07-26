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

package io.getstream.whatsappclone.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.whatsappclone.designsystem.theme.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository
) : ViewModel() {

  val privacySettings: StateFlow<PrivacySettings> = settingsRepository.privacySettings
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = PrivacySettings()
    )

  val notificationSettings: StateFlow<NotificationSettings> =
    settingsRepository.notificationSettings
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationSettings()
      )

  val storageSettings: StateFlow<StorageSettings> = settingsRepository.storageSettings
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = StorageSettings()
    )

  val userProfile: StateFlow<UserProfile> = settingsRepository.userProfile
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = UserProfile()
    )

  val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ThemeMode.SYSTEM
    )

  val blockedUserIds: StateFlow<Set<String>> = settingsRepository.blockedUserIds
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = emptySet()
    )

  private val _imageUploading = MutableStateFlow(false)
  val imageUploading: StateFlow<Boolean> = _imageUploading.asStateFlow()

  private val _profileMessage = MutableStateFlow<String?>(null)
  val profileMessage: StateFlow<String?> = _profileMessage.asStateFlow()

  fun clearProfileMessage() {
    _profileMessage.value = null
  }

  init {
    settingsRepository.syncProfileFromFirebaseAuth()
    settingsRepository.syncPrivacyFromFirestore()
  }

  fun setLastSeenVisible(visible: Boolean) {
    settingsRepository.setLastSeenVisible(visible)
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    settingsRepository.setReadReceiptsEnabled(enabled)
  }

  fun setProfilePhotoVisible(visible: Boolean) {
    settingsRepository.setProfilePhotoVisible(visible)
  }

  fun setMessageNotifications(enabled: Boolean) {
    settingsRepository.setMessageNotifications(enabled)
  }

  fun setCallNotifications(enabled: Boolean) {
    settingsRepository.setCallNotifications(enabled)
  }

  fun setNotificationPreview(enabled: Boolean) {
    settingsRepository.setNotificationPreview(enabled)
  }

  fun setAutoDownloadWifi(enabled: Boolean) {
    settingsRepository.setAutoDownloadWifi(enabled)
  }

  fun setAutoDownloadCellular(enabled: Boolean) {
    settingsRepository.setAutoDownloadCellular(enabled)
  }

  fun setMediaQualityHigh(enabled: Boolean) {
    settingsRepository.setMediaQualityHigh(enabled)
  }

  fun setThemeMode(mode: ThemeMode) {
    settingsRepository.setThemeMode(mode)
  }

  fun updateProfile(name: String, about: String) {
    viewModelScope.launch {
      settingsRepository.updateProfile(name = name.trim(), about = about.trim())
      _profileMessage.value = "Profile saved"
    }
  }

  fun uploadProfileImage(uri: Uri) {
    viewModelScope.launch {
      _imageUploading.value = true
      val url = settingsRepository.uploadProfileImage(uri)
      _imageUploading.value = false
      _profileMessage.value = url?.let { "Profile photo updated" } ?: "Could not update photo"
    }
  }

  fun blockUser(userId: String) {
    settingsRepository.blockUser(userId.trim())
  }

  fun unblockUser(userId: String) {
    settingsRepository.unblockUser(userId.trim())
  }

  fun syncProfileFromFirebaseAuth() {
    settingsRepository.syncProfileFromFirebaseAuth()
  }

  fun syncPrivacyFromFirestore() {
    settingsRepository.syncPrivacyFromFirestore()
  }
}
