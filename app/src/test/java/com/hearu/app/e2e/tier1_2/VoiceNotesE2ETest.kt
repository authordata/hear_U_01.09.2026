package com.hearu.app.e2e.tier1_2

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.local.AppDatabase
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.model.Message
import com.hearu.app.repository.ChatRepository
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.ChatViewModel
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Opaque-box E2E tests for Feature F8: Voice Notes, RECORD_AUDIO Permission & Playback.
 * Covers Tier 1 (6 happy-path tests) and Tier 2 (6 boundary/corner/error tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceNotesE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var tempDir: File
    private lateinit var db: AppDatabase
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockChatRepository: ChatRepository
    private lateinit var mockVoiceNoteManager: VoiceNoteManager
    private lateinit var mockRecordingFlow: MutableStateFlow<Boolean>
    private var realVoiceNoteManager: VoiceNoteManager? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        tempDir = createTempDirectory("hearu_voice_e2e").toFile()
        db = E2ETestHarness.createInMemoryDb(app)

        mockAuth = mock()
        mockUser = mock()
        mockChatRepository = mock()
        mockVoiceNoteManager = createStubbedVoiceNoteManager()

        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("u1")
        whenever(mockChatRepository.getMessages(any())).thenReturn(flowOf(emptyList()))
        runBlocking {
            whenever(mockChatRepository.sendMessage(any(), any())).thenReturn(Unit)
        }
    }

    @After
    fun tearDown() {
        realVoiceNoteManager?.cancelRecording()
        realVoiceNoteManager?.stopPlayback()
        realVoiceNoteManager = null
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createStubbedVoiceNoteManager(): VoiceNoteManager {
        val manager: VoiceNoteManager = mock()
        mockRecordingFlow = MutableStateFlow(false)
        whenever(manager.isRecording).thenReturn(mockRecordingFlow)
        whenever(manager.recordingDuration).thenReturn(MutableStateFlow(0))
        whenever(manager.liveAmplitudes).thenReturn(MutableStateFlow(emptyList()))
        whenever(manager.playingMessageId).thenReturn(MutableStateFlow(null))
        whenever(manager.playbackProgress).thenReturn(MutableStateFlow(0f))
        whenever(manager.isPlaying).thenReturn(MutableStateFlow(false))
        whenever(manager.startRecording(any(), any())).thenAnswer {
            mockRecordingFlow.value = true
            true
        }
        whenever(manager.stopRecording()).thenAnswer {
            mockRecordingFlow.value = false
            null
        }
        whenever(manager.cancelRecording()).thenAnswer {
            mockRecordingFlow.value = false
        }
        return manager
    }

    private fun createChatViewModel(
        chatRepository: ChatRepository = mockChatRepository,
        auth: FirebaseAuth = mockAuth,
        voiceNoteManager: VoiceNoteManager = mockVoiceNoteManager
    ): ChatViewModel = ChatViewModel(chatRepository, auth, voiceNoteManager)

    private fun drainUi() {
        ShadowLooper.idleMainLooper()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
    }

    // =========================================================================
    // Tier 1 — Happy Path (6 Tests)
    // =========================================================================

    @Test
    fun f8_tier1_manifestDeclaresRecordAudioPermission() {
        val manifestFile = File(
            E2ETestHarness.resolveProjectRoot(),
            "app/src/main/AndroidManifest.xml"
        )
        assertTrue(manifestFile.exists())
        val manifestText = manifestFile.readText()
        assertTrue(manifestText.contains("android.permission.RECORD_AUDIO"))

        @Suppress("DEPRECATION")
        val packageInfo = app.packageManager.getPackageInfo(
            app.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions?.toList().orEmpty()
        assertTrue(
            "Merged package manifest must declare RECORD_AUDIO",
            requested.contains(Manifest.permission.RECORD_AUDIO)
        )
    }

    @Test
    fun f8_tier1_recordingLifecycleGeneratesM4aFileAnd28SampledAmplitudes() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val testDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = testDispatcher).also {
            realVoiceNoteManager = it
        }

        assertFalse(manager.isRecording.value)
        assertEquals(0, manager.recordingDuration.value)
        assertTrue(manager.liveAmplitudes.value.isEmpty())

        val started = manager.startRecording(app, tempDir)
        assertTrue(started)
        assertTrue(manager.isRecording.value)

        testDispatcher.scheduler.advanceTimeBy(1200L)
        testDispatcher.scheduler.runCurrent()

        assertTrue(manager.recordingDuration.value >= 1)
        assertTrue(manager.liveAmplitudes.value.isNotEmpty())

        val result = manager.stopRecording()
        assertNotNull(result)
        assertTrue(result!!.durationSec >= 1)
        assertEquals(28, result.amplitudes.size)
        assertNotNull(result.file)
        assertTrue(result.file!!.name.startsWith("voice_note_"))
        assertTrue(result.file!!.name.endsWith(".m4a"))
        assertFalse(manager.isRecording.value)
    }

    @Test
    fun f8_tier1_stopAndSendVoiceRecordingDelegatesVoiceMessageToRepository() =
        runTest(mainDispatcherRule.testDispatcher) {
            val dummyFile = File(tempDir, "voice_note_send.m4a").apply { writeText("audio") }
            val amplitudes = listOf(20, 50, 80)
            whenever(mockVoiceNoteManager.stopRecording()).thenReturn(
                VoiceNoteManager.RecordingResult(
                    file = dummyFile,
                    durationSec = 14,
                    amplitudes = amplitudes
                )
            )

            val vm = createChatViewModel()
            vm.joinSession("s1", "u1")
            vm.stopAndSendVoiceRecording()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            verify(mockChatRepository).sendMessage(
                eq("s1"),
                check { msg ->
                    assertTrue(msg.isVoiceNote)
                    assertEquals(Message.TYPE_VOICE, msg.messageType)
                    assertEquals(14, msg.audioDurationSec)
                    assertEquals(amplitudes, msg.waveformAmplitudes)
                    assertTrue(msg.text.contains("0:14"))
                }
            )
        }

    @Test
    fun f8_tier1_playbackTogglePauseResumeSeekAndStopTransitionsState() {
        val tempAudio = File(tempDir, "sample_play.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(tempAudio.absolutePath),
            ShadowMediaPlayer.MediaInfo(10_000, 0)
        )
        val testDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = testDispatcher).also {
            realVoiceNoteManager = it
        }

        manager.togglePlayback("m1", tempAudio.absolutePath, 10)
        assertTrue(manager.isPlaying.value)
        assertEquals("m1", manager.playingMessageId.value)

        testDispatcher.scheduler.advanceTimeBy(2000L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(manager.playbackProgress.value > 0f)

        manager.togglePlayback("m1", tempAudio.absolutePath, 10)
        assertFalse(manager.isPlaying.value)
        assertEquals("m1", manager.playingMessageId.value)

        manager.seekTo("m1", 0.5f, 10)
        assertEquals(0.5f, manager.playbackProgress.value, 0.01f)

        manager.togglePlayback("m1", tempAudio.absolutePath, 10)
        assertTrue(manager.isPlaying.value)

        manager.stopPlayback()
        assertFalse(manager.isPlaying.value)
        assertNull(manager.playingMessageId.value)
        assertEquals(0f, manager.playbackProgress.value, 0.001f)
    }

    @Test
    fun f8_tier1_chatScreenDisplaysVoiceBubbleAndRecordBarControls() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val voiceMsg = Message(
            id = "v1",
            senderId = "peer_1",
            text = "🎤 Voice Note (0:15)",
            timestamp = 1000L,
            isVoiceNote = true,
            voiceDurationSeconds = 15,
            messageType = Message.TYPE_VOICE
        )
        whenever(mockChatRepository.getMessages("s1")).thenReturn(flowOf(listOf(voiceMsg)))
        whenever(mockVoiceNoteManager.stopRecording()).thenAnswer {
            mockRecordingFlow.value = false
            VoiceNoteManager.RecordingResult(File(tempDir, "rec.m4a"), 1, listOf(30, 60))
        }

        val vm = createChatViewModel()
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "u1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithText("0:15").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Record Voice Note").performClick()
        drainUi()

        composeTestRule.onNodeWithText("Recording: 0:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Send Voice Note").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Send Voice Note").performClick()
        drainUi()

        composeTestRule.onNodeWithText("Recording: 0:00").assertDoesNotExist()
        verify(mockVoiceNoteManager).stopRecording()
    }

    @Test
    fun f8_tier1_roomMessageEntityStoresVoiceDurationFlag() =
        runTest(mainDispatcherRule.testDispatcher) {
            val entity = MessageEntity(
                id = "v1",
                sessionId = "s1",
                senderId = "u1",
                text = "🎤 Voice Note (0:09)",
                timestamp = 1000L,
                isVoiceNote = true,
                voiceDurationSeconds = 9
            )
            db.messageDao().insertMessage(entity)

            val rows = db.messageDao().getMessagesForSession("s1").first()
            assertEquals(1, rows.size)
            assertTrue(rows[0].isVoiceNote)
            assertEquals(9, rows[0].voiceDurationSeconds)
        }

    // =========================================================================
    // Tier 2 — Boundary / Corner / Error (6 Tests)
    // =========================================================================

    @Test
    fun f8_tier2_startRecordingDeniedPermissionOrRecorderFailureReturnsFalseAndDoesNotFakeRecording() {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val invalidOutputDir = File(tempDir, "not_a_directory.txt").apply { writeText("locked") }

        val testDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = testDispatcher).also {
            realVoiceNoteManager = it
        }

        val started = manager.startRecording(app, invalidOutputDir)

        assertFalse(
            "startRecording must return false when RECORD_AUDIO is denied or MediaRecorder fails",
            started
        )
        assertFalse(
            "isRecording must remain false when recording fails",
            manager.isRecording.value
        )
    }

    @Test
    fun f8_tier2_chatScreenTapMicWithoutRecordAudioPermissionDoesNotStartRecording() {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val testDispatcher = StandardTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)
        val manager = VoiceNoteManager(mainDispatcher = testDispatcher).also {
            realVoiceNoteManager = it
        }
        val vm = createChatViewModel(voiceNoteManager = manager)

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "u1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Record Voice Note").performClick()
        drainUi()

        composeTestRule.onNodeWithText("Recording:", substring = true).assertDoesNotExist()
        assertFalse(
            "VoiceNoteManager must not start recording without RECORD_AUDIO permission",
            manager.isRecording.value
        )
    }

    @Test
    fun f8_tier2_voiceNoteBubblePlaybackDelegatesToViewModelNotIsolatedLocalTimer() {
        val voiceMsg = Message(
            id = "vn_peer",
            senderId = "peer_1",
            text = "🎤 Voice Note (0:12)",
            timestamp = 1000L,
            isVoiceNote = true,
            voiceDurationSeconds = 12,
            messageType = Message.TYPE_VOICE,
            audioUrl = "/tmp/p.m4a",
            audioDurationSec = 12
        )
        whenever(mockChatRepository.getMessages("s1")).thenReturn(flowOf(listOf(voiceMsg)))
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "u1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Play").performClick()
        drainUi()

        verify(mockVoiceNoteManager).togglePlayback(eq("vn_peer"), anyOrNull(), any())
    }

    @Test
    fun f8_tier2_cancelRecordingDeletesTempFileAndClearsAmplitudes() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val testDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = testDispatcher).also {
            realVoiceNoteManager = it
        }

        manager.startRecording(app, tempDir)
        testDispatcher.scheduler.advanceTimeBy(500L)
        testDispatcher.scheduler.runCurrent()

        manager.cancelRecording()

        assertFalse(manager.isRecording.value)
        assertEquals(0, manager.recordingDuration.value)
        assertTrue(manager.liveAmplitudes.value.isEmpty())
        assertNull(manager.stopRecording())

        val leftover = tempDir.listFiles { _, name ->
            name.startsWith("voice_note_") && name.endsWith(".m4a")
        }.orEmpty()
        assertTrue("Cancelled recording must remove temp .m4a file", leftover.isEmpty())
    }

    @Test
    fun f8_tier2_playbackBoundarySeekClampingZeroDurationAndTrackSwitch() {
        val track1 = File(tempDir, "track1.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val track2 = File(tempDir, "track2.m4a").apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(track1.absolutePath),
            ShadowMediaPlayer.MediaInfo(5_000, 0)
        )
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(track2.absolutePath),
            ShadowMediaPlayer.MediaInfo(8_000, 0)
        )
        val testDispatcher = StandardTestDispatcher()
        val manager = VoiceNoteManager(mainDispatcher = testDispatcher).also {
            realVoiceNoteManager = it
        }

        manager.seekTo("m1", -0.5f, 0)
        assertEquals(0f, manager.playbackProgress.value, 0.001f)

        manager.seekTo("m1", 2.0f, 5)
        assertEquals(1.0f, manager.playbackProgress.value, 0.001f)

        manager.togglePlayback("m1", track1.absolutePath, 5)
        assertEquals("m1", manager.playingMessageId.value)
        assertTrue(manager.isPlaying.value)

        manager.togglePlayback("m2", track2.absolutePath, 8)
        assertEquals("m2", manager.playingMessageId.value)
        assertEquals(0f, manager.playbackProgress.value, 0.001f)
        assertTrue(manager.isPlaying.value)
    }

    @Test
    fun f8_tier2_chatRepositoryPreservesVoiceMetadataThroughRoomSsot() =
        runTest(mainDispatcherRule.testDispatcher) {
            val mockFirestore: FirebaseFirestore = mock()
            val mockSessionsCol: CollectionReference = mock()
            val mockSessionDoc: DocumentReference = mock()
            val mockMessagesCol: CollectionReference = mock()
            val mockDocRef: DocumentReference = mock()
            val mockQuery: Query = mock()
            val mockReg: ListenerRegistration = mock()

            whenever(mockFirestore.collection("sessions")).thenReturn(mockSessionsCol)
            whenever(mockSessionsCol.document(any())).thenReturn(mockSessionDoc)
            whenever(mockSessionDoc.collection("messages")).thenReturn(mockMessagesCol)
            whenever(mockMessagesCol.document()).thenReturn(mockDocRef)
            whenever(mockDocRef.id).thenReturn("m_vn")
            whenever(mockDocRef.set(any())).thenReturn(Tasks.forResult(null))
            whenever(mockMessagesCol.orderBy("timestamp", Query.Direction.ASCENDING)).thenReturn(mockQuery)
            whenever(mockQuery.addSnapshotListener(any())).thenReturn(mockReg)

            val repo = ChatRepository(mockFirestore, db.messageDao())
            val voiceMessage = Message(
                senderId = "u1",
                text = "Voice",
                timestamp = 5000L,
                isVoiceNote = true,
                voiceDurationSeconds = 18,
                messageType = Message.TYPE_VOICE,
                audioUrl = "https://cdn.hearu.app/v.m4a",
                audioDurationSec = 18,
                waveformAmplitudes = listOf(25, 60, 90)
            )

            repo.sendMessage("s_vn", voiceMessage)
            val persisted = repo.getMessages("s_vn").first()
            repo.stopFirestoreSync("s_vn")

            assertEquals(1, persisted.size)
            val loaded = persisted[0]
            assertEquals(Message.TYPE_VOICE, loaded.messageType)
            assertEquals("https://cdn.hearu.app/v.m4a", loaded.audioUrl)
            assertEquals(18, loaded.audioDurationSec)
            assertEquals(listOf(25, 60, 90), loaded.waveformAmplitudes)
        }
}
