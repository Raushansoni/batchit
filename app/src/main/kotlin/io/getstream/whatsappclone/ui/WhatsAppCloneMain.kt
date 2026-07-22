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

package io.getstream.whatsappclone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import io.getstream.whatsappclone.auth.AuthUiState
import io.getstream.whatsappclone.auth.AuthViewModel
import io.getstream.whatsappclone.auth.ui.AuthFlow
import io.getstream.whatsappclone.designsystem.component.WhatsAppCloneBackground
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppNavHost

@Composable
fun WhatsAppCloneMain(
  composeNavigator: AppComposeNavigator,
  authViewModel: AuthViewModel = hiltViewModel()
) {
  WhatsAppCloneComposeTheme {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    var isAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
      if (authState is AuthUiState.Authenticated) {
        isAuthenticated = true
      }
      if (authState is AuthUiState.PhoneInput) {
        isAuthenticated = false
      }
    }

    WhatsAppCloneBackground {
      if (!isAuthenticated && authState !is AuthUiState.Authenticated) {
        AuthFlow(
          onAuthenticated = { isAuthenticated = true },
          authViewModel = authViewModel
        )
      } else {
        val navHostController = rememberNavController()

        LaunchedEffect(Unit) {
          composeNavigator.handleNavigationCommands(navHostController)
        }

        WhatsAppNavHost(
          navHostController = navHostController,
          composeNavigator = composeNavigator
        )
      }
    }
  }
}
