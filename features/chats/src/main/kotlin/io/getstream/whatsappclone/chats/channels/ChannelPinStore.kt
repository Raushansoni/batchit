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

package io.getstream.whatsappclone.chats.channels

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelPinStore @Inject constructor(
  @ApplicationContext context: Context
) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun isPinned(channelCid: String): Boolean =
    prefs.getStringSet(KEY_PINNED, emptySet())?.contains(channelCid) == true

  fun togglePin(channelCid: String): Boolean {
    val pinned = prefs.getStringSet(KEY_PINNED, emptySet())?.toMutableSet() ?: mutableSetOf()
    val nowPinned = if (channelCid in pinned) {
      pinned.remove(channelCid)
      false
    } else {
      pinned.add(channelCid)
      true
    }
    prefs.edit().putStringSet(KEY_PINNED, pinned).apply()
    return nowPinned
  }

  companion object {
    private const val PREFS_NAME = "batchit_channel_pins"
    private const val KEY_PINNED = "pinned_cids"
  }
}
