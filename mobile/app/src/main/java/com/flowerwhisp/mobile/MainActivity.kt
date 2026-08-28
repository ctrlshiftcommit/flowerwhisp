package com.flowerwhisp.mobile

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
                    onRetry = { uiState.selectedDictation?.id?.let(model::retryHistory) },
                    onCopy = ::copyText,
                    onOpenApp = { model.navigate(FlowerWhispDestination.HOME) },
                    onSearchHistory = model::searchHistory,
                    onOpenHistory = model::openHistory,
                    onCloseHistory = model::closeHistory,
                    onFavoriteHistory = model::favoriteHistory,
                    onDeleteHistory = model::deleteHistory,
                    onCopyHistory = { id -> model.withHistoryText(id, ::copyText) },
                    onShareHistory = { id -> model.withHistoryText(id, ::shareText) },
                    onRetryHistory = model::retryHistory,
                    onAddDictionary = model::upsertDictionary,
                    onLibrarySectionChanged = model::selectLibrary,
                    onEditDictionary = model::upsertDictionary,
                    onDeleteDictionary = model::deleteDictionary,
                    onAddSnippet = model::upsertSnippet,
                    onEditSnippet = model::upsertSnippet,
                    onDeleteSnippet = model::deleteSnippet,
                    onWritingStyleChanged = model::setWritingStyle,
                    onLanguageChanged = model::setLanguage,
                    onAutoPunctuationChanged = model::setAutoPunctuation,
                    onRemoveFillersChanged = model::setRemoveFillers,
                    onSpokenCorrectionsChanged = model::setSpokenCorrections,
                    onAiRefinementChanged = model::setAiRefinement,
                    onHapticsChanged = model::setHaptics,
                    onReduceMotionChanged = model::setReduceMotion,
                    onPrivacyChanged = model::setPrivacy,
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
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("FlowerWhisp dictation", text))
        mainViewModel?.message("Copied to clipboard")
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
}
