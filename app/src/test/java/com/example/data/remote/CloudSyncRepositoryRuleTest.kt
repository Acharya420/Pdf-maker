package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfileAndRecordPdfActivity_owner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore, auth)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Alice", ALICE_EMAIL, null, "dark", "en")
        }
        assertTrue("Expected ensureUserProfile to succeed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)

        val recId = "rec_${UUID.randomUUID()}"
        val recRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordPdfActivity(
                recordId = recId,
                fileName = "Contract.pdf",
                operation = "image_to_pdf",
                pageCount = 2,
                fileSizeBytes = 128000L,
                isFavorite = true
            )
        }
        assertTrue("Expected recordPdfActivity to succeed: ${recRes.exceptionOrNull()}", recRes.isSuccess)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePdfRecords(uid).first { list -> list.any { it.id == recId } }
        }
        assertTrue(emitted.any { it.fileName == "Contract.pdf" && it.pageCount == 2L })
    }

    @Test
    fun submitFeedback_authenticatedUser_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore, auth)
        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitFeedback("feature", "Batch OCR", "Would love OCR later!", "1.0")
        }
        assertTrue("Expected submitFeedback to succeed: ${res.exceptionOrNull()}", res.isSuccess)
    }

    @Test
    fun observePdfRecords_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", ALICE_EMAIL, null, "light", "en").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observePdfRecords(aliceUid).first()
            }
            fail("Expected cross-user observePdfRecords to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr!!.code)
        }
    }

    @Test
    fun observePdfRecords_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePdfRecords("some_uid").first()
            }
            fail("Expected unauthenticated observePdfRecords to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsErr)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pdf@test.com"
        const val BOB_EMAIL = "bob_pdf@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
