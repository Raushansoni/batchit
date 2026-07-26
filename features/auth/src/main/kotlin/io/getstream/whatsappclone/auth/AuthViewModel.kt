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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthUiState {
  data object Loading : AuthUiState
  data object GoogleSignIn : AuthUiState
  data object UsernameSetup : AuthUiState
  data object PermissionsSetup : AuthUiState
  data object Authenticated : AuthUiState
  data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
  private val authRepository: AuthRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

  init {
    viewModelScope.launch {
      if (!authRepository.isSessionActive() && Firebase.auth.currentUser == null) {
        _uiState.value = AuthUiState.GoogleSignIn
        return@launch
      }

      if (BatchItAuthConfig.USE_DEMO_AUTH) {
        authRepository.signInDemo()
          .onSuccess { _uiState.value = nextOnboardingState() }
          .onFailure { error ->
            authRepository.signOut()
            _uiState.value = AuthUiState.Error(
              error.message
                ?: "Could not restore session. Check your Stream API key in secrets.properties."
            )
          }
        return@launch
      }

      // Must finish ChatClient.connectUser before Authenticated — ChannelListViewModel
      // crashes if the shell opens earlier. Cached JWT still speeds restore.
      authRepository.restoreFirebaseSession()
        .onSuccess { _uiState.value = nextOnboardingState() }
        .onFailure { error ->
          authRepository.signOut()
          _uiState.value = AuthUiState.Error(
            error.message
              ?: "Could not restore session. Deploy the token Worker and check STREAM_TOKEN_URL."
          )
        }
    }

    viewModelScope.launch {
      authRepository.isLoggedIn.collect { loggedIn ->
        if (!loggedIn && _uiState.value is AuthUiState.Authenticated) {
          _uiState.value = AuthUiState.GoogleSignIn
        }
      }
    }
  }

  fun onGoogleIdToken(idToken: String) {
    viewModelScope.launch {
      _isSubmitting.value = true
      val result = authRepository.signInWithGoogleIdToken(idToken)
      _isSubmitting.value = false
      result
        .onSuccess { _uiState.value = nextOnboardingState() }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(error.message ?: "Google sign-in failed")
        }
    }
  }

  fun onGoogleSignInCancelled() {
    _isSubmitting.value = false
  }

  fun onGoogleSignInError(message: String) {
    _isSubmitting.value = false
    _uiState.value = AuthUiState.Error(message)
  }

  fun setSubmitting(value: Boolean) {
    _isSubmitting.value = value
  }

  fun continueAsDemo() {
    viewModelScope.launch {
      _isSubmitting.value = true
      val result = authRepository.signInDemo()
      _isSubmitting.value = false
      result
        .onSuccess { _uiState.value = nextOnboardingState() }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(error.message ?: "Demo sign-in failed")
        }
    }
  }

  fun saveUsername(username: String) {
    viewModelScope.launch {
      _isSubmitting.value = true
      val result = authRepository.saveUsername(username)
      _isSubmitting.value = false
      result
        .onSuccess { _uiState.value = nextOnboardingState() }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(error.message ?: "Failed to save username")
        }
    }
  }

  fun onPermissionsFinished() {
    authRepository.markPermissionsPrompted()
    _uiState.value = AuthUiState.Authenticated
  }

  fun clearError() {
    _uiState.update { current ->
      if (current is AuthUiState.Error) AuthUiState.GoogleSignIn else current
    }
  }

  fun signOut() {
    viewModelScope.launch {
      authRepository.signOut()
      _uiState.value = AuthUiState.GoogleSignIn
    }
  }

  fun deleteAccount() {
    viewModelScope.launch {
      authRepository.deleteAccount()
        .onSuccess { _uiState.value = AuthUiState.GoogleSignIn }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(
            error.message ?: "Failed to delete account"
          )
        }
    }
  }

  private fun nextOnboardingState(): AuthUiState {
    if (!BatchItAuthConfig.USE_DEMO_AUTH && !authRepository.hasUsername()) {
      return AuthUiState.UsernameSetup
    }
    if (!authRepository.hasPromptedPermissions()) {
      return AuthUiState.PermissionsSetup
    }
    return AuthUiState.Authenticated
  }
}
