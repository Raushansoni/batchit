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

package io.getstream.whatsappclone.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.getstream.whatsappclone.auth.AuthViewModel
import io.getstream.whatsappclone.calls.info.WhatsAppCallHistoryInfo
import io.getstream.whatsappclone.chats.friends.FriendsContactsScreen
import io.getstream.whatsappclone.chats.messages.WhatsAppMessages
import io.getstream.whatsappclone.chats.search.ChatSearchScreen
import io.getstream.whatsappclone.chats.starred.StarredMessagesScreen
import io.getstream.whatsappclone.model.WhatsAppUser
import io.getstream.whatsappclone.settings.ui.AccountProfileScreen
import io.getstream.whatsappclone.settings.ui.BatchItSettingsScreen
import io.getstream.whatsappclone.settings.ui.BlockedContactsScreen
import io.getstream.whatsappclone.settings.ui.HelpScreen
import io.getstream.whatsappclone.settings.ui.NotificationsSettingsScreen
import io.getstream.whatsappclone.settings.ui.PrivacySettingsScreen
import io.getstream.whatsappclone.settings.ui.StorageSettingsScreen
import io.getstream.whatsappclone.ui.WhatsAppTabPager
import io.getstream.whatsappclone.ui.WhatsAppTopBar
import io.getstream.whatsappclone.update.AppUpdateViewModel
import io.getstream.whatsappclone.video.WhatsAppVideoCall
import kotlinx.coroutines.launch

fun NavGraphBuilder.whatsAppHomeNavigation(
  composeNavigator: AppComposeNavigator
) {
  composable(route = WhatsAppScreens.Home.name) {
    Scaffold(
      topBar = {
        WhatsAppTopBar(
          onSettingsClick = {
            composeNavigator.navigate(WhatsAppScreens.Settings.name)
          },
          onSearchClick = {
            composeNavigator.navigate(WhatsAppScreens.ChatSearch.name)
          }
        )
      }
    ) { padding ->
      WhatsAppTabPager(
        modifier = Modifier.padding(padding)
      )
    }
  }

  composable(
    route = WhatsAppScreens.Messages.name,
    arguments = WhatsAppScreens.Messages.navArguments
  ) {
    val channelId = it.arguments?.getString("channelId") ?: return@composable
    WhatsAppMessages(
      channelId = channelId
    )
  }

  composable(
    route = WhatsAppScreens.CallInfo.name,
    arguments = WhatsAppScreens.CallInfo.navArguments
  ) {
    val whatsAppUser = it.arguments?.getParcelable<WhatsAppUser>(WhatsAppScreens.CallInfo.KEY_USER)
      ?: return@composable

    WhatsAppCallHistoryInfo(
      whatsAppUser = whatsAppUser
    )
  }

  composable(
    route = WhatsAppScreens.VideoCall.name,
    arguments = WhatsAppScreens.VideoCall.navArguments
  ) {
    val callId = it.arguments?.getString(WhatsAppScreens.VideoCall.KEY_CALL_ID) ?: return@composable
    val videoCall =
      it.arguments?.getBoolean(WhatsAppScreens.VideoCall.KEY_VIDEO_ID) ?: return@composable

    WhatsAppVideoCall(id = callId, videoCall = videoCall)
  }

  composable(route = WhatsAppScreens.Settings.name) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val activity = LocalContext.current as ComponentActivity
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel(activity)
    val scope = rememberCoroutineScope()
    BatchItSettingsScreen(
      onAccountClick = {
        composeNavigator.navigate(WhatsAppScreens.AccountProfile.name)
      },
      onPrivacyClick = {
        composeNavigator.navigate(WhatsAppScreens.PrivacySettings.name)
      },
      onNotificationsClick = {
        composeNavigator.navigate(WhatsAppScreens.NotificationsSettings.name)
      },
      onStorageClick = {
        composeNavigator.navigate(WhatsAppScreens.StorageSettings.name)
      },
      onHelpClick = {
        composeNavigator.navigate(WhatsAppScreens.Help.name)
      },
      onStarredMessagesClick = {
        composeNavigator.navigate(WhatsAppScreens.StarredMessages.name)
      },
      onSignOutClick = {
        scope.launch {
          authViewModel.signOut()
          composeNavigator.navigateAndClearBackStack(WhatsAppScreens.Home.name)
        }
      },
      onDeleteAccountClick = {
        scope.launch {
          authViewModel.deleteAccount()
          composeNavigator.navigateAndClearBackStack(WhatsAppScreens.Home.name)
        }
      },
      onCheckUpdateClick = {
        appUpdateViewModel.checkForUpdate(userInitiated = true)
      }
    )
  }

  composable(route = WhatsAppScreens.AccountProfile.name) {
    AccountProfileScreen(
      onBackClick = { composeNavigator.navigateUp() }
    )
  }

  composable(route = WhatsAppScreens.NotificationsSettings.name) {
    NotificationsSettingsScreen(
      onBackClick = { composeNavigator.navigateUp() }
    )
  }

  composable(route = WhatsAppScreens.StorageSettings.name) {
    StorageSettingsScreen(
      onBackClick = { composeNavigator.navigateUp() }
    )
  }

  composable(route = WhatsAppScreens.Help.name) {
    HelpScreen(
      onBackClick = { composeNavigator.navigateUp() }
    )
  }

  composable(route = WhatsAppScreens.PrivacySettings.name) {
    PrivacySettingsScreen(
      onBackClick = { composeNavigator.navigateUp() },
      onBlockedContactsClick = {
        composeNavigator.navigate(WhatsAppScreens.BlockedContacts.name)
      }
    )
  }

  composable(route = WhatsAppScreens.BlockedContacts.name) {
    BlockedContactsScreen(
      onBackClick = { composeNavigator.navigateUp() }
    )
  }

  composable(
    route = WhatsAppScreens.FriendsContacts.name,
    arguments = WhatsAppScreens.FriendsContacts.navArguments
  ) {
    FriendsContactsScreen()
  }

  composable(route = WhatsAppScreens.ChatSearch.name) {
    ChatSearchScreen()
  }

  composable(route = WhatsAppScreens.StarredMessages.name) {
    StarredMessagesScreen()
  }
}
