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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.EntryPointAccessors
import io.getstream.whatsappclone.auth.AuthUiState
import io.getstream.whatsappclone.auth.AuthViewModel
import io.getstream.whatsappclone.auth.ui.AuthFlow
import io.getstream.whatsappclone.designsystem.component.WhatsAppCloneBackground
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingIndicator
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppNavHost
import io.getstream.whatsappclone.navigation.WhatsAppScreens
import io.getstream.whatsappclone.notifications.NotificationDeepLink
import io.getstream.whatsappclone.notifications.NotificationEntryPoint
import io.getstream.whatsappclone.settings.SettingsViewModel
import io.getstream.whatsappclone.update.AppUpdateHost
import io.getstream.whatsappclone.video.IncomingCallOverlay

@Composable
fun WhatsAppCloneMain(
  composeNavigator: AppComposeNavigator,
  authViewModel: AuthViewModel = hiltViewModel(),
  settingsViewModel: SettingsViewModel = hiltViewModel()
) {
  val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
  val darkTheme = themeMode.resolveIsDark(isSystemInDarkTheme())
  val appContext = LocalContext.current.applicationContext
  val notificationEntry = remember(appContext) {
    EntryPointAccessors.fromApplication(appContext, NotificationEntryPoint::class.java)
  }
  val notificationCoordinator = remember(notificationEntry) {
    notificationEntry.notificationCoordinator()
  }
  val deepLinkBus = remember(notificationEntry) { notificationEntry.deepLinkBus() }

  WhatsAppCloneComposeTheme(darkTheme = darkTheme) {
    SyncSystemBars(darkTheme = darkTheme)

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    // Keep NavHost alive across recompositions once the user is in; avoid AnimatedContent teardown.
    var sessionReady by remember { mutableStateOf(false) }
    LaunchedEffect(authState) {
      when (authState) {
        is AuthUiState.Authenticated -> sessionReady = true
        is AuthUiState.GoogleSignIn,
        is AuthUiState.Error -> sessionReady = false
        else -> Unit
      }
    }

    DisposableEffect(sessionReady) {
      if (sessionReady) {
        notificationCoordinator.start()
      } else {
        notificationCoordinator.stop()
      }
      onDispose { notificationCoordinator.stop() }
    }

    WhatsAppCloneBackground {
      when {
        authState is AuthUiState.Loading && !sessionReady -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WhatsAppLoadingIndicator()
          }
        }

        sessionReady -> {
          val navHostController = rememberNavController()
          LaunchedEffect(navHostController) {
            composeNavigator.handleNavigationCommands(navHostController)
          }

          val backStackEntry by navHostController.currentBackStackEntryAsState()
          LaunchedEffect(backStackEntry) {
            val route = backStackEntry?.destination?.route.orEmpty()
            val channelId = backStackEntry?.arguments?.getString("channelId")
            val activeCid = when {
              route.startsWith("messages") && !channelId.isNullOrBlank() -> channelId
              else -> null
            }
            notificationCoordinator.setActiveChannelCid(activeCid)
          }

          LaunchedEffect(deepLinkBus, sessionReady) {
            deepLinkBus.events.collect { link ->
              when (link) {
                is NotificationDeepLink.OpenChat -> {
                  composeNavigator.navigate(WhatsAppScreens.Messages.createRoute(link.channelCid))
                }
                is NotificationDeepLink.OpenVideoCall -> {
                  composeNavigator.navigate(
                    WhatsAppScreens.VideoCall.createRoute(
                      callId = link.callId,
                      videoCall = link.video
                    )
                  )
                }
                NotificationDeepLink.OpenCallsTab -> {
                  composeNavigator.navigate(WhatsAppScreens.Home.name)
                }
              }
            }
          }

          Box(modifier = Modifier.fillMaxSize()) {
            WhatsAppNavHost(
              navHostController = navHostController,
              composeNavigator = composeNavigator
            )
            IncomingCallOverlay(
              onCallConnected = { callId, video ->
                composeNavigator.navigate(
                  WhatsAppScreens.VideoCall.createRoute(
                    callId = callId,
                    videoCall = video
                  )
                )
              }
            )
            AppUpdateHost(enabled = true)
          }
        }

        else -> {
          AuthFlow(
            onAuthenticated = {},
            authViewModel = authViewModel
          )
        }
      }
    }
  }
}
