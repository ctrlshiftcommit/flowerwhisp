package com.flowerwhisp.mobile.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import com.flowerwhisp.mobile.domain.ports.AudioRecorder
import com.flowerwhisp.mobile.domain.ports.AudioRecording
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Records microphone input as AAC in an MPEG-4 container. */
class AndroidAudioRecorder(context: Context) : AudioRecorder {
    private val appContext = context.applicationContext
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionMutex = Mutex()
    private val mutableLevel = MutableStateFlow(0f)

    private var activeSession: RecordingSession? = null

    override val level: StateFlow<Float> = mutableLevel.asStateFlow()

    override suspend fun start(output: File) {
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                check(activeSession == null) { "A recording is already in progress" }
                prepareOutput(output)

                val mediaRecorder = MediaRecorder(appContext)
                try {
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    mediaRecorder.setAudioChannels(AUDIO_CHANNELS)
                    mediaRecorder.setAudioSamplingRate(SAMPLE_RATE_HZ)
                    mediaRecorder.setAudioEncodingBitRate(BIT_RATE_BPS)
                    mediaRecorder.setOutputFile(output.absolutePath)
                    mediaRecorder.prepare()
                    mediaRecorder.start()
                } catch (error: Exception) {
                    runCatching { mediaRecorder.release() }
                    mutableLevel.value = 0f
                    throw error
                }

                val session = RecordingSession(
                    recorder = mediaRecorder,
                    output = output,
                    startedAtElapsedMs = SystemClock.elapsedRealtime(),
                )
                activeSession = session
                session.meterJob = recorderScope.launch {
                    collectMeasuredLevel(session)
                }
            }
        }
    }

    override suspend fun stop(): AudioRecording {
        val session = detachSession()
            ?: throw IllegalStateException("No recording is in progress")
        return withContext(NonCancellable + Dispatchers.IO) {
            session.meterJob?.cancelAndJoin()
            try {
                session.recorder.stop()
                AudioRecording(
                    file = session.output,
                    durationMs = (SystemClock.elapsedRealtime() - session.startedAtElapsedMs)
                        .coerceAtLeast(0L),
                )
            } finally {
                runCatching { session.recorder.release() }
                mutableLevel.value = 0f
            }
        }
    }

    override suspend fun cancel() {
        val session = detachSession()
        if (session == null) {
            mutableLevel.value = 0f
            return
        }
        withContext(NonCancellable + Dispatchers.IO) {
            session.meterJob?.cancelAndJoin()
            try {
                // Stopping first gives Android a chance to finalize a recoverable container.
                runCatching { session.recorder.stop() }
            } finally {
                runCatching { session.recorder.release() }
                mutableLevel.value = 0f
            }
        }
        // Intentionally retain the output. The port has no way to distinguish a disposable
        // cancellation from interruption recovery, so deleting here could destroy valid audio.
    }

    private suspend fun detachSession(): RecordingSession? = sessionMutex.withLock {
        activeSession.also { activeSession = null }
    }

    private suspend fun collectMeasuredLevel(session: RecordingSession) {
        var smoothedLevel = 0f
        while (currentCoroutineContext().isActive) {
            val amplitude = runCatching { session.recorder.maxAmplitude }
                .getOrDefault(0)
                .coerceIn(0, MAX_AMPLITUDE)
            val measuredLevel = amplitude.toFloat() / MAX_AMPLITUDE.toFloat()
            smoothedLevel = (smoothedLevel * LEVEL_SMOOTHING) +
                (measuredLevel * (1f - LEVEL_SMOOTHING))
            mutableLevel.value = smoothedLevel.coerceIn(0f, 1f)
            delay(LEVEL_SAMPLE_INTERVAL_MS)
        }
    }

    private fun prepareOutput(output: File) {
        require(!output.isDirectory) { "Recording output must be a file" }
        if (output.exists() && output.length() > 0L) {
            throw IOException("Refusing to overwrite an existing recording")
        }
        val parent = output.absoluteFile.parentFile
            ?: throw IOException("Recording output has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create the recording directory")
        }
    }

    private data class RecordingSession(
        val recorder: MediaRecorder,
        val output: File,
        val startedAtElapsedMs: Long,
        var meterJob: Job? = null,
    )

    private companion object {
        const val AUDIO_CHANNELS = 1
        const val SAMPLE_RATE_HZ = 16_000
        const val BIT_RATE_BPS = 64_000
        const val MAX_AMPLITUDE = 32_767
        const val LEVEL_SAMPLE_INTERVAL_MS = 50L
        const val LEVEL_SMOOTHING = 0.65f
    }
}
