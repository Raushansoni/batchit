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

package io.getstream.whatsappclone.chats.friends

import android.content.Context
import android.provider.ContactsContract
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BatchItUser(
  val uid: String,
  val username: String,
  val name: String,
  val email: String = "",
  val image: String = ""
)

data class DeviceContact(
  val id: String,
  val name: String,
  val emails: List<String>,
  val phones: List<String>
)

data class ContactMatch(
  val contact: DeviceContact,
  val batchItUser: BatchItUser?
)

@Singleton
class FriendsRepository @Inject constructor(
  @ApplicationContext private val context: Context
) {

  private val db get() = Firebase.firestore

  suspend fun resolveUsername(rawUsername: String): Result<BatchItUser> = runCatching {
    val username = normalizeUsername(rawUsername)
    require(USERNAME_REGEX.matches(username)) {
      "Username must be 3–20 characters: letters, numbers, underscore"
    }
    val snap = db.collection(USERNAMES).document(username).get().awaitTask()
    if (!snap.exists()) error("No BatchIt user with username @$username")
    val uid = snap.getString("uid") ?: error("Invalid username record")
    val me = Firebase.auth.currentUser?.uid
    if (uid == me) error("That's your own username")
    val userSnap = db.collection(USERS).document(uid).get().awaitTask()
    BatchItUser(
      uid = uid,
      username = userSnap.getString("username") ?: username,
      name = userSnap.getString("name") ?: username,
      email = userSnap.getString("email").orEmpty(),
      image = userSnap.getString("image").orEmpty()
    )
  }

  suspend fun addFriend(user: BatchItUser): Result<Unit> = runCatching {
    val me = Firebase.auth.currentUser?.uid ?: error("Not signed in")
    require(user.uid != me) { "Can't add yourself" }
    val myUsername = db.collection(USERS).document(me).get().awaitTask()
      .getString("username")
      .orEmpty()

    db.collection(USERS).document(me).collection(FRIENDS).document(user.uid)
      .set(
        mapOf(
          "uid" to user.uid,
          "username" to user.username,
          "name" to user.name,
          "image" to user.image,
          "addedAt" to FieldValue.serverTimestamp()
        ),
        SetOptions.merge()
      )
      .awaitTask()

    // Mirror so the other user sees you too (best-effort).
    db.collection(USERS).document(user.uid).collection(FRIENDS).document(me)
      .set(
        mapOf(
          "uid" to me,
          "username" to myUsername,
          "name" to myUsername.ifBlank { "BatchIt User" },
          "image" to (Firebase.auth.currentUser?.photoUrl?.toString().orEmpty()),
          "addedAt" to FieldValue.serverTimestamp()
        ),
        SetOptions.merge()
      )
      .awaitTask()
  }

  suspend fun listFriends(): Result<List<BatchItUser>> = runCatching {
    val me = Firebase.auth.currentUser?.uid ?: error("Not signed in")
    db.collection(USERS).document(me).collection(FRIENDS)
      .get()
      .awaitTask()
      .documents
      .mapNotNull { doc ->
        val uid = doc.getString("uid") ?: doc.id
        BatchItUser(
          uid = uid,
          username = doc.getString("username").orEmpty(),
          name = doc.getString("name").orEmpty().ifBlank {
            doc.getString("username").orEmpty()
          },
          image = doc.getString("image").orEmpty()
        )
      }
      .sortedBy { it.username.lowercase() }
  }

  suspend fun loadContactMatches(): Result<List<ContactMatch>> = withContext(Dispatchers.IO) {
    runCatching {
      val contacts = readDeviceContacts()
      val emails = contacts.flatMap { it.emails }.distinct()
      val matchedByEmail = lookupUsersByEmails(emails)
      contacts.map { contact ->
        val match = contact.emails.firstNotNullOfOrNull { matchedByEmail[it] }
        ContactMatch(contact = contact, batchItUser = match)
      }.sortedWith(
        compareByDescending<ContactMatch> { it.batchItUser != null }
          .thenBy { it.contact.name.lowercase() }
      )
    }
  }

  fun myUsername(): String {
    return Firebase.auth.currentUser?.displayName
      ?.takeIf { it.isNotBlank() }
      .orEmpty()
  }

  fun inviteMessage(myUsername: String): String {
    val handle = myUsername.ifBlank { "me" }
    return "Join me on BatchIt! My username is @$handle — sign in with Google and add me to chat."
  }

  private suspend fun lookupUsersByEmails(emails: List<String>): Map<String, BatchItUser> {
    if (emails.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, BatchItUser>()
    emails.chunked(10).forEach { chunk ->
      val snap = db.collection(USERS)
        .whereIn("email", chunk)
        .get()
        .awaitTask()
      snap.documents.forEach { doc ->
        val email = doc.getString("email")?.lowercase().orEmpty()
        if (email.isNotBlank()) {
          result[email] = BatchItUser(
            uid = doc.getString("uid") ?: doc.id,
            username = doc.getString("username").orEmpty(),
            name = doc.getString("name").orEmpty(),
            email = email,
            image = doc.getString("image").orEmpty()
          )
        }
      }
    }
    return result
  }

  private fun readDeviceContacts(): List<DeviceContact> {
    val resolver = context.contentResolver
    val contacts = linkedMapOf<String, MutableContact>()

    resolver.query(
      ContactsContract.CommonDataKinds.Email.CONTENT_URI,
      arrayOf(
        ContactsContract.CommonDataKinds.Email.CONTACT_ID,
        ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Email.ADDRESS
      ),
      null,
      null,
      ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY + " ASC"
    )?.use { cursor ->
      val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
      val nameIdx = cursor.getColumnIndex(
        ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY
      )
      val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
      while (cursor.moveToNext()) {
        val id = cursor.getString(idIdx) ?: continue
        val name = cursor.getString(nameIdx).orEmpty().ifBlank { "Unknown" }
        val email = cursor.getString(emailIdx)?.trim()?.lowercase().orEmpty()
        val entry = contacts.getOrPut(id) { MutableContact(id, name) }
        if (email.isNotBlank() && email.contains("@")) entry.emails.add(email)
      }
    }

    resolver.query(
      ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
      arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Phone.NUMBER
      ),
      null,
      null,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
    )?.use { cursor ->
      val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
      val nameIdx = cursor.getColumnIndex(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
      )
      val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
      while (cursor.moveToNext()) {
        val id = cursor.getString(idIdx) ?: continue
        val name = cursor.getString(nameIdx).orEmpty().ifBlank { "Unknown" }
        val phone = cursor.getString(phoneIdx)?.trim().orEmpty()
        val entry = contacts.getOrPut(id) { MutableContact(id, name) }
        if (phone.isNotBlank()) entry.phones.add(phone)
      }
    }

    return contacts.values.map {
      DeviceContact(
        id = it.id,
        name = it.name,
        emails = it.emails.distinct(),
        phones = it.phones.distinct()
      )
    }
  }

  private data class MutableContact(
    val id: String,
    val name: String,
    val emails: MutableSet<String> = linkedSetOf(),
    val phones: MutableSet<String> = linkedSetOf()
  )

  companion object {
    private const val USERS = "users"
    private const val USERNAMES = "usernames"
    private const val FRIENDS = "friends"
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,20}$")

    fun normalizeUsername(raw: String): String = raw.trim().lowercase().removePrefix("@")
  }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCoroutine { continuation ->
  addOnSuccessListener { continuation.resume(it) }
  addOnFailureListener { continuation.resumeWithException(it) }
}
