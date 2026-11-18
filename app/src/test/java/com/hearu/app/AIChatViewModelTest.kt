package com.hearu.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.data.RolePreferences
import com.hearu.app.model.Message
import com.hearu.app.ui.chat.AIChatViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.check
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
    fun `initial messages contains AI welcome greeting with SENDER_AI`() = runTest(testDispatcher) {
        val messages = viewModel.messages.value
        assertEquals(1, messages.size)
        assertEquals(Message.SENDER_AI, messages[0].senderId)
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

    @Test
    fun `crisis flag on AiResult Error triggers crisis dialog without consuming quota`() = runTest(testDispatcher) {
        runBlocking {
            whenever(geminiService.generateEmpatheticResponse(any<String>())).thenReturn(
                AiResult.Error("Offline", isCrisisDetected = true)
            )
        }

        viewModel.sendMessage("I want to kill myself")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.crisisEvent.value)
        verify(preferences, never()).tryConsumeAiQuota(any())
    }

    @Test
    fun `multi-turn conversation passes history on second turn`() = runTest(testDispatcher) {
        runBlocking {
            whenever(geminiService.generateEmpatheticResponse("First"))
                .thenReturn(AiResult.Success("Reply 1", isCrisisDetected = false))
            whenever(geminiService.generateEmpatheticResponse(eq("Second"), any()))
                .thenReturn(AiResult.Success("Reply 2", isCrisisDetected = false))
        }

        viewModel.sendMessage("First")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.sendMessage("Second")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(geminiService).generateEmpatheticResponse("First")
        verify(geminiService).generateEmpatheticResponse(eq("Second"), any())
        assertEquals(5, viewModel.messages.value.size)
    }

    @Test
    fun `concurrent sendMessage is ignored while loading`() = runTest(testDispatcher) {
        val deferred = CompletableDeferred<AiResult>()
        runBlocking {
            whenever(geminiService.generateEmpatheticResponse(any<String>())).doSuspendableAnswer {
                deferred.await()
            }
        }

        viewModel.sendMessage("First")
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isLoading.value)

        viewModel.sendMessage("Second")
        testDispatcher.scheduler.runCurrent()
        verify(geminiService, times(1)).generateEmpatheticResponse(any<String>())

        deferred.complete(AiResult.Success("Done", isCrisisDetected = false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `quota exhaustion blocks further AI requests`() = runTest(testDispatcher) {
        runBlocking {
            whenever(preferences.getAiMessagesUsedToday()).thenReturn(50)
        }
        val exhaustedVm = AIChatViewModel(geminiService, preferences, auth)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(exhaustedVm.quotaExhausted.value)
        exhaustedVm.sendMessage("Blocked message")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(geminiService, never()).generateEmpatheticResponse(any<String>())
    }

    @Test
    fun `failed AI turn is excluded from multi-turn history so user turns are never unpaired`() = runTest(testDispatcher) {
        runBlocking {
            whenever(geminiService.generateEmpatheticResponse("First failed"))
                .thenReturn(AiResult.Error("Timeout", isCrisisDetected = false))
            whenever(geminiService.generateEmpatheticResponse("Second"))
                .thenReturn(AiResult.Success("Reply 2", isCrisisDetected = false))
            whenever(geminiService.generateEmpatheticResponse(eq("Third failed"), any()))
                .thenReturn(AiResult.Error("Server error", isCrisisDetected = false))
            whenever(geminiService.generateEmpatheticResponse(eq("Fourth"), any()))
                .thenReturn(AiResult.Success("Reply 4", isCrisisDetected = false))
        }

        viewModel.sendMessage("First failed")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.sendMessage("Second")
        testDispatcher.scheduler.advanceUntilIdle()
        verify(geminiService).generateEmpatheticResponse("Second")
        verify(geminiService, never()).generateEmpatheticResponse(eq("Second"), any())

        viewModel.sendMessage("Third failed")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.sendMessage("Fourth")
        testDispatcher.scheduler.advanceUntilIdle()
        verify(geminiService).generateEmpatheticResponse(
            eq("Fourth"),
            check { history ->
                assertEquals(2, history.size)
                assertEquals("user", history[0].role)
                assertEquals("model", history[1].role)
            }
        )
    }
}
