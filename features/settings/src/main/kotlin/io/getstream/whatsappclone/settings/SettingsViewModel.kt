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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.whatsappclone.designsystem.theme.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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

  fun setLastSeenVisible(visible: Boolean) {
    settingsRepository.setLastSeenVisible(visible)
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    settingsRepository.setReadReceiptsEnabled(enabled)
  }

  fun setProfilePhotoVisible(visible: Boolean) {
    settingsRepository.setProfilePhotoVisible(visible)
  }

  fun setThemeMode(mode: ThemeMode) {
    settingsRepository.setThemeMode(mode)
  }
}
