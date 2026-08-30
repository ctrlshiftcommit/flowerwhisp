package com.flowerwhisp.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowerwhisp.mobile.ui.app.FlowerWhispActions
import com.flowerwhisp.mobile.ui.app.FlowerWhispApp
import com.flowerwhisp.mobile.ui.app.FlowerWhispDestination
import com.flowerwhisp.mobile.platform.SensitiveClipboard
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { mainViewModel?.refreshCapabilities() }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { mainViewModel?.refreshCapabilities() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pruneStaleAudioExports()
        enableEdgeToEdge()
        setContent {
            val application = application as FlowerWhispApplication
            val model: MainViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        MainViewModel(application) as T
                },
            )
            mainViewModel = model
            val uiState by model.uiState.collectAsStateWithLifecycle()
            FlowerWhispApp(
                uiState = uiState,
                actions = FlowerWhispActions(
                    onNavigate = model::navigate,
                    onAdvanceOnboarding = model::advanceOnboarding,
                    onCompleteOnboarding = model::completeOnboarding,
                    onSkipOnboarding = model::skipOnboarding,
                    onRequestAccessibility = ::openAccessibilitySettings,
                    onRequestOverlay = ::openOverlaySettings,
                    onRequestMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                    onRequestNotifications = ::requestNotifications,
                    onOnboardingTap = model::startDictation,
                    onOnboardingHold = model::startDictation,
                    onOnboardingRealTest = model::startDictation,
                    onStart = model::startDictation,
                    onFinish = model::finishDictation,
                    onCancel = model::cancelDictation,
                    onRetry = model::retryCurrentState,
                    onCopy = ::copyText,
                    onOpenApp = { model.navigate(FlowerWhispDestination.HOME) },
                    onSearchHistory = model::searchHistory,
                    onRefreshHistory = model::refreshHistory,
                    onOpenHistory = model::openHistory,
                    onCloseHistory = model::closeHistory,
                    onFavoriteHistory = model::favoriteHistory,
                    onDeleteHistory = model::deleteHistory,
                    onCopyHistory = { id -> model.withHistoryText(id, ::copyText) },
                    onShareHistory = { id -> model.withHistoryText(id, ::shareText) },
                    onPlayHistory = model::playHistory,
                    onDeleteHistoryAudio = model::deleteHistoryAudio,
                    onUndoHistoryCleanup = model::undoHistoryCleanup,
                    onExportHistoryAudio = { id -> model.withHistoryAudio(id, ::shareAudio) },
                    onCopyLastTranscript = { model.withLatestHistoryText(::copyText) },
                    onSendHistoryToScratchpad = model::sendHistoryToScratchpad,
                    onRetryHistory = model::retryHistory,
                    onAddDictionary = model::upsertDictionary,
                    onLibrarySectionChanged = model::selectLibrary,
                    onEditDictionary = model::upsertDictionary,
                    onDeleteDictionary = model::deleteDictionary,
                    onDictionaryEnabledChanged = model::setDictionaryEnabled,
                    onAddSnippet = model::upsertSnippet,
                    onEditSnippet = model::upsertSnippet,
                    onDeleteSnippet = model::deleteSnippet,
                    onSnippetEnabledChanged = model::setSnippetEnabled,
                    onSaveTransform = model::upsertTransform,
                    onDeleteTransform = model::deleteTransform,
                    onRunHistoryTransform = model::runHistoryTransform,
                    onRunScratchpadTransform = model::runScratchpadTransform,
                    onDismissTransform = model::dismissTransform,
                    onSaveScratchpad = model::saveScratchpad,
                    onWritingStyleChanged = model::setWritingStyle,
                    onContextWritingStyleChanged = model::setContextWritingStyle,
                    onContextStyleInstructionsChanged = model::setContextStyleInstructions,
                    onLanguageChanged = model::setLanguage,
                    onAutoPunctuationChanged = model::setAutoPunctuation,
                    onRemoveFillersChanged = model::setRemoveFillers,
                    onSpokenCorrectionsChanged = model::setSpokenCorrections,
                    onAiRefinementChanged = model::setAiRefinement,
                    onCleanupLevelChanged = model::setCleanupLevel,
                    onCleanupPromptChanged = model::setCleanupPrompt,
                    onTranscriptionModelChanged = model::setTranscriptionModel,
                    onRefinementModelChanged = model::setRefinementModel,
                    onHapticsChanged = model::setHaptics,
                    onPlaySoundsChanged = model::setPlaySounds,
                    onMuteMusicChanged = model::setMuteMusic,
                    onReduceMotionChanged = model::setReduceMotion,
                    onPrivacyChanged = model::setPrivacy,
                    onRetentionChanged = model::setRetention,
                    onAppearanceChanged = model::setAppearance,
                    onBubbleSizeChanged = model::setBubbleSize,
                    onBubbleOpacityChanged = model::setBubbleOpacity,
                    onIdleBehaviorChanged = model::setIdleBehavior,
                    onSaveApiKey = model::saveApiKey,
                    onClearApiKey = model::clearApiKey,
                    onUseMockEnginesChanged = model::setUseMockEngines,
                    onRefinementPromptChanged = model::setRefinementPrompt,
                    onSnooze = model::snooze,
                    onWake = model::wake,
                    onRestartService = model::restartService,
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel?.refreshCapabilities()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestNotifications() {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun copyText(text: String) {
        mainViewModel?.message(
            if (SensitiveClipboard.copy(this, text)) "Copied temporarily" else "Could not copy the text",
        )
    }

    private fun shareText(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text),
                "Share dictation",
            ),
        )
    }

    private fun shareAudio(file: java.io.File) {
        runCatching {
            val exportDirectory = java.io.File(cacheDir, "audio_exports")
            check(exportDirectory.exists() || exportDirectory.mkdirs()) { "Could not prepare audio export" }
            pruneStaleAudioExports()
            val extension = file.extension.takeIf(String::isNotBlank) ?: "m4a"
            val exportFile = java.io.File(exportDirectory, "flowerwhisp-${UUID.randomUUID()}.$extension")
            file.copyTo(exportFile, overwrite = false)
            val uri = FileProvider.getUriForFile(this, "$packageName.files", exportFile)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("audio/mp4")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Export recording",
                ),
            )
            Handler(mainLooper).postDelayed(
                { runCatching { exportFile.delete() } },
                AUDIO_EXPORT_LIFETIME_MS,
            )
        }.onFailure { failure ->
            mainViewModel?.message(failure.message ?: "Could not export the recording")
        }
    }

    private fun pruneStaleAudioExports() {
        val directory = java.io.File(cacheDir, "audio_exports")
        if (!directory.isDirectory) return
        val cutoff = System.currentTimeMillis() - STALE_AUDIO_EXPORT_AGE_MS
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    private companion object {
        const val AUDIO_EXPORT_LIFETIME_MS = 10 * 60 * 1_000L
        const val STALE_AUDIO_EXPORT_AGE_MS = 60 * 60 * 1_000L
    }
}
