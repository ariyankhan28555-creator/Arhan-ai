package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AssistantLanguage
import com.example.model.ChatMessageEntity
import com.example.model.MessageSender
import com.example.model.NoteItem
import com.example.model.ReminderItem
import com.example.model.TodoItem
import com.example.model.TodoPriority
import com.example.model.UserPreference
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUser(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        return uid
    }

    // --- NOTES ---
    fun observeNotes(userId: String): Flow<List<NoteItem>> =
        db.collection("users").document(userId).collection("notes")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(NoteItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun addNote(
        userId: String,
        title: String,
        content: String,
        tag: String = "General"
    ): Result<String> {
        val cleanTitle = title.trim().take(120)
        val cleanContent = content.trim().take(5000)
        val cleanTag = tag.trim().ifBlank { "General" }.take(32)
        val docRef = db.collection("users").document(userId).collection("notes").document()
        return try {
            val uid = requireSignedInUser(userId)
            require(cleanTitle.isNotEmpty()) { "Note title cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "content" to cleanContent,
                "tag" to cleanTag,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        userId: String,
        noteId: String,
        title: String,
        content: String,
        tag: String
    ): Result<Unit> {
        val cleanTitle = title.trim().take(120)
        val cleanContent = content.trim().take(5000)
        val cleanTag = tag.trim().ifBlank { "General" }.take(32)
        val docPath = "users/$userId/notes/$noteId"
        return try {
            requireSignedInUser(userId)
            require(noteId.isNotBlank() && cleanTitle.isNotEmpty()) { "Invalid note data." }
            db.collection("users").document(userId).collection("notes").document(noteId)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "content" to cleanContent,
                        "tag" to cleanTag,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        val docPath = "users/$userId/notes/$noteId"
        return try {
            requireSignedInUser(userId)
            require(noteId.isNotBlank())
            db.collection("users").document(userId).collection("notes").document(noteId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- TODOS ---
    fun observeTodos(userId: String): Flow<List<TodoItem>> =
        db.collection("users").document(userId).collection("todos")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(TodoItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun addTodo(
        userId: String,
        title: String,
        priority: TodoPriority = TodoPriority.NORMAL
    ): Result<String> {
        val cleanTitle = title.trim().take(160)
        val docRef = db.collection("users").document(userId).collection("todos").document()
        return try {
            val uid = requireSignedInUser(userId)
            require(cleanTitle.isNotEmpty()) { "Task title cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "completed" to false,
                "priority" to priority.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleTodo(userId: String, todoId: String, completed: Boolean): Result<Unit> {
        val docPath = "users/$userId/todos/$todoId"
        return try {
            requireSignedInUser(userId)
            require(todoId.isNotBlank())
            db.collection("users").document(userId).collection("todos").document(todoId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTodo(userId: String, todoId: String): Result<Unit> {
        val docPath = "users/$userId/todos/$todoId"
        return try {
            requireSignedInUser(userId)
            require(todoId.isNotBlank())
            db.collection("users").document(userId).collection("todos").document(todoId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- REMINDERS ---
    fun observeReminders(userId: String): Flow<List<ReminderItem>> =
        db.collection("users").document(userId).collection("reminders")
            .orderBy("dueTimeMillis", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(ReminderItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun addReminder(
        userId: String,
        title: String,
        dueTimeMillis: Long,
        category: String = "Personal"
    ): Result<String> {
        val cleanTitle = title.trim().take(160)
        val cleanCategory = category.trim().ifBlank { "Personal" }.take(40)
        val safeDue = dueTimeMillis.coerceAtLeast(System.currentTimeMillis() + 60_000L)
        val docRef = db.collection("users").document(userId).collection("reminders").document()
        return try {
            val uid = requireSignedInUser(userId)
            require(cleanTitle.isNotEmpty()) { "Reminder title cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "dueTimeMillis" to safeDue,
                "active" to true,
                "category" to cleanCategory,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleReminder(userId: String, reminderId: String, active: Boolean): Result<Unit> {
        val docPath = "users/$userId/reminders/$reminderId"
        return try {
            requireSignedInUser(userId)
            require(reminderId.isNotBlank())
            db.collection("users").document(userId).collection("reminders").document(reminderId)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> {
        val docPath = "users/$userId/reminders/$reminderId"
        return try {
            requireSignedInUser(userId)
            require(reminderId.isNotBlank())
            db.collection("users").document(userId).collection("reminders").document(reminderId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- USER-APPROVED PREFERENCES ---
    fun observePreferences(userId: String): Flow<List<UserPreference>> =
        db.collection("users").document(userId).collection("preferences")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap ->
                snap.toObjects(UserPreference::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun saveApprovedPreference(
        userId: String,
        prefKey: String,
        prefValue: String
    ): Result<String> {
        val cleanKey = prefKey.trim().take(80)
        val cleanVal = prefValue.trim().take(300)
        val docRef = db.collection("users").document(userId).collection("preferences").document()
        return try {
            val uid = requireSignedInUser(userId)
            require(cleanKey.isNotEmpty() && cleanVal.isNotEmpty()) { "Preference cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "prefKey" to cleanKey,
                "prefValue" to cleanVal,
                "approvedByUser" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePreference(userId: String, prefId: String): Result<Unit> {
        val docPath = "users/$userId/preferences/$prefId"
        return try {
            requireSignedInUser(userId)
            require(prefId.isNotBlank())
            db.collection("users").document(userId).collection("preferences").document(prefId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- CONVERSATION HISTORY ---
    fun observeMessages(userId: String): Flow<List<ChatMessageEntity>> =
        db.collection("users").document(userId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(60)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessageEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun addMessage(
        userId: String,
        sender: MessageSender,
        text: String,
        language: AssistantLanguage,
        actionTag: String = ""
    ): Result<String> {
        val cleanText = text.trim().take(4000)
        val cleanAction = actionTag.trim().take(80)
        val docRef = db.collection("users").document(userId).collection("messages").document()
        return try {
            val uid = requireSignedInUser(userId)
            require(cleanText.isNotEmpty()) { "Message text cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "sender" to sender.name,
                "text" to cleanText,
                "language" to language.code,
                "actionTag" to cleanAction,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearConversation(userId: String): Result<Unit> {
        val path = "users/$userId/messages"
        return try {
            requireSignedInUser(userId)
            val snap = db.collection("users").document(userId).collection("messages")
                .get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
