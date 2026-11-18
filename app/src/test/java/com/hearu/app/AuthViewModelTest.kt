package com.hearu.app

import android.content.Context
import app.cash.turbine.test
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.AuthResult
import com.hearu.app.ui.auth.AppStartupState
import com.hearu.app.ui.auth.AuthState
import com.hearu.app.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mock()
    private val rolePreferences: RolePreferences = mock()
    private val messageDao: MessageDao = mock()
    private val mockContext: Context = mock()
    private val mockUser: FirebaseUser = mock()
    private lateinit var tempCacheDir: File
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempCacheDir = createTempDirectory("auth_vm_test_cache").toFile()
        whenever(mockContext.cacheDir).thenReturn(tempCacheDir)
        whenever(authRepository.currentUser).thenReturn(null)
        whenever(rolePreferences.activeRoleFlow).thenReturn(flowOf(null))
        whenever(rolePreferences.onboardingCompletedFlow).thenReturn(flowOf(false))
        whenever(rolePreferences.isOnboardingCompletedFlow).thenReturn(flowOf(false))
        whenever(rolePreferences.isBiometricEnabledFlow).thenReturn(flowOf(false))
        whenever(rolePreferences.isDarkThemeFlow).thenReturn(flowOf(null))
        whenever(rolePreferences.displayNameFlow).thenReturn(flowOf("KindSoul"))
        viewModel = AuthViewModel(authRepository, rolePreferences, messageDao, mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun `initial state is Idle when not signed in`() = runTest(testDispatcher) {
        assertEquals(AuthState.Idle, viewModel.authState.value)
    }

    @Test
    fun `startupState transitions from Loading to Ready with DataStore values`() = runTest(testDispatcher) {
        whenever(rolePreferences.isOnboardingCompletedFlow).thenReturn(flowOf(true))
        whenever(rolePreferences.activeRoleFlow).thenReturn(flowOf("seeker"))
        whenever(rolePreferences.isBiometricEnabledFlow).thenReturn(flowOf(true))
        whenever(rolePreferences.isDarkThemeFlow).thenReturn(flowOf(true))
        whenever(authRepository.currentUser).thenReturn(mockUser)

        val vm = AuthViewModel(authRepository, rolePreferences)
        val collectJob = launch { vm.startupState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.startupState.value
        assertTrue(state is AppStartupState.Ready)
        val ready = state as AppStartupState.Ready
        assertTrue(ready.isOnboardingCompleted)
        assertEquals("seeker", ready.activeRole)
        assertTrue(ready.isBiometricEnabled)
        assertEquals(true, ready.isDarkTheme)
        assertTrue(ready.isAuthenticated)
        collectJob.cancel()
    }

    @Test
    fun `login trims whitespace and transitions through Loading to Authenticated`() = runTest(testDispatcher) {
        runBlocking {
            whenever(authRepository.signInWithEmail(eq("test@hearu.app"), eq("Password123!")))
                .thenReturn(AuthResult.Success(mockUser))
        }

        viewModel.authState.test {
            assertEquals(AuthState.Idle, awaitItem())
            viewModel.login("  test@hearu.app  ", "Password123!")
            testDispatcher.scheduler.runCurrent()
            assertEquals(AuthState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AuthState.Authenticated, awaitItem())
        }
    }

    @Test
    fun `login maps FirebaseAuthInvalidCredentialsException to friendly message`() = runTest(testDispatcher) {
        val firebaseEx = mock<FirebaseAuthInvalidCredentialsException>()
        runBlocking {
            whenever(authRepository.signInWithEmail(any(), any()))
                .thenReturn(AuthResult.Failure(firebaseEx))
        }

        viewModel.login("test@hearu.app", "WrongPass123!")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue(state is AuthState.Error)
        assertTrue((state as AuthState.Error).message.contains("Incorrect email or password"))
    }

    @Test
    fun `register transitions through Loading to Authenticated on valid credentials`() = runTest(testDispatcher) {
        runBlocking {
            whenever(authRepository.signUpWithEmail(any<String>(), any<String>()))
                .thenReturn(AuthResult.Success(mockUser))
        }

        viewModel.authState.test {
            assertEquals(AuthState.Idle, awaitItem())
            viewModel.register("newuser@hearu.app", "Password123!")
            testDispatcher.scheduler.runCurrent()
            assertEquals(AuthState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AuthState.Authenticated, awaitItem())
        }
    }

    @Test
    fun `register fails immediately with short password`() = runTest(testDispatcher) {
        viewModel.register("valid@hearu.app", "short")
        val state = viewModel.authState.value
        assert(state is AuthState.Error && state.message.contains("at least 8 characters"))
    }

    @Test
    fun `signInAnonymously transitions through Loading to Authenticated on success`() = runTest(testDispatcher) {
        runBlocking {
            whenever(authRepository.signInAnonymously())
                .thenReturn(AuthResult.Success(mockUser))
        }

        viewModel.authState.test {
            assertEquals(AuthState.Idle, awaitItem())
            viewModel.signInAnonymously()
            testDispatcher.scheduler.runCurrent()
            assertEquals(AuthState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AuthState.Authenticated, awaitItem())
        }
    }

    @Test
    fun `sendPasswordReset validates email and invokes repository`() = runTest(testDispatcher) {
        runBlocking {
            whenever(authRepository.sendPasswordResetEmail("user@hearu.app"))
                .thenReturn(Result.success(Unit))
        }

        var errorMsg: String? = null
        viewModel.sendPasswordReset("bad-email", onError = { errorMsg = it })
        assertEquals("Please enter a valid email address.", errorMsg)

        var success = false
        viewModel.sendPasswordReset("  user@hearu.app ", onSuccess = { success = true })
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(success)
    }

    @Test
    fun `signOut resets authState and clears session data, Room cache, and voice temp files`() = runTest(testDispatcher) {
        val voiceFile = File(tempCacheDir, "voice_note_123.m4a").apply { writeText("audio") }
        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthState.Idle, viewModel.authState.value)
        verify(authRepository).signOut()
        verify(rolePreferences).clearSessionData()
        verify(messageDao).clearAll()
        assertFalse(voiceFile.exists())
    }

    @Test
    fun `deleteAccount clears all local data on success and reports recent-login error`() = runTest(testDispatcher) {
        val mockFbAuth: FirebaseAuth = mock()
        val mockFirestore: FirebaseFirestore = mock()
        val mockUsersCol: CollectionReference = mock()
        val mockPublicCol: CollectionReference = mock()
        val mockUserDoc: DocumentReference = mock()
        val mockPublicDoc: DocumentReference = mock()
        whenever(mockFbAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_del_1")
        whenever(mockFirestore.collection("users")).thenReturn(mockUsersCol)
        whenever(mockFirestore.collection("publicListeners")).thenReturn(mockPublicCol)
        whenever(mockUsersCol.document("uid_del_1")).thenReturn(mockUserDoc)
        whenever(mockPublicCol.document("uid_del_1")).thenReturn(mockPublicDoc)
        whenever(mockUserDoc.delete()).thenReturn(Tasks.forResult(null))
        whenever(mockPublicDoc.delete()).thenReturn(
            Tasks.forException(
                FirebaseFirestoreException("Missing", FirebaseFirestoreException.Code.NOT_FOUND)
            )
        )
        whenever(mockUser.delete()).thenReturn(Tasks.forResult(null))

        val realRepo = AuthRepository(mockFbAuth, mockFirestore)
        val vm = AuthViewModel(realRepo, rolePreferences, messageDao, mockContext)

        var deleted = false
        vm.deleteAccount(onSuccess = { deleted = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(deleted)
        assertEquals(AuthState.Idle, vm.authState.value)
        verify(rolePreferences).clearAll()
        verify(messageDao).clearAll()

        val recentLoginEx = mock<FirebaseAuthRecentLoginRequiredException>()
        whenever(mockUser.delete()).thenReturn(Tasks.forException(recentLoginEx))
        var capturedError = ""
        vm.deleteAccount(onError = { capturedError = it })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(capturedError.contains("sign out and sign back in"))
    }

    @Test
    fun `deleteAccount treats real NullPointerException from repository as error without wiping local data`() = runTest(testDispatcher) {
        whenever(authRepository.currentUser).thenReturn(mockUser)
        runBlocking {
            whenever(authRepository.deleteAccount()).thenAnswer {
                throw NullPointerException("repo failure")
            }
        }
        val vm = AuthViewModel(authRepository, rolePreferences, messageDao, mockContext)
        assertEquals(AuthState.Authenticated, vm.authState.value)

        var successCalled = false
        var errorMessage: String? = null
        vm.deleteAccount(
            onSuccess = { successCalled = true },
            onError = { errorMessage = it }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals("repo failure", errorMessage)
        assertEquals(AuthState.Authenticated, vm.authState.value)
        verify(rolePreferences, never()).clearAll()
        verify(messageDao, never()).clearAll()
    }

    @Test
    fun `sendPasswordReset treats real NullPointerException from repository as error`() = runTest(testDispatcher) {
        runBlocking {
            whenever(authRepository.sendPasswordResetEmail("user@hearu.app")).thenAnswer {
                throw NullPointerException("repo failure")
            }
        }

        var successCalled = false
        var errorMessage: String? = null
        viewModel.sendPasswordReset(
            email = "user@hearu.app",
            onSuccess = { successCalled = true },
            onError = { errorMessage = it }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals("repo failure", errorMessage)
    }
}
