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

package io.getstream.whatsappclone.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.StreamVideo
import io.getstream.whatsappclone.data.repository.CallHistoryRepository
import io.getstream.whatsappclone.model.CallRecord
import io.getstream.whatsappclone.navigation.AppComposeNavigator
import io.getstream.whatsappclone.navigation.WhatsAppScreens
import io.getstream.whatsappclone.uistate.WhatsAppVideoUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WhatsAppVideoCallViewModel @Inject constructor(
  private val composeNavigator: AppComposeNavigator,
  private val callHistoryRepository: CallHistoryRepository,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val memberIds: List<String> =
    savedStateHandle.get<String>(WhatsAppScreens.VideoCall.KEY_MEMBERS)
      .orEmpty()
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }

  private val isVideoCall: Boolean =
    savedStateHandle.get<Boolean>(WhatsAppScreens.VideoCall.KEY_VIDEO_ID) ?: true

  private val videoMutableUiState =
    MutableStateFlow<WhatsAppVideoUiState>(WhatsAppVideoUiState.Loading)
  val videoUiSate: StateFlow<WhatsAppVideoUiState> = videoMutableUiState

  fun joinCall(type: String, id: String) {
    viewModelScope.launch {
      val streamVideo = runCatching { StreamVideo.instance() }.getOrNull()
      if (streamVideo == null) {
        videoMutableUiState.value = WhatsAppVideoUiState.Error
        return@launch
      }

      val activeCall = streamVideo.state.activeCall.value
      val call = if (activeCall != null) {
        if (activeCall.id != id) {
          activeCall.leave()
          streamVideo.call(type = type, id = id)
        } else {
          // Already joined (e.g. accept from ringing overlay / notification).
          recordHistory(activeCall, outgoing = memberIds.isNotEmpty())
          videoMutableUiState.value = WhatsAppVideoUiState.Success(activeCall)
          return@launch
        }
      } else {
        streamVideo.call(type = type, id = id)
      }

      if (memberIds.isNotEmpty()) {
        val me = streamVideo.user.id
        val allMembers = (memberIds + me).distinct()
        val createResult = call.create(memberIds = allMembers, ring = true)
        if (createResult.isFailure) {
          videoMutableUiState.value = WhatsAppVideoUiState.Error
          return@launch
        }
      }

      // Incoming / rejoin must not create a new call — that breaks accept flows.
      val result = call.join(create = false)

      result.onSuccess {
        recordHistory(call, outgoing = memberIds.isNotEmpty())
        videoMutableUiState.value = WhatsAppVideoUiState.Success(call)
      }.onError {
        videoMutableUiState.value = WhatsAppVideoUiState.Error
      }
    }
  }

  fun acceptIncoming(call: Call) {
    viewModelScope.launch {
      val result = call.join()
      result.onSuccess {
        recordHistory(call, outgoing = false)
        videoMutableUiState.value = WhatsAppVideoUiState.Success(call)
      }.onError {
        videoMutableUiState.value = WhatsAppVideoUiState.Error
      }
    }
  }

  fun rejectIncoming(call: Call) {
    viewModelScope.launch {
      runCatching { call.reject() }
      recordHistory(call, outgoing = false, missed = true)
      navigateUp()
    }
  }

  private fun recordHistory(call: Call, outgoing: Boolean, missed: Boolean = false) {
    val me = StreamVideo.instance().user.id
    val peer = call.state.members.value
      .map { it.user }
      .firstOrNull { it.id != me }
    callHistoryRepository.recordCall(
      CallRecord(
        callId = call.id,
        peerId = peer?.id ?: memberIds.firstOrNull().orEmpty(),
        peerName = peer?.name ?: memberIds.firstOrNull().orEmpty(),
        peerImage = peer?.image.orEmpty(),
        video = isVideoCall,
        outgoing = outgoing,
        missed = missed
      )
    )
  }

  fun navigateUp() {
    composeNavigator.navigateUp()
  }
}
