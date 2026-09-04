package com.hearu.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

data class RecordingResult(
    val file: File?,
    val durationSec: Int,
    val amplitudes: List<Int>
)

@Singleton
class VoiceNoteManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "VoiceNoteManager"

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration: StateFlow<Int> = _recordingDuration.asStateFlow()

    private val _liveAmplitudes = MutableStateFlow<List<Int>>(emptyList())
    val liveAmplitudes: StateFlow<List<Int>> = _liveAmplitudes.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val managerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var recordingTimerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var playbackJob: Job? = null

    private val recordedAmplitudes = mutableListOf<Int>()

    fun startRecording(): Boolean {
        if (_isRecording.value) return false
        stopPlayback()

        return try {
            val cacheDir = File(context.cacheDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val outputFile = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _isRecording.value = true
            _recordingDuration.value = 0
            recordedAmplitudes.clear()
            _liveAmplitudes.value = emptyList()

            startRecordingTimers()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder: ${e.message}")
            cancelRecording()
            false
        }
    }

    private fun startRecordingTimers() {
        recordingTimerJob?.cancel()
        recordingTimerJob = managerScope.launch {
            while (_isRecording.value) {
                delay(1000)
                _recordingDuration.value += 1
                if (_recordingDuration.value >= 120) {
                    break
                }
            }
        }

        amplitudeJob?.cancel()
        amplitudeJob = managerScope.launch {
            while (_isRecording.value) {
                delay(100)
                val maxAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (_: Exception) { 0 }

                val normalized = (maxAmp / 32767f * 100).toInt().coerceIn(10, 100)
                recordedAmplitudes.add(normalized)
                val current = _liveAmplitudes.value.toMutableList()
                if (current.size >= 28) current.removeAt(0)
                current.add(normalized)
                _liveAmplitudes.value = current
            }
        }
    }

    fun stopRecording(): RecordingResult {
        if (!_isRecording.value) {
            return RecordingResult(null, 0, emptyList())
        }

        _isRecording.value = false
        recordingTimerJob?.cancel()
        amplitudeJob?.cancel()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder: ${e.message}")
        }
        mediaRecorder = null

        val duration = _recordingDuration.value.coerceAtLeast(1)
        val finalAmplitudes = sampleWaveform(recordedAmplitudes, targetCount = 28)

        val result = RecordingResult(
            file = currentOutputFile,
            durationSec = duration,
            amplitudes = finalAmplitudes
        )

        _liveAmplitudes.value = emptyList()
        _recordingDuration.value = 0
        return result
    }

    fun cancelRecording() {
        _isRecording.value = false
        recordingTimerJob?.cancel()
        amplitudeJob?.cancel()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        currentOutputFile?.delete()
        currentOutputFile = null
        _liveAmplitudes.value = emptyList()
        _recordingDuration.value = 0
    }

    fun togglePlayback(messageId: String, audioPathOrUrl: String?, durationSec: Int) {
        if (_playingMessageId.value == messageId && _isPlaying.value) {
            pausePlayback()
        } else if (_playingMessageId.value == messageId && !_isPlaying.value) {
            resumePlayback(durationSec)
        } else {
            startPlayback(messageId, audioPathOrUrl, durationSec)
        }
    }

    private fun startPlayback(messageId: String, audioPathOrUrl: String?, durationSec: Int) {
        stopPlayback()

        _playingMessageId.value = messageId
        _isPlaying.value = true
        _playbackProgress.value = 0f

        val totalDurationMs = (durationSec.coerceAtLeast(1) * 1000).toLong()

        val file = audioPathOrUrl?.let { File(it) }
        var playerStarted = false

        if (file != null && file.exists()) {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopPlayback()
                    }
                }
                mediaPlayer = player
                playerStarted = true
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer hardware playback fallback: ${e.message}")
            }
        }

        playbackJob = managerScope.launch {
            var elapsedMs = 0L
            val stepMs = 50L
            while (_isPlaying.value && elapsedMs < totalDurationMs) {
                delay(stepMs)
                elapsedMs += stepMs
                val currentMs = mediaPlayer?.currentPosition?.toLong() ?: elapsedMs
                val totalMs = mediaPlayer?.duration?.toLong()?.takeIf { it > 0 } ?: totalDurationMs
                _playbackProgress.value = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            }
            if (_isPlaying.value) {
                stopPlayback()
            }
        }
    }

    private fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    private fun resumePlayback(durationSec: Int) {
        _isPlaying.value = true
        val totalDurationMs = (durationSec.coerceAtLeast(1) * 1000).toLong()
        try {
            mediaPlayer?.start()
        } catch (_: Exception) {}

        playbackJob = managerScope.launch {
            var elapsedMs = (_playbackProgress.value * totalDurationMs).toLong()
            val stepMs = 50L
            while (_isPlaying.value && elapsedMs < totalDurationMs) {
                delay(stepMs)
                elapsedMs += stepMs
                val currentMs = mediaPlayer?.currentPosition?.toLong() ?: elapsedMs
                val totalMs = mediaPlayer?.duration?.toLong()?.takeIf { it > 0 } ?: totalDurationMs
                _playbackProgress.value = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            }
            if (_isPlaying.value) {
                stopPlayback()
            }
        }
    }

    fun seekTo(messageId: String, progress: Float, durationSec: Int) {
        if (_playingMessageId.value != messageId) {
            _playingMessageId.value = messageId
        }
        _playbackProgress.value = progress.coerceIn(0f, 1f)
        val totalDurationMs = (durationSec.coerceAtLeast(1) * 1000).toLong()
        val targetMs = (progress * totalDurationMs).toInt()
        try {
            mediaPlayer?.seekTo(targetMs)
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        _isPlaying.value = false
        _playingMessageId.value = null
        _playbackProgress.value = 0f
        playbackJob?.cancel()

        try {
            mediaPlayer?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun sampleWaveform(raw: List<Int>, targetCount: Int = 28): List<Int> {
        if (raw.isEmpty()) {
            return List(targetCount) { Random.nextInt(20, 80) }
        }
        if (raw.size <= targetCount) {
            val padded = raw.toMutableList()
            while (padded.size < targetCount) {
                padded.add(Random.nextInt(20, 60))
            }
            return padded
        }
        val step = raw.size.toDouble() / targetCount.toDouble()
        return (0 until targetCount).map { i ->
            val index = min((i * step).toInt(), raw.size - 1)
            raw[index].coerceIn(15, 95)
        }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
        managerScope.cancel()
    }
}
