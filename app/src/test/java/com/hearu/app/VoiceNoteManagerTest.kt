package com.hearu.app

import com.hearu.app.audio.VoiceNoteManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VoiceNoteManagerTest {

    private lateinit var voiceNoteManager: VoiceNoteManager

    @Before
    fun setUp() {
        voiceNoteManager = VoiceNoteManager()
    }

    @Test
    fun `initial states are idle`() {
        assertFalse(voiceNoteManager.isRecording.value)
        assertEquals(0, voiceNoteManager.recordingDuration.value)
        assertTrue(voiceNoteManager.liveAmplitudes.value.isEmpty())
        assertNull(voiceNoteManager.playingMessageId.value)
        assertFalse(voiceNoteManager.isPlaying.value)
        assertEquals(0f, voiceNoteManager.playbackProgress.value, 0.001f)
    }

    @Test
    fun `cancelRecording resets states cleanly`() {
        voiceNoteManager.cancelRecording()
        assertFalse(voiceNoteManager.isRecording.value)
        assertEquals(0, voiceNoteManager.recordingDuration.value)
        assertTrue(voiceNoteManager.liveAmplitudes.value.isEmpty())
    }

    @Test
    fun `stopPlayback clears playing state and resets progress`() {
        voiceNoteManager.stopPlayback()
        assertFalse(voiceNoteManager.isPlaying.value)
        assertNull(voiceNoteManager.playingMessageId.value)
        assertEquals(0f, voiceNoteManager.playbackProgress.value, 0.001f)
    }
}
