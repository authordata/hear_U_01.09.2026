package com.hearu.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.data.RolePreferences
import com.hearu.app.ui.chat.AIChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AIChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val geminiService: GeminiAiService = mock()
    private val preferences: RolePreferences = mock()
    private val auth: FirebaseAuth = mock()
    private val mockUser: FirebaseUser = mock()
    private lateinit var viewModel: AIChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(auth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("test_uid_123")
        runBlocking {
            whenever(preferences.getAiMessagesUsedToday()).thenReturn(0)
            whenever(preferences.tryConsumeAiQuota(any<Int>())).thenReturn(true)
        }
        viewModel = AIChatViewModel(geminiService, preferences, auth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial messages contains AI welcome greeting`() = runTest(testDispatcher) {
        val messages = viewModel.messages.value
        assertEquals(1, messages.size)
        assertEquals("ai_companion", messages[0].senderId)
    }

    @Test
    fun `crisis detection flag activates on distress response`() = runTest(testDispatcher) {
        runBlocking {
            whenever(geminiService.generateEmpatheticResponse(any<String>())).thenReturn(
                AiResult.Success("We are here to help. Please reach out.", isCrisisDetected = true)
            )
        }

        viewModel.sendMessage("I feel like giving up")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.crisisEvent.value)
    }
}
