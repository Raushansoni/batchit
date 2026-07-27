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

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.getstream.video.android.compose.theme.VideoTheme
import io.getstream.video.android.compose.ui.components.avatar.UserAvatarBackground
import io.getstream.video.android.compose.ui.components.call.activecall.CallContent
import io.getstream.video.android.compose.ui.components.video.VideoRenderer
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.CameraDirection
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.call.state.FlipCamera
import io.getstream.video.android.core.call.state.LeaveCall
import io.getstream.video.android.core.call.state.ToggleCamera
import io.getstream.video.android.core.call.state.ToggleMicrophone
import io.getstream.video.android.core.call.state.ToggleSpeakerphone
import io.getstream.video.android.mock.StreamPreviewDataUtils
import io.getstream.video.android.mock.previewCall
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingIndicator
import io.getstream.whatsappclone.uistate.WhatsAppVideoUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WhatsAppVideoCall(
  id: String,
  videoCall: Boolean,
  viewModel: WhatsAppVideoCallViewModel = hiltViewModel()
) {
  val uiState by viewModel.videoUiSate.collectAsStateWithLifecycle()

  EnsureVideoCallPermissions(requireCamera = videoCall) {
    viewModel.joinCall(type = "default", id = id.replace(":", ""))
  }

  when (val state = uiState) {
    is WhatsAppVideoUiState.Success ->
      WhatsAppVideoCallContent(
        call = state.data,
        videoCall = videoCall,
        isOutgoing = viewModel.isOutgoing,
        onBackPressed = { viewModel.navigateUp() }
      )

    is WhatsAppVideoUiState.Error -> WhatsAppVideoCallError(onClose = { viewModel.navigateUp() })

    else -> WhatsAppConnectingScreen(
      videoCall = videoCall,
      onCancel = { viewModel.navigateUp() }
    )
  }
}

@Composable
private fun WhatsAppConnectingScreen(
  videoCall: Boolean,
  onCancel: () -> Unit
) {
  BackHandler(onBack = onCancel)
  WhatsAppCallBackground {
    Box(modifier = Modifier.fillMaxSize()) {
      WhatsAppCallPeerHeader(
        name = if (videoCall) "Video call" else "Voice call",
        status = "Connecting…",
        imageUrl = null,
        showPulse = true
      )
      WhatsAppLoadingIndicator(
        modifier = Modifier.align(Alignment.Center)
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
      ) {
        WhatsAppOutgoingActions(onEnd = onCancel)
      }
    }
  }
}

@Composable
private fun WhatsAppVideoCallContent(
  call: Call,
  videoCall: Boolean,
  isOutgoing: Boolean,
  onBackPressed: () -> Unit
) {
  val me = remember {
    runCatching { StreamVideo.instance().user.id }.getOrNull().orEmpty()
  }
  val members by call.state.members.collectAsStateWithLifecycle()
  val participants by call.state.participants.collectAsStateWithLifecycle()
  val peer = remember(members, me) {
    resolveCallPeer(
      myId = me,
      memberUsers = members.map { it.user }
    )
  }
  val remoteJoined = participants.any { !it.isLocal }

  val isCameraEnabled by call.camera.isEnabled.collectAsStateWithLifecycle()
  val isMicrophoneEnabled by call.microphone.isEnabled.collectAsStateWithLifecycle()
  val isSpeakerphoneEnabled by call.speaker.isEnabled.collectAsStateWithLifecycle()
  val cameraDirection by call.camera.direction.collectAsStateWithLifecycle()

  var connectedAt by remember(call.id) { mutableStateOf<Long?>(null) }
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

  LaunchedEffect(remoteJoined, call.id) {
    if (remoteJoined && connectedAt == null) {
      connectedAt = System.currentTimeMillis()
    }
  }

  LaunchedEffect(connectedAt) {
    if (connectedAt == null) return@LaunchedEffect
    while (isActive) {
      now = System.currentTimeMillis()
      delay(1000)
    }
  }

  // Keep mic publishing after join — early enable can race SFU track setup.
  LaunchedEffect(call.id, videoCall, remoteJoined) {
    repeat(4) { attempt ->
      runCatching {
        call.microphone.setEnabled(true)
        call.camera.setEnabled(videoCall)
        call.speaker.setEnabled(videoCall)
      }
      if (attempt < 3) delay(400)
    }
  }

  DisposableEffect(call.id, videoCall) {
    call.camera.setEnabled(videoCall)
    call.speaker.setEnabled(videoCall)
    call.microphone.setEnabled(true)
    onDispose { runCatching { call.leave() } }
  }

  fun leaveAndNavigateUp() {
    runCatching { call.leave() }
    onBackPressed()
  }

  fun flipCamera() {
    runCatching {
      if (!isCameraEnabled) {
        call.camera.setEnabled(true)
      }
      call.camera.flip()
    }
  }

  BackHandler { leaveAndNavigateUp() }

  val statusText = when {
    connectedAt != null -> formatCallDuration(now - (connectedAt ?: now))
    isOutgoing && !remoteJoined -> "Ringing…"
    !remoteJoined -> "Connecting…"
    else -> "Calling…"
  }
  val showRingingChrome = !remoteJoined || connectedAt == null

  RememberCallRingtone(
    play = showRingingChrome && isOutgoing && !remoteJoined,
    incoming = false
  )

  if (!videoCall || showRingingChrome) {
    // Voice call (always) and video pre-connect use WhatsApp avatar chrome.
    WhatsAppCallBackground {
      Box(modifier = Modifier.fillMaxSize()) {
        WhatsAppCallPeerHeader(
          name = peer?.name.orEmpty().ifBlank { peer?.id.orEmpty() },
          status = statusText,
          imageUrl = peer?.image?.takeIf { it.isNotBlank() },
          showPulse = showRingingChrome
        )

        if (showRingingChrome && isOutgoing) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.BottomCenter)
          ) {
            WhatsAppOutgoingActions(onEnd = { leaveAndNavigateUp() })
          }
        } else {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.BottomCenter)
          ) {
            WhatsAppInCallControls(
              isVideoCall = videoCall,
              isMuted = !isMicrophoneEnabled,
              isSpeakerOn = isSpeakerphoneEnabled,
              isCameraOn = isCameraEnabled,
              onToggleMute = { call.microphone.setEnabled(!isMicrophoneEnabled) },
              onToggleSpeaker = { call.speaker.setEnabled(!isSpeakerphoneEnabled) },
              onToggleCamera = {
                if (videoCall) {
                  call.camera.setEnabled(!isCameraEnabled)
                }
              },
              onFlipCamera = { flipCamera() },
              onEnd = { leaveAndNavigateUp() }
            )
          }
        }
      }
    }
    return
  }

  VideoTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      CallContent(
        call = call,
        onBackPressed = { leaveAndNavigateUp() },
        onCallAction = { action ->
          when (action) {
            is ToggleMicrophone -> call.microphone.setEnabled(action.isEnabled)
            is ToggleCamera -> call.camera.setEnabled(action.isEnabled)
            is ToggleSpeakerphone -> call.speaker.setEnabled(action.isEnabled)
            is FlipCamera -> flipCamera()
            is LeaveCall -> leaveAndNavigateUp()
            else -> Unit
          }
        },
        appBarContent = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = peer?.name.orEmpty().ifBlank { peer?.id.orEmpty().ifBlank { "BatchIt call" } },
              color = Color.White,
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = statusText,
              color = CallSecondaryText,
              fontSize = 13.sp
            )
          }
        },
        videoRenderer = { modifier, videoCallRef, participant, _ ->
          val video by participant.video.collectAsStateWithLifecycle()
          val userName by participant.userNameOrId.collectAsStateWithLifecycle()
          val userImage by participant.image.collectAsStateWithLifecycle()
          val mirrorLocal = participant.isLocal && cameraDirection == CameraDirection.Front
          var localRenderer by remember(participant.sessionId) {
            mutableStateOf<io.getstream.webrtc.android.ui.VideoTextureViewRenderer?>(null)
          }
          LaunchedEffect(mirrorLocal, localRenderer) {
            localRenderer?.let { renderer ->
              runCatching { renderer.setMirror(mirrorLocal) }
            }
          }
          Box(modifier = modifier.fillMaxSize()) {
            VideoRenderer(
              call = videoCallRef,
              video = video,
              modifier = Modifier.fillMaxSize(),
              videoFallbackContent = {
                UserAvatarBackground(userImage = userImage, userName = userName)
              },
              onRendered = { renderer ->
                if (participant.isLocal) {
                  localRenderer = renderer
                  runCatching { renderer.setMirror(mirrorLocal) }
                }
              }
            )
          }
        },
        controlsContent = {
          WhatsAppInCallControls(
            isVideoCall = true,
            isMuted = !isMicrophoneEnabled,
            isSpeakerOn = isSpeakerphoneEnabled,
            isCameraOn = isCameraEnabled,
            onToggleMute = { call.microphone.setEnabled(!isMicrophoneEnabled) },
            onToggleSpeaker = { call.speaker.setEnabled(!isSpeakerphoneEnabled) },
            onToggleCamera = { call.camera.setEnabled(!isCameraEnabled) },
            onFlipCamera = { flipCamera() },
            onEnd = { leaveAndNavigateUp() }
          )
        }
      )
    }
  }
}

@Composable
private fun WhatsAppVideoCallError(onClose: () -> Unit) {
  BackHandler(onBack = onClose)
  WhatsAppCallBackground {
    Box(modifier = Modifier.fillMaxSize()) {
      Text(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(24.dp),
        text = "Couldn’t connect the call. Try again.",
        fontSize = 15.sp,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
      ) {
        WhatsAppOutgoingActions(onEnd = onClose)
      }
    }
  }
}

@Preview
@Composable
private fun WhatsAppVideoCallContentPreview() {
  StreamPreviewDataUtils.initializeStreamVideo(LocalContext.current)
  WhatsAppVideoCallContent(
    call = previewCall,
    videoCall = false,
    isOutgoing = true
  ) {}
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EnsureVideoCallPermissions(
  requireCamera: Boolean = true,
  onPermissionsGranted: () -> Unit
) {
  val permissionsState = rememberMultiplePermissionsState(
    permissions = buildList {
      if (requireCamera) {
        add(Manifest.permission.CAMERA)
      }
      add(Manifest.permission.RECORD_AUDIO)
    }
  )

  LaunchedEffect(key1 = Unit) {
    permissionsState.launchMultiplePermissionRequest()
  }

  LaunchedEffect(key1 = permissionsState.allPermissionsGranted) {
    if (permissionsState.allPermissionsGranted) {
      onPermissionsGranted()
    }
  }
}
