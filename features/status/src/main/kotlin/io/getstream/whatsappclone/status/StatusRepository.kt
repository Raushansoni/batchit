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

  fun currentUserId(): String? = authOrNull()?.currentUser?.uid

  fun currentUserName(): String =
    authOrNull()?.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "You"

  fun currentUserImage(): String =
    authOrNull()?.currentUser?.photoUrl?.toString().orEmpty()

  suspend fun refresh(): Result<Unit> {
    if (useDemoFallback) {
      emitDemoStatuses()
      return Result.success(Unit)
    }
    return try {
      loadFromFirebase()
      Result.success(Unit)
    } catch (e: Exception) {
      logger.e(e) { "Firebase status load failed" }
      _myStatuses.value = emptyList()
      _contactStatuses.value = emptyList()
      if (ENABLE_DEMO_FALLBACK) {
        useDemoFallback = true
        ensureDemoStatuses()
        emitDemoStatuses()
        Result.success(Unit)
      } else {
        Result.failure(e)
      }
    }
  }

  private suspend fun loadFromFirebase() {
    val db = firestoreOrNull() ?: error("Firestore unavailable")
    val uid = currentUserId() ?: error("Not signed in")
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
    val uid = currentUserId() ?: return Result.failure(IllegalStateException("Not signed in"))

    val now = System.currentTimeMillis()
    val item = StatusItem(
      id = UUID.randomUUID().toString(),
      userId = uid,
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
    val uid = currentUserId() ?: return Result.failure(IllegalStateException("Not signed in"))
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID().toString()

    val mediaUrl = try {
      uploadMedia(uid, id, imageUri)
    } catch (e: Exception) {
      logger.e(e) { "Image upload failed — using local uri" }
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

  suspend fun createVideoStatus(videoUri: Uri, caption: String = ""): Result<StatusItem> {
    val uid = currentUserId() ?: return Result.failure(IllegalStateException("Not signed in"))
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID().toString()

    val mediaUrl = try {
      uploadMedia(uid, id, videoUri)
    } catch (e: Exception) {
      logger.e(e) { "Video upload failed — using local uri" }
      videoUri.toString()
    }

    val item = StatusItem(
      id = id,
      userId = uid,
      userName = currentUserName(),
      userImage = currentUserImage(),
      mediaUrl = mediaUrl,
      text = caption.trim(),
      type = StatusType.VIDEO,
      createdAt = now,
      expiresAt = now + StatusItem.TWENTY_FOUR_HOURS_MS
    )
    return persistStatus(item)
  }

  suspend fun markViewed(statusId: String): Result<Unit> {
    val uid = currentUserId() ?: return Result.failure(IllegalStateException("Not signed in"))
    if (useDemoFallback) {
      updateDemoViewed(statusId, uid)
      return Result.success(Unit)
    }

    return try {
      val db = firestoreOrNull() ?: error("Firestore unavailable")
      db.collection(COLLECTION).document(statusId)
        .update("viewedBy", FieldValue.arrayUnion(uid))
        .awaitTask()
      patchViewedLocally(statusId, uid)
      Result.success(Unit)
    } catch (e: Exception) {
      logger.e(e) { "markViewed failed" }
      Result.failure(e)
    }
  }

  suspend fun resolveViewerNames(viewerIds: List<String>): Map<String, String> {
    if (viewerIds.isEmpty()) return emptyMap()
    val db = firestoreOrNull() ?: return viewerIds.associateWith { it }
    val names = mutableMapOf<String, String>()
    viewerIds.distinct().chunked(10).forEach { chunk ->
      chunk.forEach { viewerId ->
        runCatching {
          val snap = db.collection(USERS).document(viewerId).get().awaitTask()
          val name = snap.getString("name")?.takeIf { it.isNotBlank() }
            ?: snap.getString("username")?.takeIf { it.isNotBlank() }
            ?: viewerId
          names[viewerId] = name
        }.onFailure {
          names[viewerId] = viewerId
        }
      }
    }
    return names
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
      logger.e(e) { "persistStatus failed" }
      if (ENABLE_DEMO_FALLBACK) {
        useDemoFallback = true
        demoStatuses.add(0, item)
        ensureDemoStatuses()
        emitDemoStatuses()
        Result.success(item)
      } else {
        Result.failure(e)
      }
    }
  }

  private suspend fun uploadMedia(uid: String, statusId: String, uri: Uri): String {
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
      )
    )
    val mine = demoStatuses.filter { it.userId == currentUserId() }
    demoStatuses.clear()
    demoStatuses.addAll(mine)
    demoStatuses.addAll(contacts)
  }

  private fun emitDemoStatuses() {
    val uid = currentUserId().orEmpty()
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
    private const val USERS = "users"

    /** Set to true only for local DEBUG demos; default OFF. */
    private const val ENABLE_DEMO_FALLBACK = false
  }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { cont ->
  addOnSuccessListener { result -> cont.resume(result) }
  addOnFailureListener { error -> cont.resumeWithException(error) }
}
