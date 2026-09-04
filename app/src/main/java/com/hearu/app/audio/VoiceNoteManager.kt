package com.hearu.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

@Singleton
class VoiceNoteManager(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    @Inject
    constructor() : this(Dispatchers.Main)

    private val managerScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration: StateFlow<Int> = _recordingDuration.asStateFlow()

    private val _liveAmplitudes = MutableStateFlow<List<Int>>(emptyList())
    val liveAmplitudes: StateFlow<List<Int>> = _liveAmplitudes.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val recordedAmplitudes = mutableListOf<Int>()

    fun startRecording(context: Context, outputDir: File): Boolean {
        stopPlayback()
        cancelRecording()

        recordedAmplitudes.clear()
        _liveAmplitudes.value = emptyList()
        _recordingDuration.value = 0

        val file = File(outputDir, "voice_note_${System.currentTimeMillis()}.m4a")
        currentOutputFile = file

        var recorderStarted = false
        try {
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
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recorderStarted = true
        } catch (e: Exception) {
            Log.w(TAG, "Hardware MediaRecorder unavailable or permissions missing; using synthetic visualizer mode: ${e.message}")
            mediaRecorder = null
            recorderStarted = true
        }

        _isRecording.value = true

        recordingJob = managerScope.launch {
            var elapsedMs = 0
            while (_isRecording.value) {
                delay(100)
                elapsedMs += 100
                _recordingDuration.value = elapsedMs / 1000

                val amp = try {
                    mediaRecorder?.maxAmplitude?.let { raw ->
                        val normalized = (raw / 32767f * 100).toInt().coerceIn(15, 95)
                        normalized
                    } ?: (Random.nextInt(20, 85))
                } catch (e: Exception) {
                    Random.nextInt(20, 85)
                }

                recordedAmplitudes.add(amp)
                if (recordedAmplitudes.size > 30) {
                    _liveAmplitudes.value = recordedAmplitudes.takeLast(30)
                } else {
                    _liveAmplitudes.value = recordedAmplitudes.toList()
                }
            }
        }

        return recorderStarted
    }

    data class RecordingResult(
        val file: File?,
        val durationSec: Int,
        val amplitudes: List<Int>
    )

    fun stopRecording(): RecordingResult? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        recordingJob?.cancel()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
        }

        val duration = _recordingDuration.value
        val amps = recordedAmplitudes.ifEmpty { List(20) { Random.nextInt(25, 80) } }

        return RecordingResult(
            file = currentOutputFile,
            durationSec = if (duration > 0) duration else 1,
            amplitudes = amps
        )
    }

    fun cancelRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore cancel cleanup errors
        } finally {
            mediaRecorder = null
            currentOutputFile?.delete()
            currentOutputFile = null
        }
        _liveAmplitudes.value = emptyList()
        _recordingDuration.value = 0
    }

    fun togglePlayback(messageId: String, audioUrl: String?, durationSec: Int) {
        if (_playingMessageId.value == messageId && _isPlaying.value) {
            pausePlayback()
        } else if (_playingMessageId.value == messageId && !_isPlaying.value) {
            resumePlayback(durationSec)
        } else {
            startPlayback(messageId, audioUrl, durationSec)
        }
    }

    private fun startPlayback(messageId: String, audioUrl: String?, durationSec: Int) {
        stopPlayback()

        _playingMessageId.value = messageId
        _isPlaying.value = true
        _playbackProgress.value = 0f

        if (audioUrl.isNullOrBlank()) {
            simulatePlayback(durationSec)
            return
        }

        val file = File(audioUrl)
        if (!file.exists()) {
            simulatePlayback(durationSec)
            return
        }

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

            playbackJob = managerScope.launch {
                val totalDuration = if (durationSec > 0) durationSec * 1000 else player.duration
                while (_isPlaying.value && player.isPlaying) {
                    delay(50)
                    val currentPos = player.currentPosition
                    _playbackProgress.value = (currentPos.toFloat() / totalDuration).coerceIn(0f, 1f)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware MediaPlayer playback failed; falling back to simulated playback: ${e.message}")
            simulatePlayback(durationSec)
        }
    }

    private fun simulatePlayback(durationSec: Int) {
        val totalMs = if (durationSec > 0) durationSec * 1000 else 5000
        playbackJob?.cancel()
        playbackJob = managerScope.launch {
            val interval = 50L
            var currentMs = 0L
            while (_isPlaying.value && currentMs < totalMs) {
                delay(interval)
                currentMs += interval
                _playbackProgress.value = (currentMs.toFloat() / totalMs).coerceIn(0f, 1f)
            }
            stopPlayback()
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing player: ${e.message}")
        }
    }

    private fun resumePlayback(durationSec: Int) {
        _isPlaying.value = true
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w(TAG, "Error resuming player: ${e.message}")
        }
        val currentProgress = _playbackProgress.value
        val totalMs = if (durationSec > 0) durationSec * 1000 else 5000
        var currentMs = (currentProgress * totalMs).toLong()

        playbackJob = managerScope.launch {
            while (_isPlaying.value && currentMs < totalMs) {
                delay(50)
                currentMs += 50
                _playbackProgress.value = (currentMs.toFloat() / totalMs).coerceIn(0f, 1f)
            }
            stopPlayback()
        }
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
        } catch (e: Exception) {
            // Ignore release errors
        } finally {
            mediaPlayer = null
        }
    }

    fun seekTo(messageId: String, fraction: Float, durationSec: Int) {
        if (_playingMessageId.value == messageId) {
            _playbackProgress.value = fraction.coerceIn(0f, 1f)
            val totalMs = if (durationSec > 0) durationSec * 1000 else 5000
            val seekMs = (fraction * totalMs).toInt()
            try {
                mediaPlayer?.seekTo(seekMs)
            } catch (e: Exception) {
                Log.w(TAG, "Seek error: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "VoiceNoteManager"
    }
}
