package com.hearu.app

import app.cash.turbine.test
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.data.RolePreferences
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.AuthResult
import com.hearu.app.ui.auth.AuthState
import com.hearu.app.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mock()
    private val rolePreferences: RolePreferences = mock()
    private val mockUser: FirebaseUser = mock()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(authRepository.currentUser).thenReturn(null)
        whenever(rolePreferences.activeRoleFlow).thenReturn(flowOf(null))
        whenever(rolePreferences.onboardingCompletedFlow).thenReturn(flowOf(false))
        whenever(rolePreferences.isOnboardingCompletedFlow).thenReturn(flowOf(false))
        viewModel = AuthViewModel(authRepository, rolePreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle when not signed in`() = runTest(testDispatcher) {
        assertEquals(AuthState.Idle, viewModel.authState.value)
    }

    @Test
    fun `login transitions through Loading to Authenticated on valid credentials`() = runTest(testDispatcher) {
        runBlocking {
            whenever(authRepository.signInWithEmail(any<String>(), any<String>()))
                .thenReturn(AuthResult.Success(mockUser))
        }

        viewModel.authState.test {
            assertEquals(AuthState.Idle, awaitItem())
            viewModel.login("test@hearu.app", "Password123!")
            testDispatcher.scheduler.runCurrent()
            assertEquals(AuthState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AuthState.Authenticated, awaitItem())
        }
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
}
