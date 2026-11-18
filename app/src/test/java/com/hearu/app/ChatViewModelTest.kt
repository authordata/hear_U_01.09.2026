package com.hearu.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.model.ChatSession
import com.hearu.app.model.Message
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.ModerationRepository
import com.hearu.app.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val chatRepository: ChatRepository = mock()
    private val auth: FirebaseAuth = mock()
    private val voiceNoteManager: VoiceNoteManager = mock()
    private val moderationRepository: ModerationRepository = mock()
    private val mockUser: FirebaseUser = mock()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(auth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("test_user_456")
        whenever(voiceNoteManager.isRecording).thenReturn(MutableStateFlow(false))
        whenever(voiceNoteManager.recordingDuration).thenReturn(MutableStateFlow(0))
        whenever(voiceNoteManager.liveAmplitudes).thenReturn(MutableStateFlow(emptyList()))
        whenever(voiceNoteManager.playingMessageId).thenReturn(MutableStateFlow(null))
        whenever(voiceNoteManager.playbackProgress).thenReturn(MutableStateFlow(0f))
        whenever(voiceNoteManager.isPlaying).thenReturn(MutableStateFlow(false))

        runBlocking {
            whenever(chatRepository.sendMessage(any<String>(), any<Message>())).thenReturn(Unit)
            whenever(chatRepository.updatePhotoConsent(any(), any(), any())).thenReturn(false)
        }

        viewModel = ChatViewModel(chatRepository, auth, voiceNoteManager, moderationRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial messages list is empty and currentUserId reflects FirebaseAuth`() = runTest(testDispatcher) {
        assertEquals(emptyList<Message>(), viewModel.messages.value)
        assertEquals("test_user_456", viewModel.currentUserId)
    }

    @Test
    fun `joinSession collects messages from repository`() = runTest(testDispatcher) {
        val testMessages = listOf(
            Message(id = "msg_1", senderId = "peer_1", text = "Hello there", timestamp = 1000L),
            Message(id = "msg_2", senderId = "test_user_456", text = "Hi!", timestamp = 2000L)
        )
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(testMessages))

        viewModel.joinSession("session_123", "test_user_456")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.messages.value.size)
        assertEquals("Hello there", viewModel.messages.value[0].text)
    }

    @Test
    fun `sendMessage delegates to chatRepository`() = runTest(testDispatcher) {
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(emptyList()))
        viewModel.joinSession("session_123", "test_user_456")

        viewModel.sendMessage("Empathetic check-in")
        testDispatcher.scheduler.advanceUntilIdle()

        runBlocking {
            verify(chatRepository).sendMessage(eq("session_123"), check { msg ->
                assertEquals("Empathetic check-in", msg.text)
                assertEquals("test_user_456", msg.senderId)
                assertEquals(Message.TYPE_TEXT, msg.messageType)
            })
        }
    }

    @Test
    fun `stopAndSendVoiceRecording sends voice note with waveforms and duration`() = runTest(testDispatcher) {
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(emptyList()))
        viewModel.joinSession("session_123", "test_user_456")

        val dummyFile = File("/tmp/voice.m4a")
        val sampleAmplitudes = listOf(30, 50, 75, 40, 60)
        whenever(voiceNoteManager.stopRecording()).thenReturn(
            VoiceNoteManager.RecordingResult(
                file = dummyFile,
                durationSec = 14,
                amplitudes = sampleAmplitudes
            )
        )

        viewModel.stopAndSendVoiceRecording()
        testDispatcher.scheduler.advanceUntilIdle()

        runBlocking {
            verify(chatRepository).sendMessage(eq("session_123"), check { msg ->
                assertEquals(Message.TYPE_VOICE, msg.messageType)
                assertEquals(14, msg.audioDurationSec)
                assertEquals(sampleAmplitudes, msg.waveformAmplitudes)
                assertEquals(dummyFile.absolutePath, msg.audioUrl)
                assertTrue(msg.text.contains("0:14"))
            })
        }
    }

    @Test
    fun `toggleVoicePlayback delegates to VoiceNoteManager`() = runTest(testDispatcher) {
        val voiceMsg = Message(
            id = "msg_audio_1",
            senderId = "peer_1",
            messageType = Message.TYPE_VOICE,
            audioUrl = "/path/to/voice.m4a",
            audioDurationSec = 22
        )

        viewModel.toggleVoicePlayback(voiceMsg)

        verify(voiceNoteManager).togglePlayback("msg_audio_1", "/path/to/voice.m4a", 22)
    }

    @Test
    fun `seekVoicePlayback delegates to VoiceNoteManager`() = runTest(testDispatcher) {
        val voiceMsg = Message(
            id = "msg_audio_1",
            senderId = "peer_1",
            messageType = Message.TYPE_VOICE,
            audioDurationSec = 22
        )

        viewModel.seekVoicePlayback(voiceMsg, 0.5f)

        verify(voiceNoteManager).seekTo("msg_audio_1", 0.5f, 22)
    }

    @Test
    fun `togglePhotoConsent delegates to chatRepository without faking mutual reveal`() = runTest(testDispatcher) {
        val session = ChatSession(
            sessionId = "session_123",
            seekerId = "test_user_456",
            giverId = "peer_1",
            seekerConsent = false,
            giverConsent = false,
            identityRevealed = false
        )
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(emptyList()))
        whenever(chatRepository.observeSession("session_123")).thenReturn(flowOf(session))
        runBlocking {
            whenever(chatRepository.updatePhotoConsent("session_123", true, true)).thenReturn(false)
        }

        viewModel.joinSession("session_123", "peer_1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePhotoConsent()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.myPhotoConsent.value)
        assertFalse(viewModel.isPhotoRevealed.value)
        verify(chatRepository).updatePhotoConsent("session_123", true, true)
    }

    @Test
    fun `submitReport validates auth and calls moderationRepository`() = runTest(testDispatcher) {
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(emptyList()))
        viewModel.joinSession("session_123", "peer_99")

        viewModel.submitReport("peer_99", "Spam / Scams", "Details")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.errorState.value)
        verify(moderationRepository).reportUser(
            reporterId = "test_user_456",
            reportedUserId = "peer_99",
            sessionId = "session_123",
            reason = "Spam / Scams",
            description = "Details"
        )

        whenever(auth.currentUser).thenReturn(null)
        viewModel.submitReport("peer_99", "Spam / Scams", "Details")
        assertTrue(viewModel.errorState.value?.contains("Session expired") == true)
    }

    @Test
    fun `blockAndLeave blocks user, ends session, stops sync, and invokes callback`() = runTest(testDispatcher) {
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(emptyList()))
        viewModel.joinSession("session_123", "peer_99")

        var callbackInvoked = false
        viewModel.blockAndLeave("peer_99") { callbackInvoked = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(callbackInvoked)
        verify(moderationRepository).blockUser("test_user_456", "peer_99")
        verify(chatRepository).updateSessionStatus("session_123", "ended")
        verify(chatRepository).stopFirestoreSync("session_123")
    }

    @Test
    fun `onCleared stops Firestore sync and cleans up VoiceNoteManager`() = runTest(testDispatcher) {
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(emptyList()))
        viewModel.joinSession("session_123", "peer_99")

        val method = ChatViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)

        verify(chatRepository).stopFirestoreSync("session_123")
        verify(voiceNoteManager).cancelRecording()
        verify(voiceNoteManager).stopPlayback()
        verify(voiceNoteManager).cleanup()
    }

    @Test
    fun `joinSession clears prior messages immediately when switching sessions`() = runTest(testDispatcher) {
        val session1Messages = listOf(
            Message(id = "m1", senderId = "peer_1", text = "Session 1 message", timestamp = 1000L)
        )
        val session2Flow = MutableSharedFlow<List<Message>>()
        whenever(chatRepository.getMessages("session_1")).thenReturn(flowOf(session1Messages))
        whenever(chatRepository.getMessages("session_2")).thenReturn(session2Flow)

        viewModel.joinSession("session_1", "peer_1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.size)

        viewModel.joinSession("session_2", "peer_2")
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `togglePhotoConsent reverts consent and sets errorState on real NullPointerException`() = runTest(testDispatcher) {
        whenever(chatRepository.getMessages("session_123")).thenReturn(flowOf(emptyList()))
        runBlocking {
            whenever(chatRepository.updatePhotoConsent("session_123", true, true)).thenAnswer {
                throw NullPointerException("firestore failure")
            }
        }

        viewModel.joinSession("session_123", "peer_1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.togglePhotoConsent()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.myPhotoConsent.value)
        assertTrue(viewModel.errorState.value?.contains("firestore failure") == true)
    }
}
