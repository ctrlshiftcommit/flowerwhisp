package com.flowerwhisp.mobile.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.flowerwhisp.mobile.R
import com.flowerwhisp.mobile.accessibility.FlowerWhispAccessibilityService
import com.flowerwhisp.mobile.accessibility.TargetCaptureResult
import com.flowerwhisp.mobile.accessibility.TargetInsertionOutcome
import com.flowerwhisp.mobile.accessibility.TargetToken
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.appliesTo
import com.flowerwhisp.mobile.domain.model.CleanupLevel
import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.forPackageContext
import com.flowerwhisp.mobile.domain.model.styleContextForPackage
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import com.flowerwhisp.mobile.domain.model.RetentionMode
import com.flowerwhisp.mobile.domain.ports.AudioRecording
import com.flowerwhisp.mobile.overlay.OverlayRuntime
import com.flowerwhisp.mobile.overlay.OverlayStatus
import com.flowerwhisp.mobile.platform.CapabilityMonitor
import com.flowerwhisp.mobile.platform.SensitiveClipboard
import com.flowerwhisp.mobile.refinement.DeterministicTextRefiner
import com.flowerwhisp.mobile.transcription.GroqEngineException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class DictationService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var activeSession: RecordingSession? = null
    private var levelJob: Job? = null
    private var autoStopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FROM_BUBBLE, ACTION_START_FROM_APP -> startRecording(intent)
            ACTION_STOP -> serviceScope.launch { stopAndProcess() }
            ACTION_CANCEL -> serviceScope.launch {
                cancelActiveSession("Recording cancelled", preserveRecovery = false)
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            cancelActiveSession(
                "FlowerWhisp was closed while dictation was active",
                preserveRecovery = true,
            )
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        val session = activeSession
        levelJob?.cancel()
        autoStopJob?.cancel()
        serviceJob.cancel()
        if (session != null) {
            runBlocking(Dispatchers.IO) {
                if (session.microphoneMayBeOwned) {
                    runCatching { session.dependencies.audioRecorder.cancel() }
                    session.microphoneMayBeOwned = false
                }
                (session.stoppedRecording ?: session.recoverableOutput())?.let { recording ->
                    if (!session.recoveryPersisted && !session.completed) {
                        persistRecovery(
                            session = session,
                            recording = recording,
                            originalText = session.originalText,
                            refinedText = session.refinedText,
                            status = DictationStatus.CANCELLED,
                        )
                    }
                }
            }
            releaseAudioFocus(session)
        }
        activeSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startRecording(intent: Intent) {
        if (activeSession != null) return
        val dependencies = DictationDependencyRegistry.peek()
        if (dependencies == null) {
            failAndStop("FlowerWhisp recording dependencies are not installed by the application container")
            return
        }
        val token = intent.readTargetToken()
        if (token == null) {
            failAndStop("FlowerWhisp did not receive a valid frozen target")
            return
        }
        val capability = CapabilityMonitor(this).snapshot(
            accessibilityConnected = FlowerWhispAccessibilityService.isConnected(),
        )
        if (!capability.microphoneGranted) {
            failAndStop("Microphone permission is required before recording can start")
            return
        }
        if (!capability.textInsertionReady) {
            failAndStop("FlowerWhisp accessibility disconnected before recording started")
            return
        }
        if (intent.action == ACTION_START_FROM_BUBBLE &&
            (!capability.overlayEnabled || OverlayRuntime.status.value !is OverlayStatus.Visible)
        ) {
            failAndStop("The visible FlowerWhisp bubble is required to start background recording")
            return
        }
        if (!FlowerWhispAccessibilityService.targetAwareGateway.isCurrentTarget(token)) {
            failAndStop("The focused field changed before recording started")
            return
        }
        if (!DictationRuntime.beginRecording(SystemClock.elapsedRealtime())) {
            failAndStop("FlowerWhisp is not ready to start another recording")
            return
        }

        val output = runCatching { dependencies.recordingFileFactory.create(this) }.getOrElse {
            failAndStop("FlowerWhisp could not create a private recovery recording")
            return
        }
        val session = RecordingSession(
            token = token,
            dependencies = dependencies,
            outputFile = output,
            createdAtEpochMs = System.currentTimeMillis(),
            startedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        activeSession = session
        if (!promoteToForeground("Listening. Tap Stop when you finish.")) {
            failAndStop("Android did not allow FlowerWhisp to start microphone recording")
            return
        }

        serviceScope.launch {
            val startSettings = runCatching { dependencies.settingsRepository.settings.first() }
                .getOrElse { AppSettings() }
            session.settings = startSettings
            val providerReady = startSettings.useMockEngines || runCatching {
                dependencies.settingsRepository.hasGroqApiKey()
            }.getOrDefault(false)
            if (!providerReady) {
                session.startOutcome.complete(false)
                failAndStop("Add a Groq API key before starting dictation")
                return@launch
            }
            if (startSettings.muteMusicWhileDictating) requestAudioFocus(session)
            if (startSettings.playSounds) playCue(starting = true)
            session.microphoneMayBeOwned = true
            try {
                dependencies.audioRecorder.start(output)
                // The provider/key lookup and MediaRecorder preparation can take long enough
                // that measuring from service creation would allow an immediate, invalid stop.
                // Start the minimum-duration window only after the microphone is truly live.
                session.startedAtElapsedMs = SystemClock.elapsedRealtime()
                if (session.cancelRequested || activeSession !== session) {
                    runCatching { dependencies.audioRecorder.cancel() }
                    session.microphoneMayBeOwned = false
                    if (session.preserveRecoveryOnCancel) {
                        session.recoverableOutput()?.let { recording ->
                            persistRecovery(
                                session = session,
                                recording = recording,
                                originalText = session.originalText,
                                refinedText = session.refinedText,
                                status = DictationStatus.CANCELLED,
                            )
                        }
                    } else {
                        runCatching { output.delete() }
                    }
                    session.startOutcome.complete(false)
                    return@launch
                }
                session.startOutcome.complete(true)
                levelJob = launch {
                    dependencies.audioRecorder.level.collect { DictationRuntime.updateLevel(it) }
                }
                autoStopJob = serviceScope.launch {
                    delay(MAX_RECORDING_DURATION_MS)
                    autoStopJob = null
                    stopAndProcess()
                }
            } catch (failure: CancellationException) {
                session.startOutcome.complete(false)
                throw failure
            } catch (failure: Throwable) {
                runCatching { dependencies.audioRecorder.cancel() }
                session.microphoneMayBeOwned = false
                session.startOutcome.complete(false)
                failAndStop("FlowerWhisp could not start the microphone")
            }
        }
    }

    private suspend fun stopAndProcess() {
        val session = activeSession ?: return
        if (session.stopRequested) return
        session.stopRequested = true
        if (!session.startOutcome.await() || activeSession !== session) return
        if (session.stoppedRecording != null || !session.microphoneMayBeOwned) return
        val remainingMinimum = MIN_RECORDING_DURATION_MS -
            (SystemClock.elapsedRealtime() - session.startedAtElapsedMs)
        if (remainingMinimum > 0L) delay(remainingMinimum)
        levelJob?.cancel()
        levelJob = null
        autoStopJob?.cancel()
        autoStopJob = null

        val recording = try {
            session.dependencies.audioRecorder.stop()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            runCatching { session.dependencies.audioRecorder.cancel() }
            session.microphoneMayBeOwned = false
            failAndStop("FlowerWhisp could not stop and preserve the recording")
            return
        }
        session.microphoneMayBeOwned = false
        session.stoppedRecording = recording
        releaseAudioFocus(session)
        if (session.settings?.playSounds == true) playCue(starting = false)
        updateNotification("Processing your dictation")
        processStoppedRecording(session, recording)
    }

    private suspend fun processStoppedRecording(session: RecordingSession, recording: AudioRecording) {
        var failureStatus = DictationStatus.TRANSCRIPTION_FAILED
        try {
            val storedSettings = session.dependencies.settingsRepository.settings.first()
            val context = styleContextForPackage(session.token.packageName)
            val settings = storedSettings.forPackageContext(session.token.packageName)
            session.settings = settings
            DictationRuntime.processing(ProcessingStage.TRANSCRIBING)
            val original = session.dependencies.transcriptionEngine
                .transcribe(recording.file, settings.language)
                .trim()
            if (original.isBlank()) throw PipelineFailure("Transcription returned no text")
            session.originalText = original

            val dictionary = session.dependencies.dictionaryRepository.observeAll().first()
                .filter { it.appliesTo(context) }
            val snippets = session.dependencies.snippetRepository.observeAll().first()
            val safeText = DeterministicTextRefiner.refine(
                source = original,
                style = settings.writingStyle,
                settings = settings,
                dictionary = dictionary,
                snippets = snippets,
            )
            if (safeText.isBlank()) throw PipelineFailure("Text processing returned no text")
            session.safeText = safeText

            failureStatus = DictationStatus.REFINEMENT_FAILED
            DictationRuntime.processing(ProcessingStage.REFINING)
            val refined = if (settings.aiRefinement && settings.cleanupLevel != CleanupLevel.NONE) {
                try {
                    session.dependencies.refinementEngine.refine(
                        source = safeText,
                        style = settings.writingStyle,
                        settings = settings,
                        dictionary = dictionary,
                        snippets = snippets,
                    ).trim().takeIf(String::isNotEmpty)?.also { result ->
                        session.cleanupStatus = if (result == safeText) CleanupStatus.UNCHANGED else CleanupStatus.APPLIED
                    } ?: safeText.also { session.cleanupStatus = CleanupStatus.UNCHANGED }
                } catch (failure: Throwable) {
                    session.cleanupStatus = CleanupStatus.FAILED
                    session.cleanupError = failure.message?.take(240)?.takeIf(String::isNotBlank)
                        ?: "Text cleanup failed"
                    safeText
                }
            } else {
                safeText
            }
            session.refinedText = refined

            failureStatus = DictationStatus.INSERTION_FAILED
            DictationRuntime.processing(ProcessingStage.INSERTING)
            val rawOutcome = FlowerWhispAccessibilityService.targetAwareGateway.insert(session.token, refined)
            val outcome = ensureClipboardRecovery(rawOutcome)
            when (outcome) {
                TargetInsertionOutcome.VerifiedInserted -> {
                    persistCompleted(session, recording)
                    session.completed = true
                    if (!session.shouldRetainCompletedAudio()) recording.file.delete()
                    DictationRuntime.onSuccess()
                }
                is TargetInsertionOutcome.ClipboardFallback -> {
                    val id = persistRecovery(
                        session = session,
                        recording = recording,
                        originalText = original,
                        refinedText = refined,
                        status = DictationStatus.INSERTION_FAILED,
                    )
                    if (outcome.copied) {
                        DictationRuntime.onFallback(outcome.text, outcome.reason)
                    } else {
                        DictationRuntime.onServiceError(outcome.reason, id)
                    }
                }
            }
            finishAfterResult()
        } catch (failure: CancellationException) {
            withContext(NonCancellable) {
                persistRecovery(
                    session = session,
                    recording = recording,
                    originalText = session.originalText,
                    refinedText = session.refinedText,
                    status = DictationStatus.CANCELLED,
                )
            }
            throw failure
        } catch (failure: Throwable) {
            val id = persistRecovery(
                session = session,
                recording = recording,
                originalText = session.originalText,
                refinedText = session.refinedText,
                status = failureStatus,
            )
            val message = (failure as? PipelineFailure)?.publicMessage
                ?: (failure as? GroqEngineException)?.message?.take(240)?.takeIf(String::isNotBlank)
                ?: when (failureStatus) {
                    DictationStatus.TRANSCRIPTION_FAILED -> "FlowerWhisp could not transcribe the preserved recording"
                    DictationStatus.REFINEMENT_FAILED -> "FlowerWhisp could not refine the preserved recording"
                    else -> "FlowerWhisp could not finish the preserved dictation"
                }
            DictationRuntime.onServiceError(message, id)
            finishAfterResult()
        }
    }

    private suspend fun persistCompleted(session: RecordingSession, recording: AudioRecording) {
        session.dependencies.historyRepository.upsert(
            session.toDictation(
                recording = recording,
                originalText = session.originalText,
                safeText = session.safeText,
                refinedText = session.refinedText,
                status = DictationStatus.COMPLETE,
                recoveryAudioPath = recording.file.absolutePath.takeIf { session.shouldRetainCompletedAudio() },
            ),
        )
        session.recoveryPersisted = true
    }

    private suspend fun persistRecovery(
        session: RecordingSession,
        recording: AudioRecording,
        originalText: String,
        safeText: String = session.safeText,
        refinedText: String,
        status: DictationStatus,
    ): Long? {
        if (session.recoveryPersisted) return session.persistedId
        return runCatching {
            session.dependencies.historyRepository.upsert(
                session.toDictation(
                    recording = recording,
                    originalText = originalText,
                    safeText = safeText,
                    refinedText = refinedText,
                    status = status,
                    recoveryAudioPath = recording.file.absolutePath,
                ),
            )
        }.getOrNull()?.also { id ->
            session.persistedId = id
            session.recoveryPersisted = true
        }
    }

    private fun RecordingSession.toDictation(
        recording: AudioRecording,
        originalText: String,
        safeText: String,
        refinedText: String,
        status: DictationStatus,
        recoveryAudioPath: String?,
    ): Dictation = Dictation(
        id = persistedId ?: 0,
        createdAtEpochMs = createdAtEpochMs,
        originalText = originalText,
        safeText = safeText,
        refinedText = refinedText,
        durationMs = recording.durationMs,
        language = settings?.language ?: com.flowerwhisp.mobile.domain.model.LanguageMode.AUTO,
        status = status,
        recoveryAudioPath = recoveryAudioPath,
        cleanupStatus = cleanupStatus,
        cleanupError = cleanupError,
    )

    private fun RecordingSession.shouldRetainCompletedAudio(): Boolean =
        settings?.retentionMode != RetentionMode.NEVER || cleanupStatus == CleanupStatus.FAILED

    private fun requestAudioFocus(session: RecordingSession) {
        val manager = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { }
            .build()
        if (runCatching { manager.requestAudioFocus(request) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            session.audioFocusRequest = request
        }
    }

    private fun releaseAudioFocus(session: RecordingSession) {
        val request = session.audioFocusRequest ?: return
        runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request) }
        session.audioFocusRequest = null
    }

    private fun playCue(starting: Boolean) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 42) }.getOrNull() ?: return
        val toneType = if (starting) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK
        if (!tone.startTone(toneType, 90)) {
            tone.release()
            return
        }
        Handler(mainLooper).postDelayed({ runCatching { tone.release() } }, 180L)
    }

    private fun ensureClipboardRecovery(outcome: TargetInsertionOutcome): TargetInsertionOutcome {
        if (outcome !is TargetInsertionOutcome.ClipboardFallback || outcome.copied || outcome.text.isBlank()) {
            return outcome
        }
        val copied = SensitiveClipboard.copy(this, outcome.text)
        return outcome.copy(
            copied = copied,
            reason = if (copied) {
                outcome.reason.substringBefore(". FlowerWhisp could not copy the text")
            } else {
                outcome.reason
            },
        )
    }

    private suspend fun cancelActiveSession(message: String, preserveRecovery: Boolean) {
        val session = activeSession
        session?.cancelRequested = true
        session?.preserveRecoveryOnCancel = preserveRecovery
        if (session != null && !session.startOutcome.isCompleted) {
            session.startOutcome.await()
        }
        val wasRecording = session?.microphoneMayBeOwned == true
        levelJob?.cancel()
        levelJob = null
        autoStopJob?.cancel()
        autoStopJob = null
        if (session != null && session.microphoneMayBeOwned) {
            runCatching { session.dependencies.audioRecorder.cancel() }
            session.microphoneMayBeOwned = false
        }
        if (session != null) {
            releaseAudioFocus(session)
            if (wasRecording && session.settings?.playSounds == true) playCue(starting = false)
        }
        val recoverable = session?.stoppedRecording ?: session?.recoverableOutput()
        if (preserveRecovery && session != null && recoverable != null) {
            persistRecovery(
                session = session,
                recording = recoverable,
                originalText = session.originalText,
                refinedText = session.refinedText,
                status = DictationStatus.CANCELLED,
            )
        }
        if (!preserveRecovery) {
            session?.outputFile?.let { output -> runCatching { output.delete() } }
        }
        if (preserveRecovery) {
            DictationRuntime.onServiceError(message, session?.persistedId)
        } else {
            val capabilities = CapabilityMonitor(this).snapshot(
                accessibilityConnected = FlowerWhispAccessibilityService.isConnected(),
            )
            DictationRuntime.resetToAvailability(
                capabilities.canShowBubble &&
                    FlowerWhispAccessibilityService.targetAwareGateway.hasSupportedFocusedField(),
            )
        }
        activeSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun finishAfterResult() {
        kotlinx.coroutines.delay(1_500)
        val capabilities = CapabilityMonitor(this).snapshot(
            accessibilityConnected = FlowerWhispAccessibilityService.isConnected(),
        )
        val available = capabilities.canShowBubble &&
            FlowerWhispAccessibilityService.targetAwareGateway.hasSupportedFocusedField()
        DictationRuntime.resetToAvailability(available)
        activeSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteToForeground(message: String): Boolean {
        createNotificationChannel()
        return runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(message),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }.isSuccess
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(message),
        )
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, DictationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            Intent(this, DictationService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("FlowerWhisp dictation")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Stop", stopIntent)
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun failAndStop(message: String) {
        val session = activeSession
        session?.cancelRequested = true
        session?.startOutcome?.complete(false)
        levelJob?.cancel()
        levelJob = null
        autoStopJob?.cancel()
        autoStopJob = null
        if (session?.microphoneMayBeOwned == true) {
            runBlocking(Dispatchers.IO) {
                runCatching { session.dependencies.audioRecorder.cancel() }
            }
            session.microphoneMayBeOwned = false
        }
        if (session != null && !session.recoveryPersisted && !session.completed) {
            runCatching { session.outputFile.delete() }
        }
        if (session != null) releaseAudioFocus(session)
        DictationRuntime.onServiceError(message, session?.persistedId)
        activeSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private data class RecordingSession(
        val token: TargetToken,
        val dependencies: DictationDependencies,
        val outputFile: File,
        val createdAtEpochMs: Long,
        var startedAtElapsedMs: Long,
        var microphoneMayBeOwned: Boolean = false,
        var stoppedRecording: AudioRecording? = null,
        var settings: AppSettings? = null,
        var originalText: String = "",
        var safeText: String = "",
        var refinedText: String = "",
        var cleanupStatus: CleanupStatus = CleanupStatus.DISABLED,
        var cleanupError: String? = null,
        var audioFocusRequest: AudioFocusRequest? = null,
        var persistedId: Long? = null,
        var recoveryPersisted: Boolean = false,
        var completed: Boolean = false,
        var cancelRequested: Boolean = false,
        var preserveRecoveryOnCancel: Boolean = false,
        var stopRequested: Boolean = false,
        val startOutcome: CompletableDeferred<Boolean> = CompletableDeferred(),
    )

    private fun RecordingSession.recoverableOutput(): AudioRecording? = outputFile
        .takeIf { it.isFile && it.length() > 0L }
        ?.let {
            AudioRecording(
                file = it,
                durationMs = (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L),
            )
        }

    private class PipelineFailure(val publicMessage: String) : IllegalStateException(publicMessage)

    companion object {
        private const val ACTION_START_FROM_BUBBLE = "com.flowerwhisp.mobile.action.START_FROM_BUBBLE"
        private const val ACTION_START_FROM_APP = "com.flowerwhisp.mobile.action.START_FROM_APP"
        private const val ACTION_STOP = "com.flowerwhisp.mobile.action.STOP_DICTATION"
        private const val ACTION_CANCEL = "com.flowerwhisp.mobile.action.CANCEL_DICTATION"
        private const val EXTRA_PACKAGE = "target.package"
        private const val EXTRA_WINDOW = "target.window"
        private const val EXTRA_CLASS = "target.class"
        private const val EXTRA_VIEW_ID = "target.view_id"
        private const val EXTRA_GENERATION = "target.generation"
        private const val CHANNEL_ID = "flowerwhisp_dictation"
        private const val NOTIFICATION_ID = 4107
        private const val REQUEST_STOP = 4108
        private const val REQUEST_CANCEL = 4109
        private const val MIN_RECORDING_DURATION_MS = 650L
        private const val MAX_RECORDING_DURATION_MS = 15L * 60L * 1_000L

        fun startFromBubble(context: Context, token: TargetToken): String? {
            if (OverlayRuntime.status.value !is OverlayStatus.Visible) {
                return "The FlowerWhisp bubble must be visible before recording starts"
            }
            return startExplicit(context, ACTION_START_FROM_BUBBLE, token)
        }

        /** Call only from a visible in-app test control after the user explicitly taps it. */
        fun startFromVisibleInAppAction(context: Context): String? {
            val capture = FlowerWhispAccessibilityService.targetAwareGateway.captureTarget()
            return when (capture) {
                is TargetCaptureResult.Captured -> startExplicit(context, ACTION_START_FROM_APP, capture.token)
                is TargetCaptureResult.Rejected -> capture.reason
            }
        }

        fun stopFromBubble(context: Context) {
            runCatching {
                context.startService(Intent(context, DictationService::class.java).setAction(ACTION_STOP))
            }.onFailure {
                DictationRuntime.onServiceError("FlowerWhisp could not deliver the explicit stop action")
            }
        }

        fun cancel(context: Context) {
            runCatching {
                context.startService(Intent(context, DictationService::class.java).setAction(ACTION_CANCEL))
            }.onFailure {
                DictationRuntime.onServiceError("FlowerWhisp could not deliver the cancel action")
            }
        }

        private fun startExplicit(context: Context, action: String, token: TargetToken): String? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return "Microphone permission is required before recording can start"
            }
            val intent = Intent(context, DictationService::class.java)
                .setAction(action)
                .putTargetToken(token)
            return try {
                ContextCompat.startForegroundService(context, intent)
                null
            } catch (failure: ForegroundServiceStartNotAllowedException) {
                "Android blocked the microphone service because the start was not recognized as a visible user action"
            } catch (failure: SecurityException) {
                "Android blocked the microphone foreground service permission"
            } catch (failure: RuntimeException) {
                "FlowerWhisp could not start the recording service"
            }
        }

        private fun Intent.putTargetToken(token: TargetToken): Intent = apply {
            putExtra(EXTRA_PACKAGE, token.packageName)
            putExtra(EXTRA_WINDOW, token.windowId)
            putExtra(EXTRA_CLASS, token.className)
            putExtra(EXTRA_VIEW_ID, token.viewIdResourceName)
            putExtra(EXTRA_GENERATION, token.generation)
        }

        private fun Intent.readTargetToken(): TargetToken? {
            val packageName = getStringExtra(EXTRA_PACKAGE)?.takeIf(String::isNotBlank) ?: return null
            val windowId = getIntExtra(EXTRA_WINDOW, -1).takeIf { it >= 0 } ?: return null
            val generation = getLongExtra(EXTRA_GENERATION, -1L).takeIf { it >= 0L } ?: return null
            return TargetToken(
                packageName = packageName,
                windowId = windowId,
                className = getStringExtra(EXTRA_CLASS),
                viewIdResourceName = getStringExtra(EXTRA_VIEW_ID),
                generation = generation,
            )
        }
    }
}
