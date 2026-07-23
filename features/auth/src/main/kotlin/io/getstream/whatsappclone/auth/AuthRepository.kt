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

package io.getstream.whatsappclone.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.chat.android.client.ChatClient
import io.getstream.log.streamLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

@Singleton
class AuthRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val streamSessionManager: StreamSessionManager,
  private val streamTokenClient: StreamTokenClient
) {

  private val chatClient: ChatClient
    get() = ChatClient.instance()

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val loggedInFlag = MutableStateFlow(readLoggedInFlag())

  val isLoggedIn: Flow<Boolean> = combine(
    loggedInFlag.asStateFlow(),
    chatClient.clientState.user.map { it != null }
  ) { flag, hasChatUser ->
    flag || hasChatUser
  }

  fun isSessionActive(): Boolean = readLoggedInFlag()

  suspend fun signInDemo(): Result<Unit> = runCatching {
    streamSessionManager.connectDemoUser(chatClient)
    setLoggedIn(true)
  }

  suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    Firebase.auth.signInWithCredential(credential).awaitTask()
    upsertAuthUserProfile()
    connectStreamForCurrentFirebaseUser()
    setLoggedIn(true)
  }

  suspend fun restoreFirebaseSession(): Result<Unit> = runCatching {
    val user = Firebase.auth.currentUser
      ?: error("No Firebase session. Please sign in again.")
    upsertAuthUserProfile()
    connectStreamForCurrentFirebaseUser(preferredName = getCachedUsername() ?: user.displayName)
    setLoggedIn(true)
  }

  fun hasUsername(): Boolean {
    val cached = getCachedUsername()
    if (!cached.isNullOrBlank()) return true
    val uid = Firebase.auth.currentUser?.uid ?: return false
    return !prefs.getString(usernameKey(uid), null).isNullOrBlank()
  }

  fun getCachedUsername(): String? {
    val uid = Firebase.auth.currentUser?.uid
    if (uid != null) {
      prefs.getString(usernameKey(uid), null)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
  }

  fun hasPromptedPermissions(): Boolean = prefs.getBoolean(KEY_PERMISSIONS_PROMPTED, false)

  fun markPermissionsPrompted() {
    prefs.edit().putBoolean(KEY_PERMISSIONS_PROMPTED, true).apply()
  }

  suspend fun saveUsername(rawUsername: String): Result<Unit> {
    return try {
      val username = normalizeUsername(rawUsername)
      require(USERNAME_REGEX.matches(username)) {
        "Username must be 3–20 characters: letters, numbers, underscore"
      }

      val uid = Firebase.auth.currentUser?.uid
        ?: chatClient.getCurrentUser()?.id
        ?: error("Not signed in")

      // Firestore uniqueness is best-effort. App must work before the
      // (default) database exists / billing is enabled.
      try {
        val usernameRef = Firebase.firestore.collection(USERNAMES_COLLECTION).document(username)
        val userRef = Firebase.firestore.collection(USERS_COLLECTION).document(uid)

        Firebase.firestore.runTransaction { transaction ->
          val existing = transaction.get(usernameRef)
          if (existing.exists()) {
            val owner = existing.getString("uid")
            if (owner != null && owner != uid) {
              error("Username is already taken")
            }
          }
          transaction.set(
            usernameRef,
            mapOf(
              "uid" to uid,
              "username" to username,
              "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
          )
          transaction.set(
            userRef,
            mapOf(
              "uid" to uid,
              "username" to username,
              "name" to username,
              "email" to (Firebase.auth.currentUser?.email?.lowercase().orEmpty()),
              "image" to (Firebase.auth.currentUser?.photoUrl?.toString().orEmpty()),
              "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
          )
          null
        }.awaitTask()
      } catch (error: Throwable) {
        val message = error.message.orEmpty()
        if (message.contains("already taken", ignoreCase = true)) {
          throw error
        }
        streamLog { "Firestore username save skipped: $message" }
      }

      try {
        val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
          .setDisplayName(username)
          .build()
        Firebase.auth.currentUser?.updateProfile(profile)?.awaitTask()
      } catch (error: Throwable) {
        streamLog { "Firebase displayName update skipped: ${error.message}" }
      }

      prefs.edit()
        .putString(KEY_USERNAME, username)
        .putString(usernameKey(uid), username)
        .putString(KEY_PROFILE_NAME, username)
        .apply()

      context.getSharedPreferences("batchit_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("profile_name", username)
        .apply()

      connectStreamForCurrentFirebaseUser(preferredName = username)
      Result.success(Unit)
    } catch (error: Throwable) {
      Result.failure(error)
    }
  }

  /** Kept for settings screens that still call profile save. */
  suspend fun saveProfile(name: String, about: String = DEFAULT_ABOUT): Result<Unit> {
    return try {
      val uid = Firebase.auth.currentUser?.uid
        ?: chatClient.getCurrentUser()?.id
        ?: DEMO_PROFILE_ID

      try {
        val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
          .setDisplayName(name)
          .build()
        Firebase.auth.currentUser?.updateProfile(profile)?.awaitTask()
      } catch (error: Throwable) {
        streamLog { "Firebase displayName update skipped: ${error.message}" }
      }

      try {
        Firebase.firestore.collection(USERS_COLLECTION)
          .document(uid)
          .set(
            mapOf(
              "uid" to uid,
              "name" to name,
              "about" to about,
              "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
          )
          .awaitTask()
      } catch (error: Throwable) {
        streamLog { "Firestore profile save skipped: ${error.message}" }
      }

      prefs.edit()
        .putString(KEY_PROFILE_NAME, name)
        .putString(KEY_PROFILE_ABOUT, about)
        .apply()

      context.getSharedPreferences("batchit_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("profile_name", name)
        .putString("profile_about", about)
        .apply()

      Result.success(Unit)
    } catch (error: Throwable) {
      Result.failure(error)
    }
  }

  fun getCachedProfileName(): String =
    getCachedUsername()
      ?: prefs.getString(KEY_PROFILE_NAME, null)
      ?: Firebase.auth.currentUser?.displayName
      ?: chatClient.getCurrentUser()?.name
      ?: "BatchIt User"

  fun getCachedProfileAbout(): String =
    prefs.getString(KEY_PROFILE_ABOUT, DEFAULT_ABOUT) ?: DEFAULT_ABOUT

  suspend fun signOut(): Result<Unit> = runCatching {
    streamSessionManager.disconnect(chatClient)
    try {
      Firebase.auth.signOut()
    } catch (error: Throwable) {
      streamLog { "Firebase signOut skipped: ${error.message}" }
    }
    setLoggedIn(false)
  }

  suspend fun deleteAccount(): Result<Unit> = runCatching {
    val user = Firebase.auth.currentUser
    val uid = user?.uid

    if (uid != null) {
      try {
        Firebase.firestore.collection(USERS_COLLECTION)
          .document(uid)
          .delete()
          .awaitTask()
      } catch (error: Throwable) {
        streamLog { "Firestore user delete skipped: ${error.message}" }
      }
    }

    try {
      user?.delete()?.awaitTask()
    } catch (error: Throwable) {
      streamLog { "Firebase user delete failed: ${error.message}" }
      throw error
    }

    streamSessionManager.disconnect(chatClient)
    try {
      Firebase.auth.signOut()
    } catch (error: Throwable) {
      streamLog { "Firebase signOut after delete skipped: ${error.message}" }
    }
    setLoggedIn(false)
  }

  private suspend fun upsertAuthUserProfile() {
    val user = Firebase.auth.currentUser ?: return
    try {
      Firebase.firestore.collection(USERS_COLLECTION)
        .document(user.uid)
        .set(
          mapOf(
            "uid" to user.uid,
            "email" to user.email?.lowercase().orEmpty(),
            "image" to user.photoUrl?.toString().orEmpty(),
            "name" to (
              getCachedUsername()
                ?: user.displayName
                ?: user.email?.substringBefore("@")
                ?: "BatchIt User"
              ),
            "updatedAt" to FieldValue.serverTimestamp()
          ),
          SetOptions.merge()
        )
        .awaitTask()
    } catch (error: Throwable) {
      streamLog { "Firestore auth profile upsert skipped: ${error.message}" }
    }
  }

  private suspend fun connectStreamForCurrentFirebaseUser(preferredName: String? = null) {
    val firebaseUser = Firebase.auth.currentUser
      ?: error("Firebase user missing")
    val idToken = firebaseUser.getIdToken(false).awaitTask().token
      ?: error("Could not get Firebase ID token")

    val name = preferredName
      ?: getCachedUsername()
      ?: firebaseUser.displayName
      ?: prefs.getString(KEY_PROFILE_NAME, null)
      ?: firebaseUser.email?.substringBefore("@")
      ?: "BatchIt User"
    val image = firebaseUser.photoUrl?.toString().orEmpty()

    val payload = streamTokenClient.mintToken(
      firebaseIdToken = idToken,
      name = name,
      image = image
    )

    streamSessionManager.connectWithFirebaseToken(
      chatClient = chatClient,
      userId = payload.userId.ifBlank { firebaseUser.uid },
      name = payload.name,
      image = payload.image,
      token = payload.token,
      tokenRefresher = {
        runBlocking {
          val fresh = Firebase.auth.currentUser?.getIdToken(true)?.awaitTask()?.token
            ?: error("Missing Firebase ID token for refresh")
          streamTokenClient.mintToken(fresh, name, image).token
        }
      }
    )
  }

  private fun readLoggedInFlag(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

  private fun setLoggedIn(value: Boolean) {
    prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()
    loggedInFlag.value = value
  }

  private fun usernameKey(uid: String) = "${KEY_USERNAME}_$uid"

  companion object {
    private const val PREFS_NAME = "batchit_auth"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_PROFILE_NAME = "profile_name"
    private const val KEY_PROFILE_ABOUT = "profile_about"
    private const val KEY_USERNAME = "username"
    private const val KEY_PERMISSIONS_PROMPTED = "permissions_prompted"
    private const val USERS_COLLECTION = "users"
    private const val USERNAMES_COLLECTION = "usernames"
    private const val DEMO_PROFILE_ID = "batchit_demo"
    private const val DEFAULT_ABOUT = "Hey there! I am using BatchIt."
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,20}$")

    fun normalizeUsername(raw: String): String = raw.trim().lowercase()
  }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { continuation ->
  addOnSuccessListener { continuation.resume(it) }
  addOnFailureListener { continuation.resumeWithException(it) }
}
