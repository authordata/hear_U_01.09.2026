package com.hearu.app.audio

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoiceNoteManagerRobolectricTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var manager: VoiceNoteManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        testDispatcher = StandardTestDispatcher()
        manager = VoiceNoteManager(mainDispatcher = testDispatcher, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        manager.cleanup()
    }

    @Test
    fun `startRecording returns false when RECORD_AUDIO permission is denied`() {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val dir = tempFolder.newFolder("rec_denied")

        val started = manager.startRecording(app, dir)

        assertFalse(started)
        assertFalse(manager.isRecording.value)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `startRecording returns false when outputDir is not a directory`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val regularFile = tempFolder.newFile("not_a_dir.txt")

        val started = manager.startRecording(app, regularFile)

        assertFalse(started)
        assertFalse(manager.isRecording.value)
    }

    @Test
    fun `startRecording and stopRecording capture waveform amplitudes`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val dir = tempFolder.newFolder("rec_valid")

        val started = manager.startRecording(app, dir)
        assertTrue(started)
        assertTrue(manager.isRecording.value)

        testDispatcher.scheduler.advanceTimeBy(1200L)
        testDispatcher.scheduler.runCurrent()

        val result = manager.stopRecording()
        assertNotNull(result)
        assertEquals(28, result!!.amplitudes.size)
        assertTrue(result.durationSec >= 1)
        assertFalse(manager.isRecording.value)
        assertNull(manager.stopRecording())
    }

    @Test
    fun `cancelRecording deletes temporary file and resets flows`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val dir = tempFolder.newFolder("rec_cancel")

        assertTrue(manager.startRecording(app, dir))
        testDispatcher.scheduler.advanceTimeBy(400L)
        testDispatcher.scheduler.runCurrent()

        manager.cancelRecording()

        assertFalse(manager.isRecording.value)
        assertEquals(0, manager.recordingDuration.value)
        assertTrue(manager.liveAmplitudes.value.isEmpty())
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `togglePlayback pause resume seekTo and remote URL prepareAsync work without crashing`() {
        manager.togglePlayback("m1", null, 4)
        assertEquals("m1", manager.playingMessageId.value)
        assertTrue(manager.isPlaying.value)

        testDispatcher.scheduler.advanceTimeBy(1000L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(manager.playbackProgress.value > 0f)

        // Pause
        manager.togglePlayback("m1", null, 4)
        assertFalse(manager.isPlaying.value)

        // Resume
        manager.togglePlayback("m1", null, 4)
        assertTrue(manager.isPlaying.value)

        // Seek clamping
        manager.seekTo("m1", -1f, 4)
        assertEquals(0f, manager.playbackProgress.value, 0.001f)
        manager.seekTo("m1", 2f, 4)
        assertEquals(1f, manager.playbackProgress.value, 0.001f)

        // Remote URL prepareAsync path
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource("https://cdn.hearu.app/sample.m4a"),
            ShadowMediaPlayer.MediaInfo(5_000, 0)
        )
        manager.togglePlayback("m_remote", "https://cdn.hearu.app/sample.m4a", 5)
        testDispatcher.scheduler.runCurrent()
        assertEquals("m_remote", manager.playingMessageId.value)
        assertTrue(manager.isPlaying.value)
    }

    @Test
    fun `cleanup stops active work and leaves singleton manager reusable`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val dir = tempFolder.newFolder("rec_cleanup")

        assertTrue(manager.startRecording(app, dir))
        manager.cleanup()

        assertFalse(manager.isRecording.value)
        assertFalse(manager.isPlaying.value)
        assertNull(manager.playingMessageId.value)

        // Verify singleton manager remains usable after cleanup
        manager.togglePlayback("m_after", null, 2)
        testDispatcher.scheduler.advanceTimeBy(500L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(manager.isPlaying.value)
        assertTrue(manager.playbackProgress.value > 0f)
    }
}
