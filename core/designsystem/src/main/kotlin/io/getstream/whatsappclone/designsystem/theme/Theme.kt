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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalDarkTheme = staticCompositionLocalOf { true }

private val DarkWhatsAppColorScheme = darkColorScheme(
  primary = DARK_GREEN200,
  onPrimary = WHITE200,
  primaryContainer = DARK_SURFACE,
  onPrimaryContainer = WHITE200,
  secondary = GREEN450,
  onSecondary = Color.White,
  secondaryContainer = GREEN600,
  onSecondaryContainer = WHITE200,
  tertiary = WHITE200,
  onTertiary = GRAY200,
  background = DARK_GREEN300,
  onBackground = WHITE200,
  surface = DARK_SURFACE,
  onSurface = WHITE200,
  surfaceVariant = DARK_GREEN200,
  onSurfaceVariant = GRAY200,
  outline = DARK_OUTLINE,
  error = Color(0xFFEF5350),
  onError = Color.White
)

private val LightWhatsAppColorScheme = lightColorScheme(
  primary = GREEN500,
  onPrimary = Color.White,
  primaryContainer = LIGHT_PRIMARY_CONTAINER,
  onPrimaryContainer = GREEN700,
  secondary = GREEN450,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFC8E6C9),
  onSecondaryContainer = GREEN700,
  tertiary = Color.White,
  onTertiary = GRAY200,
  background = LIGHT_BACKGROUND,
  onBackground = BLACK200,
  surface = LIGHT_SURFACE,
  onSurface = BLACK200,
  surfaceVariant = Color(0xFFE9EDEF),
  onSurfaceVariant = GRAY200,
  outline = Color(0xFFD1D7DB),
  error = Color(0xFFD32F2F),
  onError = Color.White
)

private val LightAndroidBackgroundTheme = BackgroundTheme(color = LIGHT_BACKGROUND)
private val DarkAndroidBackgroundTheme = BackgroundTheme(color = DARK_GREEN300)

@Composable
fun WhatsAppCloneComposeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkWhatsAppColorScheme else LightWhatsAppColorScheme
  val backgroundTheme = if (darkTheme) DarkAndroidBackgroundTheme else LightAndroidBackgroundTheme

  CompositionLocalProvider(
    LocalBackgroundTheme provides backgroundTheme,
    LocalDarkTheme provides darkTheme
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
