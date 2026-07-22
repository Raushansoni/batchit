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

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PrivacySettings(
  val lastSeenVisible: Boolean = true,
  val readReceiptsEnabled: Boolean = true,
  val profilePhotoVisible: Boolean = true
)

data class UserProfile(
  val name: String = "BatchIt User",
  val about: String = "Hey there! I am using BatchIt.",
  val imageUrl: String = "https://placekitten.com/200/300"
)

@Singleton
class SettingsRepository @Inject constructor(
  @ApplicationContext context: Context
) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _privacySettings = MutableStateFlow(readPrivacySettings())
  val privacySettings: Flow<PrivacySettings> = _privacySettings.asStateFlow()

  private val _userProfile = MutableStateFlow(readUserProfile())
  val userProfile: Flow<UserProfile> = _userProfile.asStateFlow()

  fun setLastSeenVisible(visible: Boolean) {
    prefs.edit().putBoolean(KEY_LAST_SEEN, visible).apply()
    _privacySettings.update { it.copy(lastSeenVisible = visible) }
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_READ_RECEIPTS, enabled).apply()
    _privacySettings.update { it.copy(readReceiptsEnabled = enabled) }
  }

  fun setProfilePhotoVisible(visible: Boolean) {
    prefs.edit().putBoolean(KEY_PROFILE_PHOTO, visible).apply()
    _privacySettings.update { it.copy(profilePhotoVisible = visible) }
  }

  fun updateProfile(name: String, about: String, imageUrl: String = _userProfile.value.imageUrl) {
    prefs.edit()
      .putString(KEY_NAME, name)
      .putString(KEY_ABOUT, about)
      .putString(KEY_IMAGE, imageUrl)
      .apply()
    _userProfile.value = UserProfile(name = name, about = about, imageUrl = imageUrl)
  }

  private fun readPrivacySettings(): PrivacySettings = PrivacySettings(
    lastSeenVisible = prefs.getBoolean(KEY_LAST_SEEN, true),
    readReceiptsEnabled = prefs.getBoolean(KEY_READ_RECEIPTS, true),
    profilePhotoVisible = prefs.getBoolean(KEY_PROFILE_PHOTO, true)
  )

  private fun readUserProfile(): UserProfile = UserProfile(
    name = prefs.getString(KEY_NAME, null) ?: "BatchIt User",
    about = prefs.getString(KEY_ABOUT, null) ?: "Hey there! I am using BatchIt.",
    imageUrl = prefs.getString(KEY_IMAGE, null) ?: "https://placekitten.com/200/300"
  )

  companion object {
    private const val PREFS_NAME = "batchit_settings"
    private const val KEY_LAST_SEEN = "privacy_last_seen"
    private const val KEY_READ_RECEIPTS = "privacy_read_receipts"
    private const val KEY_PROFILE_PHOTO = "privacy_profile_photo"
    private const val KEY_NAME = "profile_name"
    private const val KEY_ABOUT = "profile_about"
    private const val KEY_IMAGE = "profile_image"
  }
}
