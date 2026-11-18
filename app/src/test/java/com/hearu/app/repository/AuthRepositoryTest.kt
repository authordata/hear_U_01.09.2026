package com.hearu.app.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult as FirebaseAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockUsersCollection: CollectionReference
    private lateinit var mockPublicListenersCollection: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var mockPublicDoc: DocumentReference
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockAuthResult: FirebaseAuthResult
    private lateinit var mockSnapshot: DocumentSnapshot
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        mockAuth = mock()
        mockFirestore = mock()
        mockUsersCollection = mock()
        mockPublicListenersCollection = mock()
        mockUserDoc = mock()
        mockPublicDoc = mock()
        mockUser = mock()
        mockAuthResult = mock()
        mockSnapshot = mock()

        whenever(mockFirestore.collection("users")).thenReturn(mockUsersCollection)
        whenever(mockFirestore.collection("publicListeners")).thenReturn(mockPublicListenersCollection)
        whenever(mockUsersCollection.document(any())).thenReturn(mockUserDoc)
        whenever(mockPublicListenersCollection.document(any())).thenReturn(mockPublicDoc)

        whenever(mockAuthResult.user).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_123")
        whenever(mockUser.email).thenReturn("user@hearu.app")
        whenever(mockUser.displayName).thenReturn("KindListener")

        whenever(mockUserDoc.get()).thenReturn(Tasks.forResult(mockSnapshot))
        whenever(mockUserDoc.set(any(), eq(SetOptions.merge()))).thenReturn(Tasks.forResult(null))
        whenever(mockUserDoc.delete()).thenReturn(Tasks.forResult(null))
        whenever(mockPublicDoc.delete()).thenReturn(Tasks.forResult(null))

        repository = AuthRepository(mockAuth, mockFirestore)
    }

    @Test
    fun `signUpWithEmail trims email and creates user document without rating`() = runTest {
        whenever(mockSnapshot.exists()).thenReturn(false)
        whenever(mockAuth.createUserWithEmailAndPassword("user@hearu.app", "Secret123!"))
            .thenReturn(Tasks.forResult(mockAuthResult))

        val result = repository.signUpWithEmail("  user@hearu.app  ", "Secret123!")

        assertEquals(AuthResult.Success(mockUser), result)
        val payloadCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockUserDoc).set(payloadCaptor.capture(), eq(SetOptions.merge()))
        val payload = payloadCaptor.firstValue
        assertEquals("uid_123", payload["uid"])
        assertEquals("user@hearu.app", payload["email"])
        assertEquals("", payload["role"])
        assertEquals("offline", payload["status"])
        assertFalse("Must never write rating field", payload.containsKey("rating"))
    }

    @Test
    fun `signInAnonymously creates user doc without email or rating`() = runTest {
        whenever(mockUser.email).thenReturn(null)
        whenever(mockUser.displayName).thenReturn(null)
        whenever(mockSnapshot.exists()).thenReturn(false)
        whenever(mockAuth.signInAnonymously()).thenReturn(Tasks.forResult(mockAuthResult))

        val result = repository.signInAnonymously()

        assertEquals(AuthResult.Success(mockUser), result)
        val payloadCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockUserDoc).set(payloadCaptor.capture(), eq(SetOptions.merge()))
        val payload = payloadCaptor.firstValue
        assertEquals("uid_123", payload["uid"])
        assertFalse("Anonymous user doc must omit email when null/blank", payload.containsKey("email"))
        assertFalse("Must never write rating field", payload.containsKey("rating"))
    }

    @Test
    fun `signInWithEmail creates doc when missing and skips overwrite when existing`() = runTest {
        whenever(mockAuth.signInWithEmailAndPassword("user@hearu.app", "Secret123!"))
            .thenReturn(Tasks.forResult(mockAuthResult))

        whenever(mockSnapshot.exists()).thenReturn(true)
        val existingResult = repository.signInWithEmail(" user@hearu.app ", "Secret123!")
        assertEquals(AuthResult.Success(mockUser), existingResult)
        verify(mockUserDoc, never()).set(any(), any())

        whenever(mockSnapshot.exists()).thenReturn(false)
        val missingResult = repository.signInWithEmail("user@hearu.app", "Secret123!")
        assertEquals(AuthResult.Success(mockUser), missingResult)
        verify(mockUserDoc).set(any(), eq(SetOptions.merge()))
    }

    @Test
    fun `sendPasswordResetEmail trims email and validates blank input`() = runTest {
        val blankResult = repository.sendPasswordResetEmail("   ")
        assertTrue(blankResult.isFailure)
        verify(mockAuth, never()).sendPasswordResetEmail(any())

        whenever(mockAuth.sendPasswordResetEmail("reset@hearu.app")).thenReturn(Tasks.forResult(null))
        val successResult = repository.sendPasswordResetEmail("  reset@hearu.app ")
        assertTrue(successResult.isSuccess)
        verify(mockAuth).sendPasswordResetEmail("reset@hearu.app")
    }

    @Test
    fun `deleteAccount deletes Firestore docs before FirebaseUser delete and signs out`() = runTest {
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.delete()).thenReturn(Tasks.forResult(null))
        whenever(mockPublicDoc.delete()).thenReturn(
            Tasks.forException(
                FirebaseFirestoreException("Not found", FirebaseFirestoreException.Code.NOT_FOUND)
            )
        )

        val result = repository.deleteAccount()

        assertTrue(result.isSuccess)
        val order = inOrder(mockUserDoc, mockPublicDoc, mockUser, mockAuth)
        order.verify(mockUserDoc).delete()
        order.verify(mockPublicDoc).delete()
        order.verify(mockUser).delete()
        order.verify(mockAuth).signOut()
    }

    @Test
    fun `deleteAccount returns friendly message when recent login is required`() = runTest {
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        val recentLoginEx: FirebaseAuthRecentLoginRequiredException = mock()
        whenever(mockUser.delete()).thenReturn(Tasks.forException(recentLoginEx))

        val result = repository.deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("sign in again", ignoreCase = true) == true
        )
    }

    @Test
    fun `CancellationException is rethrown across suspend methods`() = runTest {
        whenever(mockAuth.signInWithEmailAndPassword(any(), any())).thenAnswer {
            throw CancellationException("cancelled")
        }
        whenever(mockAuth.createUserWithEmailAndPassword(any(), any())).thenAnswer {
            throw CancellationException("cancelled")
        }
        whenever(mockAuth.signInAnonymously()).thenAnswer {
            throw CancellationException("cancelled")
        }
        whenever(mockAuth.sendPasswordResetEmail(any())).thenAnswer {
            throw CancellationException("cancelled")
        }
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUserDoc.delete()).thenAnswer {
            throw CancellationException("cancelled")
        }

        val operations: List<suspend () -> Unit> = listOf(
            { repository.signInWithEmail("a@b.com", "12345678") },
            { repository.signUpWithEmail("a@b.com", "12345678") },
            { repository.signInAnonymously() },
            { repository.sendPasswordResetEmail("a@b.com") },
            { repository.deleteAccount() }
        )

        operations.forEach { op ->
            var caught: CancellationException? = null
            try {
                op()
            } catch (e: CancellationException) {
                caught = e
            }
            assertTrue("Expected CancellationException to be rethrown", caught != null)
        }
    }
}
