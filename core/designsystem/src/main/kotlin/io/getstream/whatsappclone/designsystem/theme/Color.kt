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

package io.getstream.whatsappclone.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val GREEN200 = Color(0xFFE2FFC7)
val GREEN300 = Color(0xFF7EB0AD)
val GREEN400 = Color(0xFF16CC3E)
val GREEN450 = Color(0xFF1AA05B)
val GREEN500 = Color(0xFF19887A)
val GREEN600 = Color(0xFF0E5E55)
val GREEN700 = Color(0xFF00574B)

val DARK_GREEN200 = Color(0xFF232D36)
val DARK_GREEN300 = Color(0xFF101D25)

val WHITE200 = Color(0xFFE9EDEF)
val BLACK200 = Color(0xFF111B21)
val GRAY100 = Color(0xC1EFF0F3)
val GRAY200 = Color(0xFF8696A0)

val LIGHT_SURFACE = Color(0xFFFFFFFF)
val LIGHT_BACKGROUND = Color(0xFFF0F2F5)
val LIGHT_CHAT_BACKGROUND = Color(0xFFEFE7DE)
val LIGHT_PRIMARY_CONTAINER = Color(0xFFD1F4E8)

val DARK_SURFACE = Color(0xFF1F2C34)
val DARK_OUTLINE = Color(0xFF3B4A54)

val shimmerHighLight = Color(0xA3C2C2C2)

@Composable
@ReadOnlyComposable
fun getTabPrimaryColor(): Color {
  return if (LocalDarkTheme.current) {
    GREEN450
  } else {
    Color.White
  }
}

@Composable
@ReadOnlyComposable
fun getTabUnselectedColor(): Color {
  return if (LocalDarkTheme.current) {
    WHITE200.copy(alpha = 0.55f)
  } else {
    Color.White.copy(alpha = 0.65f)
  }
}

@Composable
@ReadOnlyComposable
fun getTitleColor(): Color = MaterialTheme.colorScheme.onBackground

@Composable
@ReadOnlyComposable
fun getChromeContentColor(): Color = MaterialTheme.colorScheme.onPrimary
