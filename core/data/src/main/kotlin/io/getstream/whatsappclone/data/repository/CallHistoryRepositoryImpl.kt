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

package io.getstream.whatsappclone.data.repository

import io.getstream.whatsappclone.model.CallRecord
import io.getstream.whatsappclone.model.WhatsAppUser
import io.getstream.whatsappclone.network.Dispatcher
import io.getstream.whatsappclone.network.WhatsAppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal class CallHistoryRepositoryImpl @Inject constructor(
  @Dispatcher(WhatsAppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
  private val localCallHistoryStore: LocalCallHistoryStore
) : CallHistoryRepository {

  override fun getCallHistoryUsersStream(): Flow<Result<List<WhatsAppUser>>> =
    localCallHistoryStore.records
      .map { records -> Result.success(records.map { it.toWhatsAppUser() }) }
      .flowOn(ioDispatcher)

  override suspend fun updateCallCallHistoryUsers(whatsappUsers: Result<List<WhatsAppUser>>) {
    // Legacy API kept for DI compatibility; real history uses [recordCall].
  }

  override fun recordCall(record: CallRecord) {
    localCallHistoryStore.add(record)
  }
}
