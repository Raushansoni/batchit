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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.getstream.video.android.compose.theme.VideoTheme
import io.getstream.video.android.compose.ui.components.call.controls.actions.DefaultOnCallActionHandler
import io.getstream.video.android.compose.ui.components.call.ringing.RingingCallContent
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.call.state.AcceptCall
import io.getstream.video.android.core.call.state.CancelCall
import io.getstream.video.android.core.call.state.DeclineCall
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
      // Accept the ringing call so the caller is notified, then join.
      runCatching { call.accept() }
      val result = call.join()
      result.onSuccess {
        record(call, outgoing = false, missed = false, isVideo = isVideo)
        onJoined(call)
      }
    }
  }

  fun reject(call: Call, isVideo: Boolean = true) {
    viewModelScope.launch {
      runCatching { call.reject() }
      record(call, outgoing = false, missed = true, isVideo = isVideo)
    }
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
  var streamVideo by remember {
    mutableStateOf(runCatching { StreamVideo.instance() }.getOrNull())
  }

  // Video connects after auth; keep retrying so late init still shows ringing UI.
  LaunchedEffect(Unit) {
    while (streamVideo == null) {
      delay(750)
      streamVideo = runCatching { StreamVideo.instance() }.getOrNull()
    }
  }

  val videoClient = streamVideo ?: return
  val ringingCall by videoClient.state.ringingCall.collectAsStateWithLifecycle()
  val call = ringingCall ?: return
  val scope = rememberCoroutineScope()
  val custom by call.state.custom.collectAsStateWithLifecycle()
  val settings by call.state.settings.collectAsStateWithLifecycle()
  val isVideo = remember(custom, settings) {
    resolveIsVideoCall(custom = custom, settings = settings)
  }
  var acceptHandled by remember(call.id) { mutableStateOf(false) }

  VideoTheme {
    RingingCallContent(
      call = call,
      isVideoType = isVideo,
      modifier = Modifier.fillMaxSize(),
      onBackPressed = {
        scope.launch { viewModel.reject(call, isVideo) }
      },
      onCallAction = { action ->
        when (action) {
          AcceptCall -> {
            if (acceptHandled) return@RingingCallContent
            acceptHandled = true
            viewModel.accept(call, isVideo) { joined ->
              onCallConnected(joined.id, isVideo)
            }
          }
          DeclineCall, CancelCall -> {
            scope.launch { viewModel.reject(call, isVideo) }
          }
          else -> DefaultOnCallActionHandler.onCallAction(call, action)
        }
      },
      onAcceptedContent = {
        // Covers accept paths that flip RingingState.Active without our AcceptCall handler
        // (e.g. notification accept while overlay is still composed).
        LaunchedEffect(call.id) {
          if (acceptHandled) return@LaunchedEffect
          acceptHandled = true
          viewModel.accept(call, isVideo) { joined ->
            onCallConnected(joined.id, isVideo)
          }
        }
      }
    )
  }
}
