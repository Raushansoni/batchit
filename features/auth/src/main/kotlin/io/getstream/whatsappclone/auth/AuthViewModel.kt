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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthUiState {
  data object Loading : AuthUiState
  data object PhoneInput : AuthUiState
  data class OtpInput(val phone: String) : AuthUiState
  data object ProfileSetup : AuthUiState
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
      if (authRepository.isSessionActive()) {
        if (BatchItAuthConfig.USE_DEMO_AUTH) {
          authRepository.signInDemo()
        }
        _uiState.value = AuthUiState.Authenticated
      } else {
        _uiState.value = AuthUiState.PhoneInput
      }
    }

    viewModelScope.launch {
      authRepository.isLoggedIn.collect { loggedIn ->
        if (!loggedIn && _uiState.value is AuthUiState.Authenticated) {
          _uiState.value = AuthUiState.PhoneInput
        }
      }
    }
  }

  fun continueWithPhone(phone: String, activity: Activity) {
    viewModelScope.launch {
      _isSubmitting.value = true
      val result = authRepository.sendOtp(phone.trim(), activity)
      _isSubmitting.value = false

      result
        .onSuccess {
          if (BatchItAuthConfig.USE_DEMO_AUTH) {
            _uiState.value = AuthUiState.ProfileSetup
          } else {
            _uiState.value = AuthUiState.OtpInput(phone.trim())
          }
        }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(error.message ?: "Failed to send OTP")
        }
    }
  }

  fun continueAsDemo() {
    viewModelScope.launch {
      _isSubmitting.value = true
      val result = authRepository.signInDemo()
      _isSubmitting.value = false

      result
        .onSuccess { _uiState.value = AuthUiState.ProfileSetup }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(error.message ?: "Demo sign-in failed")
        }
    }
  }

  fun verifyOtp(code: String) {
    viewModelScope.launch {
      _isSubmitting.value = true
      val result = authRepository.verifyOtp(code.trim())
      _isSubmitting.value = false

      result
        .onSuccess { _uiState.value = AuthUiState.ProfileSetup }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(error.message ?: "OTP verification failed")
        }
    }
  }

  fun saveProfile(name: String) {
    viewModelScope.launch {
      _isSubmitting.value = true
      val result = authRepository.saveProfile(name.trim())
      _isSubmitting.value = false

      result
        .onSuccess { _uiState.value = AuthUiState.Authenticated }
        .onFailure { error ->
          _uiState.value = AuthUiState.Error(error.message ?: "Failed to save profile")
        }
    }
  }

  fun clearError() {
    _uiState.update { current ->
      if (current is AuthUiState.Error) AuthUiState.PhoneInput else current
    }
  }

  fun signOut() {
    viewModelScope.launch {
      authRepository.signOut()
      _uiState.value = AuthUiState.PhoneInput
    }
  }
}
