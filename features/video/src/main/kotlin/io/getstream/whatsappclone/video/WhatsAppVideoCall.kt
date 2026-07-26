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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
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
import io.getstream.video.android.compose.ui.components.call.controls.ControlActions
import io.getstream.video.android.compose.ui.components.call.controls.actions.DefaultOnCallActionHandler
import io.getstream.video.android.compose.ui.components.call.controls.actions.FlipCameraAction
import io.getstream.video.android.compose.ui.components.call.controls.actions.GenericAction
import io.getstream.video.android.compose.ui.components.call.controls.actions.LeaveCallAction
import io.getstream.video.android.compose.ui.components.call.controls.actions.ToggleCameraAction
import io.getstream.video.android.compose.ui.components.call.controls.actions.ToggleMicrophoneAction
import io.getstream.video.android.compose.ui.components.call.controls.actions.ToggleSpeakerphoneAction
import io.getstream.video.android.compose.ui.components.call.renderer.ParticipantLabel
import io.getstream.video.android.compose.ui.components.video.VideoRenderer
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.call.state.FlipCamera
import io.getstream.video.android.core.call.state.LeaveCall
import io.getstream.video.android.core.call.state.ToggleCamera
import io.getstream.video.android.core.call.state.ToggleMicrophone
import io.getstream.video.android.core.call.state.ToggleSpeakerphone
import io.getstream.video.android.core.mapper.ReactionMapper
import io.getstream.video.android.mock.StreamPreviewDataUtils
import io.getstream.video.android.mock.previewCall
import io.getstream.whatsappclone.designsystem.component.WhatsAppLoadingIndicator
import io.getstream.whatsappclone.uistate.WhatsAppVideoUiState

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

  when (uiState) {
    is WhatsAppVideoUiState.Success ->
      WhatsAppVideoCallContent(
        call = (uiState as WhatsAppVideoUiState.Success).data,
        videoCall = videoCall,
        onBackPressed = { viewModel.navigateUp() }
      )

    is WhatsAppVideoUiState.Error -> WhatsAppVideoCallError()

    else -> WhatsAppVideoLoading()
  }
}

@Composable
private fun WhatsAppVideoCallContent(
  call: Call,
  videoCall: Boolean,
  onBackPressed: () -> Unit
) {
  val isCameraEnabled by call.camera.isEnabled.collectAsStateWithLifecycle()
  val isMicrophoneEnabled by call.microphone.isEnabled.collectAsStateWithLifecycle()
  val isSpeakerphoneEnabled by call.speaker.isEnabled.collectAsStateWithLifecycle()
  var isShowingReactionDialog by remember { mutableStateOf(false) }

  DisposableEffect(key1 = call.id) {
    call.camera.setEnabled(videoCall)
    call.speaker.setEnabled(true)
    onDispose { call.leave() }
  }

  fun leaveAndNavigateUp() {
    call.leave()
    onBackPressed.invoke()
  }

  VideoTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      CallContent(
        call = call,
        onBackPressed = { leaveAndNavigateUp() },
        onCallAction = { action ->
          when (action) {
            LeaveCall -> leaveAndNavigateUp()
            is ToggleSpeakerphone -> call.speaker.setEnabled(action.isEnabled)
            is ToggleCamera -> call.camera.setEnabled(action.isEnabled)
            is ToggleMicrophone -> call.microphone.setEnabled(action.isEnabled)
            FlipCamera -> {
              if (call.camera.isEnabled.value) {
                call.camera.flip()
              }
            }
            else -> DefaultOnCallActionHandler.onCallAction(call, action)
          }
        },
        // Natural (non-mirrored) local preview — matches what the remote peer sees.
        videoRenderer = { modifier, videoCallRef, participant, style ->
          val video by participant.video.collectAsStateWithLifecycle()
          val userName by participant.userNameOrId.collectAsStateWithLifecycle()
          val userImage by participant.image.collectAsStateWithLifecycle()
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
                  runCatching { renderer.setMirror(false) }
                }
              }
            )
            if (style.isShowingParticipantLabel) {
              ParticipantLabel(
                call = videoCallRef,
                participant = participant,
                labelPosition = style.labelPosition
              )
            }
          }
        },
        controlsContent = {
          ControlActions(
            call = call,
            actions = buildList {
              add {
                GenericAction(
                  icon = ImageVector.vectorResource(
                    id = io.getstream.video.android.ui.common.R.drawable.stream_video_ic_reaction
                  ),
                  modifier = Modifier.size(52.dp),
                  onAction = { isShowingReactionDialog = true }
                )
              }
              if (videoCall) {
                add {
                  ToggleCameraAction(
                    modifier = Modifier.size(52.dp),
                    isCameraEnabled = isCameraEnabled,
                    onCallAction = { call.camera.setEnabled(it.isEnabled) }
                  )
                }
              }
              add {
                ToggleMicrophoneAction(
                  modifier = Modifier.size(52.dp),
                  isMicrophoneEnabled = isMicrophoneEnabled,
                  onCallAction = { call.microphone.setEnabled(it.isEnabled) }
                )
              }
              add {
                ToggleSpeakerphoneAction(
                  modifier = Modifier.size(52.dp),
                  isSpeakerphoneEnabled = isSpeakerphoneEnabled,
                  onCallAction = { call.speaker.setEnabled(it.isEnabled) }
                )
              }
              if (videoCall && isCameraEnabled) {
                add {
                  FlipCameraAction(
                    modifier = Modifier.size(52.dp),
                    onCallAction = { call.camera.flip() }
                  )
                }
              }
              add {
                LeaveCallAction(
                  modifier = Modifier.size(52.dp),
                  onCallAction = { leaveAndNavigateUp() }
                )
              }
            }
          )
        }
      )

      if (isShowingReactionDialog) {
        ReactionsMenu(
          call = call,
          reactionMapper = ReactionMapper.defaultReactionMapper(),
          onDismiss = { isShowingReactionDialog = false }
        )
      }
    }
  }
}

@Composable
private fun WhatsAppVideoCallError() {
  Box(modifier = Modifier.fillMaxSize()) {
    Text(
      modifier = Modifier.align(Alignment.Center),
      text = "Something went wrong; failed to join a call",
      fontSize = 14.sp,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onTertiary
    )
  }
}

@Composable
private fun WhatsAppVideoLoading() {
  Box(modifier = Modifier.fillMaxSize()) {
    WhatsAppLoadingIndicator(modifier = Modifier.align(Alignment.Center))
  }
}

@Preview
@Composable
private fun WhatsAppVideoCallContentPreview() {
  StreamPreviewDataUtils.initializeStreamVideo(LocalContext.current)
  VideoTheme {
    WhatsAppVideoCallContent(
      call = previewCall,
      videoCall = true
    ) {}
  }
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
