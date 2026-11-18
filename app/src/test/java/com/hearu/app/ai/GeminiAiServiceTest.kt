package com.hearu.app.ai

import com.google.firebase.vertexai.Chat
import com.google.firebase.vertexai.GenerativeModel
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiAiServiceTest {

    @Test
    fun `MODEL_NAME is gemini-2_0-flash`() {
        assertEquals("gemini-2.0-flash", GeminiAiService.MODEL_NAME)
    }

    @Test
    fun `generateEmpatheticResponse forwards history to GenerativeModel startChat and strips crisis tag`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mockModel: GenerativeModel = mock()
        val mockChat: Chat = mock()
        val mockResponse: GenerateContentResponse = mock()
        val history = listOf(
            content(role = "user") { text("I feel overwhelmed.") },
            content(role = "model") { text("I hear you. Tell me more.") }
        )

        whenever(mockModel.startChat(history)).thenReturn(mockChat)
        whenever(mockChat.sendMessage("Please help")).thenReturn(mockResponse)
        whenever(mockResponse.text).thenReturn("[CRISIS_ALERT] Call 988 now.")

        val service = GeminiAiService(mockModel, dispatcher)
        val result = service.generateEmpatheticResponse("Please help", history)

        verify(mockModel).startChat(history)
        verify(mockChat).sendMessage("Please help")
        assertTrue(result is AiResult.Success)
        result as AiResult.Success
        assertEquals("Call 988 now.", result.message)
        assertTrue(result.isCrisisDetected)
    }

    @Test
    fun `single-arg generateEmpatheticResponse delegates with empty history`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mockModel: GenerativeModel = mock()
        val mockChat: Chat = mock()
        val mockResponse: GenerateContentResponse = mock()

        whenever(mockModel.startChat(emptyList())).thenReturn(mockChat)
        whenever(mockChat.sendMessage("Hello")).thenReturn(mockResponse)
        whenever(mockResponse.text).thenReturn("Hi there, I'm listening.")

        val service = GeminiAiService(mockModel, dispatcher)
        val result = service.generateEmpatheticResponse("Hello")

        verify(mockModel).startChat(emptyList())
        assertEquals(AiResult.Success("Hi there, I'm listening.", isCrisisDetected = false), result)
    }

    @Test
    fun `crisis keyword sets isCrisisDetected true even when AI call throws exception`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = GeminiAiService(
            aiClient = GeminiAiService.AiChatClient { _, _ ->
                throw RuntimeException("Safety filter blocked")
            },
            ioDispatcher = dispatcher
        )

        val result = service.generateEmpatheticResponse("I want to kill myself")

        assertTrue(result is AiResult.Error)
        result as AiResult.Error
        assertTrue(result.isCrisisDetected)
        assertTrue(result.error.contains("Safety filter blocked"))
    }

    @Test
    fun `non-crisis prompt on exception returns Error with isCrisisDetected false`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = GeminiAiService(
            aiClient = GeminiAiService.AiChatClient { _, _ ->
                throw RuntimeException("Network timeout")
            },
            ioDispatcher = dispatcher
        )

        val result = service.generateEmpatheticResponse("How can I sleep better?")

        assertTrue(result is AiResult.Error)
        result as AiResult.Error
        assertFalse(result.isCrisisDetected)
    }

    @Test
    fun `prompts longer than 500 chars are truncated before crisis check and sending`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var capturedPrompt: String? = null
        val service = GeminiAiService(
            aiClient = GeminiAiService.AiChatClient { prompt, _ ->
                capturedPrompt = prompt
                "I'm here."
            },
            ioDispatcher = dispatcher
        )

        val result = service.generateEmpatheticResponse("a".repeat(500) + " kill myself")

        assertEquals(500, capturedPrompt?.length)
        assertTrue(result is AiResult.Success)
        assertFalse((result as AiResult.Success).isCrisisDetected)
    }

    @Test
    fun `blank or null response falls back to empathetic default text`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = GeminiAiService(
            aiClient = GeminiAiService.AiChatClient { _, _ -> null },
            ioDispatcher = dispatcher
        )

        val result = service.generateEmpatheticResponse("Feeling sad")

        assertEquals(
            AiResult.Success("I am here with you. Take a deep breath.", isCrisisDetected = false),
            result
        )
    }

    @Test
    fun `CancellationException is rethrown instead of wrapped in AiResult Error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = GeminiAiService(
            aiClient = GeminiAiService.AiChatClient { _, _ ->
                throw CancellationException("Coroutine cancelled")
            },
            ioDispatcher = dispatcher
        )

        var caught: CancellationException? = null
        try {
            service.generateEmpatheticResponse("I want to die")
        } catch (e: CancellationException) {
            caught = e
        }

        assertTrue("CancellationException must be rethrown", caught != null)
    }
}
