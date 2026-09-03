package com.hearu.app

import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.model.Message
import com.hearu.app.repository.ChatRepository
import com.hearu.app.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val chatRepository: ChatRepository = mock()
    private val auth: FirebaseAuth = mock()
    private val voiceNoteManager: VoiceNoteManager = mock()
    private val mockUser: FirebaseUser = mock()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(auth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("test_user_456")
        whenever(voiceNoteManager.isRecording).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))
        whenever(voiceNoteManager.recordingDuration).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(0))
        whenever(voiceNoteManager.liveAmplitudes).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        whenever(voiceNoteManager.playingMessageId).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(null))
        whenever(voiceNoteManager.playbackProgress).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(0f))
        whenever(voiceNoteManager.isPlaying).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))

        viewModel = ChatViewModel(chatRepository, auth, voiceNoteManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial messages list is empty`() = runTest(testDispatcher) {
        assertEquals(emptyList<Message>(), viewModel.messages.value)
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

        verify(chatRepository).sendMessage(eq("session_123"), check { msg ->
            assertEquals("Empathetic check-in", msg.text)
            assertEquals("test_user_456", msg.senderId)
            assertEquals(Message.TYPE_TEXT, msg.messageType)
        })
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

        verify(chatRepository).sendMessage(eq("session_123"), check { msg ->
            assertEquals(Message.TYPE_VOICE, msg.messageType)
            assertEquals(14, msg.audioDurationSec)
            assertEquals(sampleAmplitudes, msg.waveformAmplitudes)
            assertEquals(dummyFile.absolutePath, msg.audioUrl)
            assertTrue(msg.text.contains("0:14"))
        })
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
}
