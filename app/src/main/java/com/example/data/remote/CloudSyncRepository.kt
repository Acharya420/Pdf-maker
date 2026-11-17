package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?,
        preferredTheme: String,
        preferredLanguage: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().take(80).ifBlank { "PDF Creator" }
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredTheme" to preferredTheme,
                    "preferredLanguage" to preferredLanguage,
                    "pdfsCreatedCount" to 0L,
                    "totalBytesProcessed" to 0L,
                    "status" to "active",
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) payload["email"] = email.take(150)
                if (!photoUrl.isNullOrBlank()) payload["photoUrl"] = photoUrl.take(500)
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!email.isNullOrBlank()) updates["email"] = email.take(150)
                if (!photoUrl.isNullOrBlank()) updates["photoUrl"] = photoUrl.take(500)
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserPreferences(
        preferredTheme: String,
        preferredLanguage: String,
        displayName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val updates = mapOf<String, Any>(
                "displayName" to displayName.trim().take(80).ifBlank { "PDF Creator" },
                "preferredTheme" to preferredTheme,
                "preferredLanguage" to preferredLanguage,
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordPdfActivity(
        recordId: String = UUID.randomUUID().toString(),
        fileName: String,
        operation: String,
        pageCount: Int,
        fileSizeBytes: Long,
        isFavorite: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeId = recordId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val recordRef = db.collection("users").document(uid).collection("pdf_records").document(safeId)
        val userRef = db.collection("users").document(uid)
        try {
            val payload = mapOf<String, Any>(
                "userId" to uid,
                "fileName" to fileName.take(180).ifBlank { "Document.pdf" },
                "operation" to operation,
                "pageCount" to pageCount.coerceAtLeast(1).toLong(),
                "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                "isFavorite" to isFavorite,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            recordRef.set(payload).await()
            userRef.update(
                mapOf(
                    "pdfsCreatedCount" to FieldValue.increment(1L),
                    "totalBytesProcessed" to FieldValue.increment(fileSizeBytes.coerceAtLeast(0L)),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            safeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, recordRef.path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observePdfRecords(userId: String): Flow<List<PdfSyncRecord>> = flow {
        val colRef = db.collection("users").document(userId).collection("pdf_records")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(PdfSyncRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeGlobalConfig(): Flow<AppGlobalConfig> = flow {
        val docRef = db.collection("app_config").document("global")
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(AppGlobalConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: AppGlobalConfig()
                    } else {
                        AppGlobalConfig()
                    }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeActiveAnnouncements(): Flow<List<Announcement>> = flow {
        val query = db.collection("announcements").whereEqualTo("isActive", true)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.LIST, "announcements")
                    }
                    throw error
                }
        )
    }

    fun observeMyFeedback(userId: String): Flow<List<UserFeedback>> = flow {
        val query = db.collection("feedback").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(UserFeedback::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, "feedback")
                    }
                    throw error
                }
        )
    }

    suspend fun submitFeedback(
        category: String,
        subject: String,
        message: String,
        appVersion: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val email = auth.currentUser?.email
        val feedbackId = UUID.randomUUID().toString()
        val docRef = db.collection("feedback").document(feedbackId)
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "category" to category,
            "subject" to subject.trim().take(120),
            "message" to message.trim().take(1000),
            "appVersion" to appVersion.take(32),
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!email.isNullOrBlank()) {
            payload["userEmail"] = email.take(150)
        }
        try {
            docRef.set(payload).await()
            feedbackId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
