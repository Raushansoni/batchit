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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.RingingState
import io.getstream.video.android.core.StreamVideo
import io.getstream.whatsappclone.data.repository.CallHistoryRepository
import io.getstream.whatsappclone.model.CallRecord
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class IncomingCallViewModel @Inject constructor(
  private val callHistoryRepository: CallHistoryRepository
) : ViewModel() {

  fun accept(call: Call, isVideo: Boolean, onJoined: (Call) -> Unit) {
    viewModelScope.launch {
      runCatching { call.accept() }
      val result = call.join()
      result.onSuccess {
        applyMediaDefaults(call, isVideo)
        record(call, outgoing = false, missed = false, isVideo = isVideo)
        onJoined(call)
        launch {
          delay(350)
          applyMediaDefaults(call, isVideo)
        }
      }.onError {
        // Still open the call screen if accept succeeded but join raced.
        val activeId = runCatching {
          StreamVideo.instance().state.activeCall.value?.id
        }.getOrNull()
        if (activeId == call.id) {
          applyMediaDefaults(call, isVideo)
          onJoined(call)
        }
      }
    }
  }

  fun reject(call: Call, isVideo: Boolean = true) {
    viewModelScope.launch {
      runCatching { call.reject() }
      runCatching { call.leave() }
      record(call, outgoing = false, missed = true, isVideo = isVideo)
    }
  }

  private fun applyMediaDefaults(call: Call, isVideo: Boolean) {
    runCatching { call.camera.setEnabled(isVideo) }
    runCatching { call.speaker.setEnabled(isVideo) }
    runCatching { call.microphone.setEnabled(true) }
  }

  private fun record(call: Call, outgoing: Boolean, missed: Boolean, isVideo: Boolean) {
    val me = runCatching { StreamVideo.instance().user.id }.getOrNull().orEmpty()
    val peer = call.state.members.value.map { it.user }.firstOrNull { it.id != me }
    callHistoryRepository.recordCall(
      CallRecord(
        callId = call.id,
        peerId = peer?.id.orEmpty(),
        peerName = peer?.name.orEmpty(),
        peerImage = peer?.image.orEmpty(),
        video = isVideo,
        outgoing = outgoing,
        missed = missed
      )
    )
  }
}

@Composable
fun IncomingCallOverlay(
  onCallConnected: (callId: String, video: Boolean) -> Unit,
  viewModel: IncomingCallViewModel = hiltViewModel()
) {
  val streamVideo by produceState<StreamVideo?>(initialValue = null) {
    while (true) {
      val current = runCatching { StreamVideo.instance() }.getOrNull()
      if (current !== value) {
        value = current
      }
      delay(if (current == null) 500 else 2_000)
    }
  }

  val videoClient = streamVideo ?: return
  val ringingCall by videoClient.state.ringingCall.collectAsStateWithLifecycle()
  val call = ringingCall ?: return

  val ringingState by call.state.ringingState.collectAsStateWithLifecycle()
  val createdBy by call.state.createdBy.collectAsStateWithLifecycle()
  val me = remember(videoClient) { videoClient.user.id }

  // ringingCall is set for BOTH incoming and outgoing. Never show Accept/Decline for the caller.
  if (ringingState is RingingState.Outgoing || createdBy?.id == me) return

  val custom by call.state.custom.collectAsStateWithLifecycle()
  val settings by call.state.settings.collectAsStateWithLifecycle()
  val members by call.state.members.collectAsStateWithLifecycle()
  val isVideo = remember(custom, settings) {
    resolveIsVideoCall(custom = custom, settings = settings)
  }

  val peer = remember(members, me) {
    resolveCallPeer(
      myId = me,
      memberUsers = members.map { it.user }
    )
  }

  var acceptHandled by remember(call.id) { mutableStateOf(false) }
  var busy by remember(call.id) { mutableStateOf(false) }

  RememberCallRingtone(play = !busy, incoming = true)

  // Do NOT auto-join when activeCall appears — only Accept (or notification Accept) may connect.

  BackHandler(enabled = !busy) {
    busy = true
    viewModel.reject(call, isVideo)
  }

  WhatsAppCallBackground {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        WhatsAppCallPeerHeader(
          name = peer?.name.orEmpty().ifBlank { peer?.id.orEmpty() },
          status = if (busy) {
            "Connecting…"
          } else if (isVideo) {
            "Incoming video call"
          } else {
            "Incoming voice call"
          },
          imageUrl = peer?.image?.takeIf { it.isNotBlank() },
          showPulse = !busy
        )
        Text(
          text = "BatchIt",
          color = CallSecondaryText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.fillMaxWidth(),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
      ) {
        WhatsAppIncomingActions(
          isVideo = isVideo,
          busy = busy,
          onDecline = {
            if (busy) return@WhatsAppIncomingActions
            busy = true
            viewModel.reject(call, isVideo)
          },
          onAccept = {
            if (acceptHandled || busy) return@WhatsAppIncomingActions
            acceptHandled = true
            busy = true
            viewModel.accept(call, isVideo) { joined ->
              onCallConnected(joined.id, isVideo)
            }
          }
        )
      }
    }
  }
}
