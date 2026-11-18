package com.hearu.app.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.hearu.app.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class UserRepositoryTest {

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockUsersCollection: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var mockSnapshot: DocumentSnapshot
    private lateinit var repository: UserRepository

    @Before
    fun setUp() {
        mockFirestore = mock()
        mockUsersCollection = mock()
        mockUserDoc = mock()
        mockSnapshot = mock()

        whenever(mockFirestore.collection("users")).thenReturn(mockUsersCollection)
        whenever(mockUsersCollection.document(any())).thenReturn(mockUserDoc)
        whenever(mockUserDoc.get()).thenReturn(Tasks.forResult(mockSnapshot))
        whenever(mockUserDoc.set(any(), eq(SetOptions.merge()))).thenReturn(Tasks.forResult(null))

        repository = UserRepository(mockFirestore)
    }

    @Test
    fun `saveUser writes create payload without rating when doc is missing`() = runTest {
        whenever(mockSnapshot.exists()).thenReturn(false)
        val user = User(
            uid = "u1",
            displayName = "WarmHeart",
            email = "warm@hearu.app",
            role = "giver",
            status = "online",
            emotionTags = listOf("Hope")
        )

        repository.saveUser(user)

        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockUserDoc).set(captor.capture(), eq(SetOptions.merge()))
        val map = captor.firstValue
        assertEquals("u1", map["uid"])
        assertEquals("warm@hearu.app", map["email"])
        assertFalse(map.containsKey("rating"))
    }

    @Test
    fun `saveUser writes only update-allowed keys when doc already exists`() = runTest {
        whenever(mockSnapshot.exists()).thenReturn(true)
        val user = User(
            uid = "u1",
            displayName = "WarmHeart",
            email = "warm@hearu.app",
            role = "both",
            status = "online"
        )

        repository.saveUser(user)

        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockUserDoc).set(captor.capture(), eq(SetOptions.merge()))
        val map = captor.firstValue
        assertFalse("uid cannot be in update affectedKeys", map.containsKey("uid"))
        assertFalse("email cannot be in update affectedKeys", map.containsKey("email"))
        assertFalse("createdAt cannot be in update affectedKeys", map.containsKey("createdAt"))
        assertFalse("rating is forbidden", map.containsKey("rating"))
        assertEquals("WarmHeart", map["displayName"])
        assertEquals("both", map["role"])
    }

    @Test
    fun `getUser returns null for blank uid and deserializes User when present`() = runTest {
        assertNull(repository.getUser("   "))

        val expected = User(uid = "u1", displayName = "Aria", role = "seeker")
        whenever(mockSnapshot.toObject(User::class.java)).thenReturn(expected)
        assertEquals(expected, repository.getUser("u1"))
    }

    @Test
    fun `updateUserStatus calls update and falls back to merge on NOT_FOUND`() = runTest {
        whenever(mockUserDoc.update("status", "online")).thenReturn(Tasks.forResult(null))
        repository.updateUserStatus("u1", "online")
        verify(mockUserDoc).update("status", "online")

        whenever(mockUserDoc.update("status", "offline")).thenReturn(
            Tasks.forException(
                FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)
            )
        )
        repository.updateUserStatus("u1", "offline")
        verify(mockUserDoc).set(any(), eq(SetOptions.merge()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateUserStatus rejects invalid status`() = runTest {
        repository.updateUserStatus("u1", "away")
    }

    @Test
    fun `updateDisplayName trims and updates or merges on NOT_FOUND`() = runTest {
        whenever(mockUserDoc.update("displayName", "KindSoul")).thenReturn(Tasks.forResult(null))
        repository.updateDisplayName("u1", "  KindSoul  ")
        verify(mockUserDoc).update("displayName", "KindSoul")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateDisplayName rejects blank names`() = runTest {
        repository.updateDisplayName("u1", "   ")
    }

    @Test
    fun `updateRole accepts valid roles and rejects empty role`() = runTest {
        whenever(mockUserDoc.update("role", "seeker")).thenReturn(Tasks.forResult(null))
        repository.updateRole("u1", "seeker")
        verify(mockUserDoc).update("role", "seeker")

        var rejected = false
        try {
            repository.updateRole("u1", "")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `updateFcmToken falls back to merge on NOT_FOUND and rejects oversized token`() = runTest {
        whenever(mockUserDoc.update(any<Map<String, Any>>())).thenReturn(
            Tasks.forException(
                FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)
            )
        )
        repository.updateFcmToken("u1", "tok_123")
        verify(mockUserDoc).set(any(), eq(SetOptions.merge()))

        var rejected = false
        try {
            repository.updateFcmToken("u1", "x".repeat(513))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `CancellationException is rethrown in repository suspend calls`() = runTest {
        whenever(mockUserDoc.get()).thenAnswer { throw CancellationException("cancelled") }
        var caught = false
        try {
            repository.getUser("u1")
        } catch (_: CancellationException) {
            caught = true
        }
        assertTrue(caught)
    }
}
