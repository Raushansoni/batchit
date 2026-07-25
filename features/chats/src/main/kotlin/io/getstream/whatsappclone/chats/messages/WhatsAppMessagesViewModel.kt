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

package io.getstream.whatsappclone.chats.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelRequest
import io.getstream.chat.android.models.Attachment
import io.getstream.chat.android.models.Message
import io.getstream.whatsappclone.chats.starred.StarredMessagesStore
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppScreens
import io.getstream.whatsappclone.uistate.WhatsAppMessageUiState
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@HiltViewModel
class WhatsAppMessagesViewModel @Inject constructor(
  private val chatClient: ChatClient,
  private val composeNavigator: AppComposeNavigator,
  private val starredMessagesStore: StarredMessagesStore,
  @ApplicationContext private val context: Context,
  savedStateHandle: SavedStateHandle
) : ViewModel() {
  private val messageMutableUiState =
    MutableStateFlow<WhatsAppMessageUiState>(WhatsAppMessageUiState.Loading)
  val messageUiSate: StateFlow<WhatsAppMessageUiState> = messageMutableUiState

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage

  private val channelId = savedStateHandle.get<String>("channelId")

  init {
    if (channelId != null) {
      fetchChannelHeader(channelId = channelId)
    }
  }

  fun handleEvents(whatsAppMessageEvent: WhatsAppMessageEvent) {
    when (whatsAppMessageEvent) {
      is WhatsAppMessageEvent.NavigateUp -> composeNavigator.navigateUp()
    }
  }

  fun clearActionMessage() {
    _actionMessage.value = null
  }

  fun navigateToVideoCall(channelId: String, videoCall: Boolean) {
    viewModelScope.launch {
      val me = chatClient.getCurrentUser()?.id
      val request = QueryChannelRequest().withState()
      val channelResult = chatClient.channel(channelId).query(request).await()
      val memberIds = channelResult.getOrNull()?.members
        ?.mapNotNull { it.user.id }
        ?.filter { it != me }
        .orEmpty()
      val members = memberIds.joinToString(",")

      composeNavigator.navigate(
        WhatsAppScreens.VideoCall.createRoute(
          callId = UUID.randomUUID().toString(),
          videoCall = videoCall,
          members = members
        )
      )
    }
  }

  fun starLatestMessage() {
    val cid = channelId ?: return
    viewModelScope.launch {
      val request = QueryChannelRequest().withMessages(1)
      val result = chatClient.channel(cid).query(request).await()
      val latest = result.getOrNull()?.messages?.lastOrNull()
      if (latest == null) {
        _actionMessage.value = "No messages to star"
        return@launch
      }
      starredMessagesStore.star(
        messageId = latest.id,
        channelId = cid,
        previewText = latest.text
      )
      _actionMessage.value = "Message starred"
    }
  }

  fun shareLocationPlaceholder() {
    val cid = channelId ?: return
    viewModelScope.launch {
      if (!hasLocationPermission()) {
        _actionMessage.value = "Location permission needed to share a real location"
        return@launch
      }
      val location = runCatching { getCurrentLocation() }.getOrNull()
      if (location == null) {
        _actionMessage.value = "Could not get your location; try again"
        return@launch
      }
      val lat = location.latitude
      val lng = location.longitude
      val mapsUrl = "https://www.google.com/maps?q=$lat,$lng"
      val staticMapUrl =
        "https://staticmap.openstreetmap.de/staticmap.php" +
          "?center=$lat,$lng&zoom=16&size=400x200&markers=$lat,$lng,red-pushpin"

      val attachment = Attachment(
        type = "image",
        imageUrl = staticMapUrl,
        title = "Location",
        titleLink = mapsUrl
      )
      val message = Message(
        text = "\uD83D\uDCCD Location\n$mapsUrl",
        attachments = listOf(attachment)
      )
      chatClient.channel(cid).sendMessage(message).await()
        .onSuccess { _actionMessage.value = "Location sent" }
        .onError { _actionMessage.value = it.message ?: "Could not send location" }
    }
  }

fun showLocationPermissionDenied() {
    _actionMessage.value = "Location permission denied"
  }

  fun hasLocationPermission(): Boolean {
  val fine = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.ACCESS_FINE_LOCATION
  ) == PackageManager.PERMISSION_GRANTED
  val coarse = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.ACCESS_COARSE_LOCATION
  ) == PackageManager.PERMISSION_GRANTED
  return fine || coarse
}

private suspend fun getCurrentLocation(): Location? {
  val client = LocationServices.getFusedLocationProviderClient(context)
  val last = runCatching { client.lastLocation.awaitTask() }.getOrNull()
  if (last != null) return last
  val request = CurrentLocationRequest.Builder()
    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
    .build()
  return runCatching { client.getCurrentLocation(request, null).awaitTask() }.getOrNull()
}

  /**
   * Lightweight header fetch — do not call channel.watch() (MessagesScreen owns that).
   */
  private fun fetchChannelHeader(channelId: String) {
    viewModelScope.launch {
      val request = QueryChannelRequest().withMessages(0)
      val result = chatClient.channel(channelId).query(request).await()
      result.onSuccess {
        messageMutableUiState.value = WhatsAppMessageUiState.Success(result.getOrThrow())
      }.onError {
        messageMutableUiState.value = WhatsAppMessageUiState.Error
      }
    }
  }
}

sealed interface WhatsAppMessageEvent {
  data object NavigateUp : WhatsAppMessageEvent
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
  addOnSuccessListener { result -> cont.resume(result) }
  addOnFailureListener { error -> cont.resumeWithException(error) }
}
