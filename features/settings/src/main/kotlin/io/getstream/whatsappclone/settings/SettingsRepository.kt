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
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.log.streamLog
import io.getstream.whatsappclone.designsystem.theme.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine

data class PrivacySettings(
  val lastSeenVisible: Boolean = true,
  val readReceiptsEnabled: Boolean = true,
  val profilePhotoVisible: Boolean = true
)

data class NotificationSettings(
  val messageNotifications: Boolean = true,
  val callNotifications: Boolean = true,
  val notificationPreview: Boolean = true
)

data class StorageSettings(
  val autoDownloadWifi: Boolean = true,
  val autoDownloadCellular: Boolean = false,
  val mediaQualityHigh: Boolean = true
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

  private val _notificationSettings = MutableStateFlow(readNotificationSettings())
  val notificationSettings: Flow<NotificationSettings> = _notificationSettings.asStateFlow()

  private val _storageSettings = MutableStateFlow(readStorageSettings())
  val storageSettings: Flow<StorageSettings> = _storageSettings.asStateFlow()

  private val _userProfile = MutableStateFlow(readUserProfile())
  val userProfile: Flow<UserProfile> = _userProfile.asStateFlow()

  private val _themeMode = MutableStateFlow(readThemeMode())
  val themeMode: Flow<ThemeMode> = _themeMode.asStateFlow()

  private val _blockedUserIds = MutableStateFlow(readBlockedUserIds())
  val blockedUserIds: Flow<Set<String>> = _blockedUserIds.asStateFlow()

  init {
    syncProfileFromFirebaseAuth()
    syncPrivacyFromFirestore()
  }

  fun setLastSeenVisible(visible: Boolean) {
    prefs.edit().putBoolean(KEY_LAST_SEEN, visible).apply()
    _privacySettings.update { it.copy(lastSeenVisible = visible) }
    syncPrivacyToFirestore(_privacySettings.value)
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_READ_RECEIPTS, enabled).apply()
    _privacySettings.update { it.copy(readReceiptsEnabled = enabled) }
    syncPrivacyToFirestore(_privacySettings.value)
  }

  fun setProfilePhotoVisible(visible: Boolean) {
    prefs.edit().putBoolean(KEY_PROFILE_PHOTO, visible).apply()
    _privacySettings.update { it.copy(profilePhotoVisible = visible) }
    syncPrivacyToFirestore(_privacySettings.value)
  }

  fun currentNotificationSettings(): NotificationSettings = _notificationSettings.value

  fun setMessageNotifications(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_MSG_NOTIFICATIONS, enabled).apply()
    _notificationSettings.update { it.copy(messageNotifications = enabled) }
  }

  fun setCallNotifications(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_CALL_NOTIFICATIONS, enabled).apply()
    _notificationSettings.update { it.copy(callNotifications = enabled) }
  }

  fun setNotificationPreview(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_NOTIFICATION_PREVIEW, enabled).apply()
    _notificationSettings.update { it.copy(notificationPreview = enabled) }
  }

  fun setAutoDownloadWifi(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_WIFI, enabled).apply()
    _storageSettings.update { it.copy(autoDownloadWifi = enabled) }
  }

  fun setAutoDownloadCellular(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_CELLULAR, enabled).apply()
    _storageSettings.update { it.copy(autoDownloadCellular = enabled) }
  }

  fun setMediaQualityHigh(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_MEDIA_QUALITY_HIGH, enabled).apply()
    _storageSettings.update { it.copy(mediaQualityHigh = enabled) }
  }

  fun setThemeMode(mode: ThemeMode) {
    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    _themeMode.value = mode
  }

  fun updateProfile(name: String, about: String, imageUrl: String = _userProfile.value.imageUrl) {
    prefs.edit()
      .putString(KEY_NAME, name)
      .putString(KEY_ABOUT, about)
      .putString(KEY_IMAGE, imageUrl)
      .apply()
    _userProfile.value = UserProfile(name = name, about = about, imageUrl = imageUrl)

    try {
      val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
        .setDisplayName(name)
        .setPhotoUri(if (imageUrl.isNotBlank()) Uri.parse(imageUrl) else null)
        .build()
      Firebase.auth.currentUser?.updateProfile(profile)
    } catch (error: Throwable) {
      streamLog { "Firebase displayName update skipped: ${error.message}" }
    }

    try {
      Firebase.firestore.collection(USERS).document(Firebase.auth.currentUser?.uid.orEmpty())
        .set(
          mapOf(
            "name" to name,
            "image" to imageUrl
          ),
          SetOptions.merge()
        )
    } catch (error: Throwable) {
      streamLog { "Firestore profile image write skipped: ${error.message}" }
    }
  }

  /**
   * Uploads the picked profile photo to Firebase Storage under
   * `profile_images/<uid>.jpg` and persists the resulting URL via [updateProfile].
   * Returns the download URL on success, or null on failure.
   */
  suspend fun uploadProfileImage(uri: Uri): String? {
    val user = Firebase.auth.currentUser ?: run {
      streamLog { "uploadProfileImage: no Firebase user" }
      return null
    }
    return try {
      val ref = Firebase.storage.reference
        .child("profile_images/${user.uid}.jpg")
      ref.putFile(uri).awaitTask()
      val downloadUrl = ref.downloadUrl.awaitTask().toString()
      updateProfile(
        name = _userProfile.value.name,
        about = _userProfile.value.about,
        imageUrl = downloadUrl
      )
      downloadUrl
    } catch (error: Throwable) {
      streamLog { "uploadProfileImage failed: ${error.message}" }
      null
    }
  }

  fun syncProfileFromFirebaseAuth() {
    val user = Firebase.auth.currentUser ?: return
    val current = _userProfile.value
    val name = user.displayName?.takeIf { it.isNotBlank() }
      ?: prefs.getString(KEY_NAME, null)
      ?: current.name
    val imageUrl = user.photoUrl?.toString()?.takeIf { it.isNotBlank() }
      ?: prefs.getString(KEY_IMAGE, null)
      ?: current.imageUrl
    val about = prefs.getString(KEY_ABOUT, null) ?: current.about

    if (name != current.name || imageUrl != current.imageUrl || about != current.about) {
      prefs.edit()
        .putString(KEY_NAME, name)
        .putString(KEY_ABOUT, about)
        .putString(KEY_IMAGE, imageUrl)
        .apply()
      _userProfile.value = UserProfile(name = name, about = about, imageUrl = imageUrl)
    }
  }

  fun syncPrivacyFromFirestore() {
    val uid = Firebase.auth.currentUser?.uid ?: return
    try {
      Firebase.firestore.collection(USERS).document(uid).get()
        .addOnSuccessListener { doc ->
          @Suppress("UNCHECKED_CAST")
          val privacy = doc.get("privacy") as? Map<String, Any?> ?: return@addOnSuccessListener
          val lastSeen = privacy[PRIVACY_LAST_SEEN] as? Boolean
            ?: prefs.getBoolean(KEY_LAST_SEEN, true)
          val readReceipts = privacy[PRIVACY_READ_RECEIPTS] as? Boolean
            ?: prefs.getBoolean(KEY_READ_RECEIPTS, true)
          val profilePhoto = privacy[PRIVACY_PROFILE_PHOTO] as? Boolean
            ?: prefs.getBoolean(KEY_PROFILE_PHOTO, true)
          prefs.edit()
            .putBoolean(KEY_LAST_SEEN, lastSeen)
            .putBoolean(KEY_READ_RECEIPTS, readReceipts)
            .putBoolean(KEY_PROFILE_PHOTO, profilePhoto)
            .apply()
          _privacySettings.value = PrivacySettings(
            lastSeenVisible = lastSeen,
            readReceiptsEnabled = readReceipts,
            profilePhotoVisible = profilePhoto
          )
        }
        .addOnFailureListener { error ->
          streamLog { "Privacy Firestore read skipped: ${error.message}" }
        }
    } catch (error: Throwable) {
      streamLog { "Privacy Firestore read skipped: ${error.message}" }
    }
  }

  private fun syncPrivacyToFirestore(settings: PrivacySettings) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    try {
      Firebase.firestore.collection(USERS).document(uid)
        .set(
          mapOf(
            "privacy" to mapOf(
              PRIVACY_LAST_SEEN to settings.lastSeenVisible,
              PRIVACY_READ_RECEIPTS to settings.readReceiptsEnabled,
              PRIVACY_PROFILE_PHOTO to settings.profilePhotoVisible
            )
          ),
          SetOptions.merge()
        )
    } catch (error: Throwable) {
      streamLog { "Privacy Firestore write skipped: ${error.message}" }
    }
  }

  fun blockUser(userId: String) {
    if (userId.isBlank()) return
    val updated = _blockedUserIds.value + userId
    prefs.edit().putStringSet(KEY_BLOCKED_USERS, updated).apply()
    _blockedUserIds.value = updated
  }

  fun unblockUser(userId: String) {
    val updated = _blockedUserIds.value - userId
    prefs.edit().putStringSet(KEY_BLOCKED_USERS, updated).apply()
    _blockedUserIds.value = updated
  }

  private fun readPrivacySettings(): PrivacySettings = PrivacySettings(
    lastSeenVisible = prefs.getBoolean(KEY_LAST_SEEN, true),
    readReceiptsEnabled = prefs.getBoolean(KEY_READ_RECEIPTS, true),
    profilePhotoVisible = prefs.getBoolean(KEY_PROFILE_PHOTO, true)
  )

  private fun readNotificationSettings(): NotificationSettings = NotificationSettings(
    messageNotifications = prefs.getBoolean(KEY_MSG_NOTIFICATIONS, true),
    callNotifications = prefs.getBoolean(KEY_CALL_NOTIFICATIONS, true),
    notificationPreview = prefs.getBoolean(KEY_NOTIFICATION_PREVIEW, true)
  )

  private fun readStorageSettings(): StorageSettings = StorageSettings(
    autoDownloadWifi = prefs.getBoolean(KEY_AUTO_DOWNLOAD_WIFI, true),
    autoDownloadCellular = prefs.getBoolean(KEY_AUTO_DOWNLOAD_CELLULAR, false),
    mediaQualityHigh = prefs.getBoolean(KEY_MEDIA_QUALITY_HIGH, true)
  )

  private fun readUserProfile(): UserProfile = UserProfile(
    name = prefs.getString(KEY_NAME, null) ?: "BatchIt User",
    about = prefs.getString(KEY_ABOUT, null) ?: "Hey there! I am using BatchIt.",
    imageUrl = prefs.getString(KEY_IMAGE, null) ?: "https://placekitten.com/200/300"
  )

  private fun readBlockedUserIds(): Set<String> =
    prefs.getStringSet(KEY_BLOCKED_USERS, emptySet())?.toSet().orEmpty()

  private fun readThemeMode(): ThemeMode =
    ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))

  companion object {
    const val PREFS_NAME = "batchit_settings"
    private const val USERS = "users"
    private const val PRIVACY_LAST_SEEN = "lastSeenVisible"
    private const val PRIVACY_READ_RECEIPTS = "readReceiptsEnabled"
    private const val PRIVACY_PROFILE_PHOTO = "profilePhotoVisible"
    private const val KEY_LAST_SEEN = "privacy_last_seen"
    private const val KEY_READ_RECEIPTS = "privacy_read_receipts"
    private const val KEY_PROFILE_PHOTO = "privacy_profile_photo"
    const val KEY_MSG_NOTIFICATIONS = "notifications_messages"
    const val KEY_CALL_NOTIFICATIONS = "notifications_calls"
    const val KEY_NOTIFICATION_PREVIEW = "notifications_preview"
    private const val KEY_AUTO_DOWNLOAD_WIFI = "storage_auto_wifi"
    private const val KEY_AUTO_DOWNLOAD_CELLULAR = "storage_auto_cellular"
    private const val KEY_MEDIA_QUALITY_HIGH = "storage_media_quality_high"
    private const val KEY_BLOCKED_USERS = "blocked_user_ids"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_NAME = "profile_name"
    private const val KEY_ABOUT = "profile_about"
    private const val KEY_IMAGE = "profile_image"
  }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
  addOnSuccessListener { result -> cont.resume(result) }
  addOnFailureListener { error -> cont.resumeWithException(error) }
}
