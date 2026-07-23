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

package io.getstream.whatsappclone.chats.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.compose.ui.theme.StreamColors
import io.getstream.whatsappclone.chats.reactions.WhatsAppCloneReactionFactory
import io.getstream.whatsappclone.designsystem.theme.DARK_GREEN300
import io.getstream.whatsappclone.designsystem.theme.GREEN200
import io.getstream.whatsappclone.designsystem.theme.GREEN450
import io.getstream.whatsappclone.designsystem.theme.GREEN600
import io.getstream.whatsappclone.designsystem.theme.LIGHT_CHAT_BACKGROUND
import io.getstream.whatsappclone.designsystem.theme.LocalDarkTheme

@Composable
fun WhatsAppChatTheme(
  darkTheme: Boolean = LocalDarkTheme.current,
  content: @Composable () -> Unit
) {
  val baseColors = if (darkTheme) {
    StreamColors.defaultDarkColors()
  } else {
    StreamColors.defaultColors()
  }
  val streamColors = remember(darkTheme, baseColors) {
    if (darkTheme) {
      baseColors.copy(
        appBackground = DARK_GREEN300,
        primaryAccent = GREEN450,
        ownMessagesBackground = GREEN600
      )
    } else {
      baseColors.copy(
        appBackground = LIGHT_CHAT_BACKGROUND,
        primaryAccent = GREEN450,
        ownMessagesBackground = GREEN200
      )
    }
  }
  val reactionFactory = remember { WhatsAppCloneReactionFactory() }

  ChatTheme(
    colors = streamColors,
    reactionIconFactory = reactionFactory,
    content = content
  )
}
