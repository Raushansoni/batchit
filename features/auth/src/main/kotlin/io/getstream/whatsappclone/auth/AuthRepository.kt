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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.chat.android.client.ChatClient
import io.getstream.log.streamLog
import java.util.concurrent.TimeUnit
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

@Singleton
class AuthRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val streamSessionManager: StreamSessionManager
) {

  private val chatClient: ChatClient
    get() = ChatClient.instance()

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val loggedInFlag = MutableStateFlow(readLoggedInFlag())

  private var verificationId: String? = null
  private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

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

  suspend fun sendOtp(phone: String, activity: Activity): Result<Unit> {
    if (BatchItAuthConfig.USE_DEMO_AUTH) {
      return signInDemo()
    }

    return try {
      suspendCoroutine { continuation ->
        var resumed = false
        fun resumeOnce(block: () -> Unit) {
          if (!resumed) {
            resumed = true
            block()
          }
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
          override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            resumeOnce { continuation.resume(Unit) }
          }

          override fun onVerificationFailed(exception: FirebaseException) {
            resumeOnce { continuation.resumeWithException(exception) }
          }

          override fun onCodeSent(
            id: String,
            token: PhoneAuthProvider.ForceResendingToken
          ) {
            verificationId = id
            resendToken = token
            resumeOnce { continuation.resume(Unit) }
          }
        }

        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
          .setPhoneNumber(phone)
          .setTimeout(60L, TimeUnit.SECONDS)
          .setActivity(activity)
          .setCallbacks(callbacks)
          .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
      }
      Result.success(Unit)
    } catch (error: Throwable) {
      streamLog { "sendOtp failed, falling back to demo: ${error.message}" }
      signInDemo()
    }
  }

  suspend fun verifyOtp(code: String): Result<Unit> {
    if (BatchItAuthConfig.USE_DEMO_AUTH) {
      return signInDemo()
    }

    return try {
      val id = verificationId ?: error("Missing verification id")
      val credential = PhoneAuthProvider.getCredential(id, code)
      Firebase.auth.signInWithCredential(credential).awaitTask()

      val firebaseUser = Firebase.auth.currentUser ?: error("Firebase user missing after OTP")
      val tokenPayload = createStreamUserAndGetToken(
        uid = firebaseUser.uid,
        name = firebaseUser.displayName ?: "BatchIt User",
        image = firebaseUser.photoUrl?.toString().orEmpty()
      )

      streamSessionManager.connectWithFirebaseToken(
        chatClient = chatClient,
        userId = firebaseUser.uid,
        name = tokenPayload.name,
        image = tokenPayload.image,
        token = tokenPayload.token
      )
      setLoggedIn(true)
      Result.success(Unit)
    } catch (error: Throwable) {
      streamLog { "verifyOtp failed, falling back to demo: ${error.message}" }
      signInDemo()
    }
  }

  suspend fun saveProfile(name: String, about: String = DEFAULT_ABOUT): Result<Unit> {
    return try {
      val uid = Firebase.auth.currentUser?.uid
        ?: chatClient.getCurrentUser()?.id
        ?: DEMO_PROFILE_ID

      try {
        Firebase.firestore.collection(USERS_COLLECTION)
          .document(uid)
          .set(
            mapOf(
              "uid" to uid,
              "name" to name,
              "about" to about,
              "updatedAt" to System.currentTimeMillis()
            )
          )
          .awaitTask()
      } catch (error: Throwable) {
        streamLog { "Firestore profile save skipped: ${error.message}" }
      }

      prefs.edit()
        .putString(KEY_PROFILE_NAME, name)
        .putString(KEY_PROFILE_ABOUT, about)
        .apply()

      // Keep settings profile in sync
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
    prefs.getString(KEY_PROFILE_NAME, null)
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

  private suspend fun createStreamUserAndGetToken(
    uid: String,
    name: String,
    image: String
  ): StreamTokenPayload {
    val result = Firebase.functions
      .getHttpsCallable(FUNCTION_CREATE_STREAM_USER)
      .call(
        mapOf(
          "uid" to uid,
          "name" to name,
          "image" to image
        )
      )
      .awaitTask()

    val data = result.getData() as? Map<*, *> ?: error("Invalid token response")
    val token = data["token"] as? String ?: error("Missing Stream token")
    return StreamTokenPayload(
      token = token,
      name = data["name"] as? String ?: name,
      image = data["image"] as? String ?: image
    )
  }

  private fun readLoggedInFlag(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

  private fun setLoggedIn(value: Boolean) {
    prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()
    loggedInFlag.value = value
  }

  private data class StreamTokenPayload(
    val token: String,
    val name: String,
    val image: String
  )

  companion object {
    private const val PREFS_NAME = "batchit_auth"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_PROFILE_NAME = "profile_name"
    private const val KEY_PROFILE_ABOUT = "profile_about"
    private const val USERS_COLLECTION = "users"
    private const val FUNCTION_CREATE_STREAM_USER = "createStreamUserAndGetToken"
    private const val DEMO_PROFILE_ID = "batchit_demo"
    private const val DEFAULT_ABOUT = "Hey there! I am using BatchIt."
  }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { continuation ->
  addOnSuccessListener { continuation.resume(it) }
  addOnFailureListener { continuation.resumeWithException(it) }
}
