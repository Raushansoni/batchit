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

package io.getstream.whatsappclone.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.getstream.whatsappclone.auth.AuthUiState
import io.getstream.whatsappclone.auth.AuthViewModel
import io.getstream.whatsappclone.auth.R
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingIndicator
import io.getstream.whatsappclone.designsystem.theme.GREEN500
import io.getstream.whatsappclone.designsystem.theme.getTitleColor

@Composable
fun AuthFlow(
  onAuthenticated: () -> Unit,
  authViewModel: AuthViewModel = hiltViewModel()
) {
  val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
  val isSubmitting by authViewModel.isSubmitting.collectAsStateWithLifecycle()

  when (val state = uiState) {
    AuthUiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WhatsAppLoadingIndicator()
      }
    }

    AuthUiState.PhoneInput -> {
      PhoneAuthScreen(
        isSubmitting = isSubmitting,
        onContinue = authViewModel::continueWithPhone,
        onDemoContinue = authViewModel::continueAsDemo
      )
    }

    is AuthUiState.OtpInput -> {
      OtpVerifyScreen(
        phone = state.phone,
        isSubmitting = isSubmitting,
        onVerify = authViewModel::verifyOtp
      )
    }

    AuthUiState.ProfileSetup -> {
      ProfileSetupScreen(
        isSubmitting = isSubmitting,
        onSave = authViewModel::saveProfile
      )
    }

    AuthUiState.Authenticated -> {
      onAuthenticated()
    }

    is AuthUiState.Error -> {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = state.message.ifBlank { stringResource(id = R.string.auth_error_generic) },
          style = MaterialTheme.typography.bodyLarge,
          color = getTitleColor()
        )
        TextButton(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 32.dp),
          onClick = authViewModel::clearError
        ) {
          Text(text = stringResource(id = R.string.auth_continue), color = GREEN500)
        }
      }
    }
  }
}
