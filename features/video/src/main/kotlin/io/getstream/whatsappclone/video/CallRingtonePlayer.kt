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

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Plays looping call wait / ring tones while the custom call chrome is visible.
 * Stream's CallService ringtone often never starts with a custom overlay + missing
 * intent-filters, so we own playback here.
 */
internal class CallRingtonePlayer(private val context: Context) {
  private var player: MediaPlayer? = null

  fun playIncoming() {
    start(
      resolveRawUri("call_incoming_sound")
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    )
  }

  fun playOutgoing() {
    start(
      resolveRawUri("call_outgoing_sound")
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    )
  }

  fun stop() {
    val current = player ?: return
    player = null
    runCatching { current.stop() }
    runCatching { current.release() }
  }

  private fun start(uri: Uri?) {
    if (uri == null) return
    stop()
    runCatching {
      val mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
        setDataSource(context, uri)
        isLooping = true
        setOnPreparedListener { preparedPlayer ->
          if (player === preparedPlayer) {
            runCatching { preparedPlayer.start() }
          } else {
            preparedPlayer.release()
          }
        }
        setOnErrorListener { failedPlayer, _, _ ->
          if (player === failedPlayer) stop()
          true
        }
        prepareAsync()
      }
      player = mediaPlayer
    }.onFailure {
      stop()
    }
  }

  private fun resolveRawUri(name: String): Uri? {
    val resId = context.resources.getIdentifier(name, "raw", context.packageName)
    if (resId == 0) return null
    return Uri.parse("android.resource://${context.packageName}/$resId")
  }
}

@Composable
internal fun RememberCallRingtone(
  play: Boolean,
  incoming: Boolean
) {
  val context = LocalContext.current
  val player = remember(context) { CallRingtonePlayer(context) }

  DisposableEffect(play, incoming) {
    if (play) {
      if (incoming) player.playIncoming() else player.playOutgoing()
    } else {
      player.stop()
    }
    onDispose { player.stop() }
  }
}
