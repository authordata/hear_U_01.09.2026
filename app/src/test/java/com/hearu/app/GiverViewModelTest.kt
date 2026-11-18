package com.hearu.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.model.ChatSession
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.UserRepository
import com.hearu.app.ui.home.GiverViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GiverViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userRepository: UserRepository = mock()
    private val chatRepository: ChatRepository = mock()
    private val auth: FirebaseAuth = mock()
    private val mockUser: FirebaseUser = mock()
    private lateinit var viewModel: GiverViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(auth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("giver_uid_1")
        viewModel = GiverViewModel(userRepository, chatRepository, auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setAvailability true updates status online and collects active giver session`() = runTest(testDispatcher) {
        val sessionFlow = MutableStateFlow<ChatSession?>(
            ChatSession(sessionId = "sess_101", seekerId = "seeker_99", giverId = "giver_uid_1", status = "active")
        )
        whenever(chatRepository.observeActiveGiverSession("giver_uid_1")).thenReturn(sessionFlow)

        viewModel.setAvailability(true)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(userRepository).updateUserStatus("giver_uid_1", "online")
        assertEquals("sess_101", viewModel.activeSession.value?.sessionId)
        assertEquals("seeker_99", viewModel.activeSession.value?.seekerId)
        assertNull(viewModel.syncError.value)
    }

    @Test
    fun `setAvailability false updates status offline and clears active session`() = runTest(testDispatcher) {
        val sessionFlow = MutableStateFlow<ChatSession?>(
            ChatSession(sessionId = "sess_101", seekerId = "seeker_99", giverId = "giver_uid_1", status = "active")
        )
        whenever(chatRepository.observeActiveGiverSession("giver_uid_1")).thenReturn(sessionFlow)

        viewModel.setAvailability(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.activeSession.value)

        viewModel.setAvailability(false)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(userRepository).updateUserStatus("giver_uid_1", "offline")
        assertNull(viewModel.activeSession.value)
    }

    @Test
    fun `setAvailability surfaces syncError when updateUserStatus fails`() = runTest(testDispatcher) {
        whenever(chatRepository.observeActiveGiverSession("giver_uid_1"))
            .thenReturn(MutableStateFlow(null))
        whenever(userRepository.updateUserStatus("giver_uid_1", "online"))
            .thenThrow(RuntimeException("Network down"))

        viewModel.setAvailability(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.syncError.value)
    }
}
