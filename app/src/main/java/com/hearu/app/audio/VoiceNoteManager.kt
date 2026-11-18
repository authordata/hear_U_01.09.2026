package com.hearu.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.hearu.app.di.IoDispatcher
import com.hearu.app.di.MainDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

@Singleton
class VoiceNoteManager @Inject constructor(
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    constructor(mainDispatcher: CoroutineDispatcher = Dispatchers.Main) : this(
        mainDispatcher = mainDispatcher,
        ioDispatcher = Dispatchers.IO
    )
    private val managerScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null
    private var isPlayerPrepared: Boolean = false

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackPrepareJob: Job? = null

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

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return false
        }

        if (!outputDir.isDirectory && !outputDir.mkdirs()) {
            Log.w(TAG, "Invalid voice note output directory")
            return false
        }

        recordedAmplitudes.clear()
        _liveAmplitudes.value = emptyList()
        _recordingDuration.value = 0

        val file = File(outputDir, "voice_note_${System.currentTimeMillis()}.m4a")
        var recorder: MediaRecorder? = null
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            currentOutputFile = file
            _isRecording.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder: ${e.message}")
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            file.delete()
            mediaRecorder = null
            currentOutputFile = null
            _isRecording.value = false
            return false
        }

        recordingJob = managerScope.launch {
            var elapsedMs = 0
            while (_isRecording.value) {
                delay(100)
                elapsedMs += 100
                _recordingDuration.value = elapsedMs / 1000

                val amp = try {
                    mediaRecorder?.maxAmplitude?.let { raw ->
                        (raw / 32767f * 100).toInt().coerceIn(15, 95)
                    } ?: Random.nextInt(20, 85)
                } catch (_: Exception) {
                    Random.nextInt(20, 85)
                }

                recordedAmplitudes.add(amp)
                _liveAmplitudes.value = if (recordedAmplitudes.size > 30) {
                    recordedAmplitudes.takeLast(30)
                } else {
                    recordedAmplitudes.toList()
                }
            }
        }

        return true
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
        recordingJob = null

        val recorder = mediaRecorder
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder: ${e.message}")
        } finally {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
        }

        val finalDuration = _recordingDuration.value.coerceAtLeast(1)
        val sampledAmps = sampleWaveform(recordedAmplitudes, targetCount = 28)
        val outputFile = currentOutputFile
        currentOutputFile = null

        return RecordingResult(
            file = outputFile,
            durationSec = finalDuration,
            amplitudes = sampledAmps
        )
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        val recorder = mediaRecorder
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder on cancel: ${e.message}")
        } finally {
            try {
                recorder?.reset()
            } catch (_: Exception) {
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
        }
        currentOutputFile?.delete()
        currentOutputFile = null

        recordedAmplitudes.clear()
        _isRecording.value = false
        _recordingDuration.value = 0
        _liveAmplitudes.value = emptyList()
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

        startPlaybackProgressTracking(durationSec)

        if (audioPathOrUrl.isNullOrBlank()) {
            return
        }

        playbackPrepareJob = managerScope.launch {
            val isLocalFile = withContext(ioDispatcher) {
                File(audioPathOrUrl).exists()
            }
            if (_playingMessageId.value != messageId) return@launch

            val player = MediaPlayer()
            mediaPlayer = player
            isPlayerPrepared = false

            player.setOnCompletionListener {
                stopPlayback()
            }
            player.setOnErrorListener { mp, _, _ ->
                if (mediaPlayer === mp) {
                    stopPlayback()
                } else {
                    releasePlayerSafely(mp)
                }
                true
            }
            player.setOnPreparedListener { mp ->
                if (_playingMessageId.value != messageId || mediaPlayer !== mp) {
                    releasePlayerSafely(mp)
                    return@setOnPreparedListener
                }
                isPlayerPrepared = true
                if (_isPlaying.value) {
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not start prepared MediaPlayer: ${e.message}")
                    }
                }
            }

            try {
                if (isLocalFile) {
                    withContext(ioDispatcher) {
                        player.setDataSource(audioPathOrUrl)
                        player.prepare()
                    }
                    if (_playingMessageId.value != messageId || mediaPlayer !== player) {
                        releasePlayerSafely(player)
                        return@launch
                    }
                    isPlayerPrepared = true
                    if (_isPlaying.value) {
                        player.start()
                    }
                } else {
                    withContext(ioDispatcher) {
                        player.setDataSource(audioPathOrUrl)
                    }
                    if (_playingMessageId.value != messageId || mediaPlayer !== player) {
                        releasePlayerSafely(player)
                        return@launch
                    }
                    player.prepareAsync()
                }
            } catch (e: CancellationException) {
                releasePlayerSafely(player)
                if (mediaPlayer === player) {
                    mediaPlayer = null
                    isPlayerPrepared = false
                }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not prepare audio playback: ${e.message}")
                if (mediaPlayer === player) {
                    stopPlayback()
                } else {
                    releasePlayerSafely(player)
                }
            }
        }
    }

    private fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        if (isPlayerPrepared) {
            try {
                mediaPlayer?.pause()
            } catch (_: Exception) {
            }
        }
    }

    private fun resumePlayback(durationSec: Int) {
        _isPlaying.value = true
        if (isPlayerPrepared) {
            try {
                mediaPlayer?.start()
            } catch (_: Exception) {
            }
        }
        startPlaybackProgressTracking(durationSec)
    }

    private fun startPlaybackProgressTracking(durationSec: Int) {
        playbackJob?.cancel()
        val totalMs = (durationSec.coerceAtLeast(1) * 1000).toLong()

        playbackJob = managerScope.launch {
            var currentMs = (_playbackProgress.value * totalMs).toLong()
            while (_isPlaying.value && currentMs < totalMs) {
                delay(100)
                currentMs += 100
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
        val clamped = progress.coerceIn(0f, 1f)
        _playbackProgress.value = clamped
        val totalDurationMs = (durationSec.coerceAtLeast(1) * 1000).toLong()
        val targetMs = (clamped * totalDurationMs).toInt()
        if (isPlayerPrepared) {
            try {
                mediaPlayer?.seekTo(targetMs)
            } catch (_: Exception) {
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        _playingMessageId.value = null
        _playbackProgress.value = 0f
        playbackPrepareJob?.cancel()
        playbackPrepareJob = null
        playbackJob?.cancel()
        playbackJob = null

        val player = mediaPlayer
        val wasPrepared = isPlayerPrepared
        mediaPlayer = null
        isPlayerPrepared = false
        if (player != null) {
            try {
                if (wasPrepared) {
                    player.stop()
                }
            } catch (_: Exception) {
            } finally {
                releasePlayerSafely(player)
            }
        }
    }

    fun cleanup() {
        cancelRecording()
        stopPlayback()
        managerScope.coroutineContext[Job]?.cancelChildren()
    }

    private fun releasePlayerSafely(player: MediaPlayer?) {
        try {
            player?.release()
        } catch (_: Exception) {
        }
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

    companion object {
        private const val TAG = "VoiceNoteManager"
    }
}

typealias RecordingResult = VoiceNoteManager.RecordingResult
