package com.hearu.app

import app.cash.turbine.test
import com.hearu.app.data.RolePreferences
import com.hearu.app.repository.AuthRepository
import com.hearu.app.ui.auth.AuthState
import com.hearu.app.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mock()
    private val rolePreferences: RolePreferences = mock()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(authRepository.currentUser).thenReturn(null)
        whenever(rolePreferences.activeRoleFlow).thenReturn(flowOf(null))
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
    fun `login transitions through Loading to Authenticated`() = runTest(testDispatcher) {
        viewModel.authState.test {
            assertEquals(AuthState.Idle, awaitItem())
            viewModel.login("test@hearu.app", "Password123!")
            assertEquals(AuthState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AuthState.Authenticated, awaitItem())
        }
    }
}
