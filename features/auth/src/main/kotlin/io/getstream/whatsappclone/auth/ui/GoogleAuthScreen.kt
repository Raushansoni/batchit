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

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.getstream.whatsappclone.auth.BatchItAuthConfig
import io.getstream.whatsappclone.auth.BuildConfig
import io.getstream.whatsappclone.auth.R
import io.getstream.whatsappclone.designsystem.theme.WhatsAppCloneComposeTheme
import io.getstream.whatsappclone.designsystem.theme.getTitleColor
import kotlinx.coroutines.launch

@Composable
fun GoogleAuthScreen(
  isSubmitting: Boolean,
  onGoogleIdToken: (String) -> Unit,
  onError: (String) -> Unit,
  onCancelled: () -> Unit,
  onDemoContinue: () -> Unit,
  onSubmittingChange: (Boolean) -> Unit
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  fun launchGoogleSignIn() {
    if (activity == null) {
      onError("Google sign-in needs an Activity context.")
      return
    }
    val webClientId = resolveWebClientId(activity)
    if (webClientId.isBlank() || webClientId == "REPLACE_ME") {
      onError("Set GOOGLE_WEB_CLIENT_ID in secrets.properties (Web client ID from Firebase).")
      return
    }

    scope.launch {
      onSubmittingChange(true)
      try {
        val idToken = requestGoogleIdToken(
          activity = activity,
          credentialManager = credentialManager,
          webClientId = webClientId
        )
        onGoogleIdToken(idToken)
      } catch (cancelled: GetCredentialCancellationException) {
        onCancelled()
      } catch (error: Throwable) {
        onError(humanizeGoogleSignInError(error))
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(id = R.string.auth_welcome_title),
      style = MaterialTheme.typography.headlineSmall,
      color = getTitleColor()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(id = R.string.auth_welcome_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onTertiary
    )
    Spacer(modifier = Modifier.height(32.dp))

    if (isSubmitting) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
    } else {
      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = ::launchGoogleSignIn,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
      ) {
        Text(
          text = stringResource(id = R.string.auth_google_sign_in),
          color = MaterialTheme.colorScheme.onSecondary
        )
      }

      if (BatchItAuthConfig.USE_DEMO_AUTH) {
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDemoContinue) {
          Text(
            text = stringResource(id = R.string.auth_demo_continue),
            color = MaterialTheme.colorScheme.secondary
          )
        }
      }
    }
  }
}

/**
 * Prefer the Web client ID baked in by google-services.json, then secrets.properties.
 * Credential Manager must receive the *Web* client ID, not an Android client ID.
 */
private fun resolveWebClientId(activity: Activity): String {
  val fromGoogleServices = runCatching {
    val resId = activity.resources.getIdentifier(
      "default_web_client_id",
      "string",
      activity.packageName
    )
    if (resId != 0) activity.getString(resId).trim() else ""
  }.getOrDefault("")
  if (fromGoogleServices.isNotBlank() && fromGoogleServices != "REPLACE_ME") {
    return fromGoogleServices
  }
  return BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
}

private suspend fun requestGoogleIdToken(
  activity: Activity,
  credentialManager: CredentialManager,
  webClientId: String
): String {
  // Explicit button flow first — matches "Continue with Google" and is more reliable
  // than One Tap on misconfigured / freshly created OAuth brands.
  try {
    val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(signInOption)
      .build()
    val result = credentialManager.getCredential(activity, request)
    return extractGoogleIdToken(result.credential)
  } catch (error: Throwable) {
    if (error is GetCredentialCancellationException || isDeveloperConsoleError(error)) {
      throw error
    }
    if (error !is NoCredentialException) {
      throw error
    }
  }

  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(false)
    .setServerClientId(webClientId)
    .setAutoSelectEnabled(false)
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()
  val result = credentialManager.getCredential(activity, request)
  return extractGoogleIdToken(result.credential)
}

private fun extractGoogleIdToken(credential: androidx.credentials.Credential): String {
  if (credential is CustomCredential &&
    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
  ) {
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
  }
  error("Unexpected credential type from Google Sign-In")
}

private fun isDeveloperConsoleError(error: Throwable): Boolean {
  val raw = error.message.orEmpty()
  return raw.contains("28444") ||
    raw.contains("Developer console is not set up correctly", ignoreCase = true)
}

private fun humanizeGoogleSignInError(error: Throwable): String {
  if (isDeveloperConsoleError(error)) {
    return "Google Sign-In developer console error (28444). " +
      "Confirm project batchit-prod (not jollypet): release SHA-1 is registered, " +
      "OAuth consent/brand exists, and this APK was rebuilt after those changes. " +
      "Use the Web client ID, not the Android client ID."
  }
  return error.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
}

@Preview
@Composable
private fun GoogleAuthScreenPreview() {
  WhatsAppCloneComposeTheme {
    GoogleAuthScreen(
      isSubmitting = false,
      onGoogleIdToken = {},
      onError = {},
      onCancelled = {},
      onDemoContinue = {},
      onSubmittingChange = {}
    )
  }
}
