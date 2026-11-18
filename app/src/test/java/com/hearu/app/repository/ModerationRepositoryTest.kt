package com.hearu.app.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationRepositoryTest {

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockReportsCollection: CollectionReference
    private lateinit var mockUsersCollection: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var mockSnapshot: DocumentSnapshot
    private lateinit var repository: ModerationRepository

    @Before
    fun setUp() {
        mockFirestore = mock()
        mockReportsCollection = mock()
        mockUsersCollection = mock()
        mockUserDoc = mock()
        mockSnapshot = mock()

        whenever(mockFirestore.collection("reports")).thenReturn(mockReportsCollection)
        whenever(mockFirestore.collection("users")).thenReturn(mockUsersCollection)
        whenever(mockUsersCollection.document(any())).thenReturn(mockUserDoc)

        repository = ModerationRepository(mockFirestore)
    }

    @Test
    fun `reportUser writes exact 7 keys and clamps description to 2000 chars`() = runTest {
        val mockReportDoc: DocumentReference = mock()
        whenever(mockReportsCollection.add(any())).thenReturn(Tasks.forResult(mockReportDoc))

        repository.reportUser(
            reporterId = "u1",
            reportedUserId = "u2",
            sessionId = "sess_1",
            reason = "Harassment",
            description = "a".repeat(2500)
        )

        val captor = argumentCaptor<Map<String, Any>>()
        verify(mockReportsCollection).add(captor.capture())
        val payload = captor.firstValue
        assertEquals(
            setOf("reporterId", "reportedUserId", "sessionId", "reason", "description", "timestamp", "status"),
            payload.keys
        )
        assertEquals("PENDING_REVIEW", payload["status"])
        assertEquals(2000, (payload["description"] as String).length)
        assertNotNull(payload["timestamp"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reportUser rejects self-reporting`() = runTest {
        repository.reportUser("u1", "u1", "s1", "Spam", "desc")
    }

    @Test
    fun `blockUser updates blockedUsers and falls back to merge on NOT_FOUND`() = runTest {
        whenever(mockUserDoc.update(eq("blockedUsers"), any<FieldValue>()))
            .thenReturn(Tasks.forResult(null))
        repository.blockUser("u1", "u2")
        verify(mockUserDoc).update(eq("blockedUsers"), any<FieldValue>())

        whenever(mockUserDoc.update(eq("blockedUsers"), any<FieldValue>()))
            .thenReturn(
                Tasks.forException(
                    FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)
                )
            )
        whenever(mockUserDoc.set(any(), eq(SetOptions.merge()))).thenReturn(Tasks.forResult(null))
        repository.blockUser("u1", "u3")
        verify(mockUserDoc).set(any(), eq(SetOptions.merge()))
    }

    @Test
    fun `blockUser rethrows non-NOT_FOUND FirestoreException and CancellationException`() = runTest {
        whenever(mockUserDoc.update(eq("blockedUsers"), any<FieldValue>()))
            .thenReturn(
                Tasks.forException(
                    FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
                )
            )
        var deniedCaught = false
        try {
            repository.blockUser("u1", "u2")
        } catch (e: FirebaseFirestoreException) {
            deniedCaught = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue(deniedCaught)

        whenever(mockUserDoc.update(eq("blockedUsers"), any<FieldValue>())).thenAnswer {
            throw CancellationException("cancelled")
        }
        var cancelCaught = false
        try {
            repository.blockUser("u1", "u2")
        } catch (_: CancellationException) {
            cancelCaught = true
        }
        assertTrue(cancelCaught)
    }

    @Test
    fun `getBlockedUsers returns parsed list or emptyList when missing`() = runTest {
        assertEquals(emptyList<String>(), repository.getBlockedUsers(""))

        whenever(mockUserDoc.get()).thenReturn(Tasks.forResult(mockSnapshot))
        whenever(mockSnapshot.exists()).thenReturn(false)
        assertEquals(emptyList<String>(), repository.getBlockedUsers("u1"))

        whenever(mockSnapshot.exists()).thenReturn(true)
        whenever(mockSnapshot.get("blockedUsers")).thenReturn(listOf("bad_1", "bad_2", 123))
        assertEquals(listOf("bad_1", "bad_2"), repository.getBlockedUsers("u1"))
    }
}
