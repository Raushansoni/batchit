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

package io.getstream.whatsappclone.status

import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import io.getstream.log.StreamLog
import io.getstream.whatsappclone.status.model.StatusItem
import io.getstream.whatsappclone.status.model.StatusType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class StatusRepository @Inject constructor() {
  private val logger = StreamLog.getLogger("StatusRepository")

  private val _myStatuses = MutableStateFlow<List<StatusItem>>(emptyList())
  val myStatuses: StateFlow<List<StatusItem>> = _myStatuses.asStateFlow()

  private val _contactStatuses = MutableStateFlow<List<StatusItem>>(emptyList())
  val contactStatuses: StateFlow<List<StatusItem>> = _contactStatuses.asStateFlow()

  private val demoStatuses = mutableListOf<StatusItem>()
  private var useDemoFallback = false

  private fun firestoreOrNull(): FirebaseFirestore? =
    runCatching { FirebaseFirestore.getInstance() }.getOrNull()

  private fun storageOrNull(): FirebaseStorage? =
    runCatching { FirebaseStorage.getInstance() }.getOrNull()

  private fun authOrNull(): FirebaseAuth? =
    runCatching { FirebaseAuth.getInstance() }.getOrNull()

  fun currentUserId(): String =
    authOrNull()?.currentUser?.uid ?: DEMO_USER_ID

  fun currentUserName(): String =
    authOrNull()?.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "You"

  fun currentUserImage(): String =
    authOrNull()?.currentUser?.photoUrl?.toString().orEmpty()

  suspend fun refresh() {
    try {
      loadFromFirebase()
      useDemoFallback = false
    } catch (e: Exception) {
      logger.e(e) { "Firebase status load failed — using demo data" }
      useDemoFallback = true
      ensureDemoStatuses()
      emitDemoStatuses()
    }
  }

  private suspend fun loadFromFirebase() {
    val db = firestoreOrNull() ?: error("Firestore unavailable")
    val uid = currentUserId()
    val now = System.currentTimeMillis()

    val snapshot = db.collection(COLLECTION)
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .limit(100)
      .get()
      .awaitTask()

    val items = snapshot.documents
      .mapNotNull { it.toStatusItem() }
      .filter { it.expiresAt > now }

    _myStatuses.value = items.filter { it.userId == uid }
    _contactStatuses.value = items.filter { it.userId != uid }
  }

  suspend fun getMyStatuses(): List<StatusItem> {
    refresh()
    return _myStatuses.value
  }

  suspend fun getContactStatuses(): List<StatusItem> {
    refresh()
    return _contactStatuses.value
  }

  suspend fun createTextStatus(text: String): Result<StatusItem> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Empty status"))

    val now = System.currentTimeMillis()
    val item = StatusItem(
      id = UUID.randomUUID().toString(),
      userId = currentUserId(),
      userName = currentUserName(),
      userImage = currentUserImage(),
      text = trimmed,
      type = StatusType.TEXT,
      createdAt = now,
      expiresAt = now + StatusItem.TWENTY_FOUR_HOURS_MS
    )
    return persistStatus(item)
  }

  suspend fun createImageStatus(imageUri: Uri, caption: String = ""): Result<StatusItem> {
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID().toString()
    val uid = currentUserId()

    val mediaUrl = try {
      uploadImage(uid, id, imageUri)
    } catch (e: Exception) {
      logger.e(e) { "Image upload failed — storing local uri in demo mode" }
      useDemoFallback = true
      imageUri.toString()
    }

    val item = StatusItem(
      id = id,
      userId = uid,
      userName = currentUserName(),
      userImage = currentUserImage(),
      mediaUrl = mediaUrl,
      text = caption.trim(),
      type = StatusType.IMAGE,
      createdAt = now,
      expiresAt = now + StatusItem.TWENTY_FOUR_HOURS_MS
    )
    return persistStatus(item)
  }

  suspend fun markViewed(statusId: String): Result<Unit> {
    val uid = currentUserId()
    if (useDemoFallback) {
      updateDemoViewed(statusId, uid)
      return Result.success(Unit)
    }

    return try {
      val db = firestoreOrNull() ?: error("Firestore unavailable")
      db.collection(COLLECTION).document(statusId)
        .update("viewedBy", FieldValue.arrayUnion(uid))
        .awaitTask()
      // Patch local state — avoid a full 100-doc refresh on every view.
      patchViewedLocally(statusId, uid)
      Result.success(Unit)
    } catch (e: Exception) {
      logger.e(e) { "markViewed failed" }
      useDemoFallback = true
      updateDemoViewed(statusId, uid)
      Result.success(Unit)
    }
  }

  private fun patchViewedLocally(statusId: String, uid: String) {
    fun List<StatusItem>.patched(): List<StatusItem> = map { item ->
      if (item.id == statusId && uid !in item.viewedBy) {
        item.copy(viewedBy = item.viewedBy + uid)
      } else {
        item
      }
    }
    _myStatuses.value = _myStatuses.value.patched()
    _contactStatuses.value = _contactStatuses.value.patched()
  }

  private suspend fun persistStatus(item: StatusItem): Result<StatusItem> {
    if (useDemoFallback) {
      demoStatuses.add(0, item)
      emitDemoStatuses()
      return Result.success(item)
    }

    return try {
      val db = firestoreOrNull() ?: error("Firestore unavailable")
      db.collection(COLLECTION).document(item.id)
        .set(item.toFirestoreMap(), SetOptions.merge())
        .awaitTask()
      refresh()
      Result.success(item)
    } catch (e: Exception) {
      logger.e(e) { "persistStatus failed — demo fallback" }
      useDemoFallback = true
      demoStatuses.add(0, item)
      ensureDemoStatuses()
      emitDemoStatuses()
      Result.success(item)
    }
  }

  private suspend fun uploadImage(uid: String, statusId: String, uri: Uri): String {
    val storage = storageOrNull() ?: error("Storage unavailable")
    val path = "statuses/$uid/$statusId"
    val ref = storage.reference.child(path)
    ref.putFile(uri).awaitTask()
    return ref.downloadUrl.awaitTask().toString()
  }

  private fun ensureDemoStatuses() {
    if (demoStatuses.any { it.userId != currentUserId() }) return
    val now = System.currentTimeMillis()
    val contacts = listOf(
      StatusItem(
        id = "demo-contact-1",
        userId = "contact_alice",
        userName = "Alice",
        userImage = "https://i.pravatar.cc/150?u=alice",
        text = "Coffee and code",
        type = StatusType.TEXT,
        createdAt = now - 3_600_000,
        expiresAt = now + StatusItem.TWENTY_FOUR_HOURS_MS
      ),
      StatusItem(
        id = "demo-contact-2",
        userId = "contact_bob",
        userName = "Bob",
        userImage = "https://i.pravatar.cc/150?u=bob",
        mediaUrl = "https://picsum.photos/seed/bob/800/1200",
        text = "Weekend hike",
        type = StatusType.IMAGE,
        createdAt = now - 7_200_000,
        expiresAt = now + StatusItem.TWENTY_FOUR_HOURS_MS
      ),
      StatusItem(
        id = "demo-contact-3",
        userId = "contact_alice",
        userName = "Alice",
        userImage = "https://i.pravatar.cc/150?u=alice",
        mediaUrl = "https://picsum.photos/seed/alice/800/1200",
        type = StatusType.IMAGE,
        createdAt = now - 1_800_000,
        expiresAt = now + StatusItem.TWENTY_FOUR_HOURS_MS
      )
    )
    // Keep any statuses the user already created in this session.
    val mine = demoStatuses.filter { it.userId == currentUserId() }
    demoStatuses.clear()
    demoStatuses.addAll(mine)
    demoStatuses.addAll(contacts)
  }

  private fun emitDemoStatuses() {
    val uid = currentUserId()
    _myStatuses.value = demoStatuses.filter { it.userId == uid }
      .sortedByDescending { it.createdAt }
    _contactStatuses.value = demoStatuses.filter { it.userId != uid }
      .sortedByDescending { it.createdAt }
  }

  private fun updateDemoViewed(statusId: String, uid: String) {
    val index = demoStatuses.indexOfFirst { it.id == statusId }
    if (index >= 0) {
      val current = demoStatuses[index]
      if (uid !in current.viewedBy) {
        demoStatuses[index] = current.copy(viewedBy = current.viewedBy + uid)
      }
    }
    emitDemoStatuses()
  }

  private fun DocumentSnapshot.toStatusItem(): StatusItem? {
    val userId = getString("userId") ?: return null
    val typeName = getString("type") ?: StatusType.TEXT.name
    val type = runCatching { StatusType.valueOf(typeName) }.getOrDefault(StatusType.TEXT)
    @Suppress("UNCHECKED_CAST")
    val viewedBy = (get("viewedBy") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    return StatusItem(
      id = id,
      userId = userId,
      userName = getString("userName").orEmpty().ifBlank { userId },
      userImage = getString("userImage").orEmpty(),
      mediaUrl = getString("mediaUrl").orEmpty(),
      text = getString("text").orEmpty(),
      type = type,
      createdAt = timestampMillis("createdAt"),
      expiresAt = timestampMillis("expiresAt"),
      viewedBy = viewedBy
    )
  }

  private fun DocumentSnapshot.timestampMillis(field: String): Long {
    val value = get(field) ?: return System.currentTimeMillis()
    return when (value) {
      is Timestamp -> value.toDate().time
      is Long -> value
      is Number -> value.toLong()
      else -> System.currentTimeMillis()
    }
  }

  private fun StatusItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "userId" to userId,
    "userName" to userName,
    "userImage" to userImage,
    "mediaUrl" to mediaUrl,
    "text" to text,
    "type" to type.name,
    "createdAt" to Timestamp(createdAt / 1000, ((createdAt % 1000) * 1_000_000).toInt()),
    "expiresAt" to Timestamp(expiresAt / 1000, ((expiresAt % 1000) * 1_000_000).toInt()),
    "viewedBy" to viewedBy,
    "storagePath" to if (mediaUrl.isNotBlank() && type != StatusType.TEXT) {
      "statuses/$userId/$id"
    } else {
      null
    }
  )

  companion object {
    private const val COLLECTION = "statuses"
    const val DEMO_USER_ID = "demo-user"
  }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { cont ->
  addOnSuccessListener { result -> cont.resume(result) }
  addOnFailureListener { error -> cont.resumeWithException(error) }
}
