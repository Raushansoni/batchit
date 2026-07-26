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

import io.getstream.video.android.core.Call
import org.openapitools.client.models.CallSettingsResponse

const val CALL_CUSTOM_IS_VIDEO = "isVideo"

fun resolveIsVideoCall(
  custom: Map<String, Any?>,
  settings: CallSettingsResponse?
): Boolean {
  when (val flag = custom[CALL_CUSTOM_IS_VIDEO]) {
    is Boolean -> return flag
    is String -> return flag.equals("true", ignoreCase = true) || flag == "1"
  }
  val video = settings?.video ?: return true
  // Prefer cameraDefaultOn — enabled alone is often true for the call type even on audio.
  return video.cameraDefaultOn ?: true
}

fun Call.resolveIsVideoCall(): Boolean =
  resolveIsVideoCall(
    custom = runCatching { state.custom.value }.getOrDefault(emptyMap()),
    settings = state.settings.value
  )
