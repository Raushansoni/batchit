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

package io.getstream.whatsappclone.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface NotificationDeepLink {
  data class OpenChat(val channelCid: String) : NotificationDeepLink
  data class OpenVideoCall(val callId: String, val video: Boolean) : NotificationDeepLink
  data object OpenCallsTab : NotificationDeepLink
}

@Singleton
class NotificationDeepLinkBus @Inject constructor() {
  private val _events = MutableSharedFlow<NotificationDeepLink>(extraBufferCapacity = 4)
  val events: SharedFlow<NotificationDeepLink> = _events.asSharedFlow()

  fun emit(link: NotificationDeepLink) {
    _events.tryEmit(link)
  }
}
