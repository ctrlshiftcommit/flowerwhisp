package com.flowerwhisp.mobile.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowerwhisp.mobile.domain.model.BubbleOpacity
import com.flowerwhisp.mobile.domain.model.BubbleSize
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.IdleBehavior
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.ui.bubble.FlowerWhispBubble
import com.flowerwhisp.mobile.ui.components.ActionRow
import com.flowerwhisp.mobile.ui.components.FeatureSurface
import com.flowerwhisp.mobile.ui.components.MinimumIconButton
import com.flowerwhisp.mobile.ui.components.PrimaryAction
import com.flowerwhisp.mobile.ui.components.RowDivider
import com.flowerwhisp.mobile.ui.components.ScreenHeader
import com.flowerwhisp.mobile.ui.components.SecondaryAction
import com.flowerwhisp.mobile.ui.components.SectionTitle
import com.flowerwhisp.mobile.ui.components.SelectRow
import com.flowerwhisp.mobile.ui.components.SwitchRow
import com.flowerwhisp.mobile.ui.theme.Error
import com.flowerwhisp.mobile.ui.theme.FlowerWhispTheme
import com.flowerwhisp.mobile.ui.theme.Mint
import com.flowerwhisp.mobile.ui.theme.MintStrong
import com.flowerwhisp.mobile.ui.theme.OLEDBlack
import com.flowerwhisp.mobile.ui.theme.Outline
import com.flowerwhisp.mobile.ui.theme.PrimaryText
import com.flowerwhisp.mobile.ui.theme.SecondaryText
import com.flowerwhisp.mobile.ui.theme.SurfaceBlack
import com.flowerwhisp.mobile.ui.theme.SurfaceSelected
import com.flowerwhisp.mobile.ui.theme.Warning
import java.text.DateFormat
import java.util.Date

@Composable
fun FlowerWhispApp(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    FlowerWhispTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = OLEDBlack) {
            if (!uiState.onboardingComplete) {
                OnboardingScreen(uiState, actions)
            } else {
                AppShell(uiState, actions)
            }
        }
    }
}

@Composable
private fun AppShell(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                DestinationRail(uiState.destination, actions.onNavigate)
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp), color = Outline)
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = OLEDBlack,
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { padding -> DestinationContent(uiState, actions, Modifier.padding(padding)) }
            }
        } else {
            Scaffold(
                containerColor = OLEDBlack,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = { DestinationBar(uiState.destination, actions.onNavigate) },
            ) { padding -> DestinationContent(uiState, actions, Modifier.padding(padding)) }
        }
    }
}

@Composable
private fun DestinationContent(uiState: FlowerWhispUiState, actions: FlowerWhispActions, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        when (uiState.destination) {
            FlowerWhispDestination.HOME -> HomeScreen(uiState, actions)
            FlowerWhispDestination.HISTORY -> HistoryScreen(uiState, actions)
            FlowerWhispDestination.LIBRARY -> LibraryScreen(uiState, actions)
            FlowerWhispDestination.SETTINGS -> SettingsScreen(uiState, actions)
        }
    }
}

@Composable
private fun DestinationBar(selected: FlowerWhispDestination, onNavigate: (FlowerWhispDestination) -> Unit) {
    NavigationBar(containerColor = SurfaceBlack, contentColor = PrimaryText, tonalElevation = 0.dp) {
        FlowerWhispDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon(), contentDescription = null) },
                label = { Text(destination.label) },
                modifier = Modifier.heightIn(min = 64.dp).testTag("nav-${destination.name.lowercase()}"),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Mint,
                    selectedTextColor = PrimaryText,
                    indicatorColor = SurfaceSelected,
                    unselectedIconColor = SecondaryText,
                    unselectedTextColor = SecondaryText,
                ),
            )
        }
    }
}

@Composable
private fun DestinationRail(selected: FlowerWhispDestination, onNavigate: (FlowerWhispDestination) -> Unit) {
    NavigationRail(containerColor = SurfaceBlack, contentColor = PrimaryText, modifier = Modifier.width(96.dp)) {
        Spacer(Modifier.height(16.dp))
        FlowerWhispDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination,
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon(), contentDescription = null) },
                label = { Text(destination.label) },
                modifier = Modifier.heightIn(min = 64.dp).testTag("nav-${destination.name.lowercase()}"),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Mint,
                    selectedTextColor = PrimaryText,
                    indicatorColor = SurfaceSelected,
                    unselectedIconColor = SecondaryText,
                    unselectedTextColor = SecondaryText,
                ),
            )
        }
    }
}

private fun FlowerWhispDestination.icon(): ImageVector = when (this) {
    FlowerWhispDestination.HOME -> Icons.Outlined.Home
    FlowerWhispDestination.HISTORY -> Icons.Outlined.History
            FlowerWhispDestination.LIBRARY -> Icons.AutoMirrored.Outlined.LibraryBooks
    FlowerWhispDestination.SETTINGS -> Icons.Outlined.Settings
}

@Composable
private fun OnboardingScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    val step = uiState.onboardingStep
    val details = onboardingDetails(step)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(WindowInsets.safeDrawing.asPaddingValues()),
    ) {
        val horizontal = if (maxWidth > 700.dp) 64.dp else 24.dp
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FLOWERWHISP", style = MaterialTheme.typography.labelLarge, color = Mint)
                LinearProgressIndicator(
                    progress = { (step.ordinal + 1f) / OnboardingStep.entries.size },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Mint,
                    trackColor = SurfaceSelected,
                )
                Text("${step.ordinal + 1} of ${OnboardingStep.entries.size}", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
            FeatureSurface(selected = true) {
                Icon(details.icon, contentDescription = null, tint = Mint, modifier = Modifier.size(36.dp))
                Text(details.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Text(details.body, style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
                if (step in listOf(OnboardingStep.OVERLAY, OnboardingStep.ACCESSIBILITY, OnboardingStep.MICROPHONE, OnboardingStep.READY)) {
                    CapabilitySummary(uiState)
                }
            }
            OnboardingAction(step, uiState, actions)
        }
    }
}

private data class OnboardingDetails(val title: String, val body: String, val icon: ImageVector)

private fun onboardingDetails(step: OnboardingStep): OnboardingDetails = when (step) {
    OnboardingStep.MEET -> OnboardingDetails("Meet FlowerWhisp", "Speak in the field you already use. FlowerWhisp turns your words into polished text without replacing your keyboard.", Icons.Outlined.GraphicEq)
    OnboardingStep.BUBBLE -> OnboardingDetails("The bubble stays close", "The compact bubble appears over supported text fields. Tap it when you want to dictate.", Icons.Outlined.Widgets)
    OnboardingStep.OVERLAY -> OnboardingDetails("Allow the bubble", "Android needs display-over-other-apps access to place the bubble beside your work.", Icons.Outlined.Layers)
    OnboardingStep.ACCESSIBILITY -> OnboardingDetails("Enable text insertion", "Accessibility access lets FlowerWhisp find the focused editable field and insert only your dictated text.", Icons.Outlined.AccessibilityNew)
    OnboardingStep.MICROPHONE -> OnboardingDetails("Allow microphone access", "Microphone access is used only while recording your dictation.", Icons.Outlined.Mic)
    OnboardingStep.TAP -> OnboardingDetails("Tap to dictate", "Tap once to start. Tap Stop when you finish speaking.", Icons.Outlined.TouchApp)
    OnboardingStep.HOLD -> OnboardingDetails("Hold for quick dictation", "Press and hold the bubble while speaking. Release to finish.", Icons.Outlined.Stop)
    OnboardingStep.REAL_TEST -> OnboardingDetails("Run a real test", "Focus a normal text field in another app, return to the bubble, and dictate a short sentence.", Icons.Outlined.Keyboard)
    OnboardingStep.READY -> OnboardingDetails("Ready to write", "FlowerWhisp is ready only when the bubble, insertion, and microphone checks below are on.", Icons.Outlined.CheckCircle)
}

@Composable
private fun OnboardingAction(step: OnboardingStep, uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    when (step) {
        OnboardingStep.MEET -> PrimaryAction("Continue", onClick = { actions.onAdvanceOnboarding(OnboardingStep.BUBBLE) })
        OnboardingStep.BUBBLE -> PrimaryAction("Set up the bubble", onClick = { actions.onAdvanceOnboarding(OnboardingStep.OVERLAY) })
        OnboardingStep.OVERLAY -> if (uiState.capabilities.overlayEnabled) {
            PrimaryAction("Continue", Icons.Outlined.Check, onClick = { actions.onAdvanceOnboarding(OnboardingStep.ACCESSIBILITY) })
        } else PrimaryAction("Open overlay settings", Icons.Outlined.Layers, onClick = actions.onRequestOverlay)
        OnboardingStep.ACCESSIBILITY -> if (uiState.capabilities.accessibilityEnabled) {
            PrimaryAction("Continue", Icons.Outlined.Check, onClick = { actions.onAdvanceOnboarding(OnboardingStep.MICROPHONE) })
        } else PrimaryAction("Open Accessibility settings", Icons.Outlined.AccessibilityNew, onClick = actions.onRequestAccessibility)
        OnboardingStep.MICROPHONE -> if (uiState.capabilities.microphoneGranted) {
            PrimaryAction("Continue", Icons.Outlined.Check, onClick = { actions.onAdvanceOnboarding(OnboardingStep.TAP) })
        } else PrimaryAction("Allow microphone", Icons.Outlined.Mic, onClick = actions.onRequestMicrophone)
        OnboardingStep.TAP -> PrimaryAction("Try tap mode", Icons.Outlined.TouchApp, onClick = actions.onOnboardingTap)
        OnboardingStep.HOLD -> PrimaryAction("Try hold mode", Icons.Outlined.TouchApp, onClick = actions.onOnboardingHold)
        OnboardingStep.REAL_TEST -> PrimaryAction("Start real test", Icons.Outlined.Mic, onClick = actions.onOnboardingRealTest)
        OnboardingStep.READY -> {
            val repair = firstRepair(uiState)
            if (repair == null) PrimaryAction("Finish setup", Icons.Outlined.Check, onClick = actions.onCompleteOnboarding)
            else PrimaryAction(repair.label, repair.icon, onClick = repair.action(actions))
        }
    }
}

@Composable
private fun CapabilitySummary(uiState: FlowerWhispUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CapabilityLine("Bubble overlay", uiState.capabilities.overlayEnabled)
        CapabilityLine("Text insertion", uiState.capabilities.accessibilityEnabled)
        CapabilityLine("Microphone", uiState.capabilities.microphoneGranted)
    }
}

@Composable
private fun CapabilityLine(label: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = if (ready) MintStrong else Warning,
            modifier = Modifier.size(20.dp),
        )
        Text("$label: ${if (ready) "On" else "Needs setup"}", style = MaterialTheme.typography.bodyMedium)
    }
}

private data class Repair(val label: String, val icon: ImageVector, val kind: Int) {
    fun action(actions: FlowerWhispActions): () -> Unit = when (kind) {
        0 -> actions.onRequestOverlay
        1 -> actions.onRequestAccessibility
        2 -> actions.onRequestMicrophone
        else -> actions.onRequestNotifications
    }
}

private fun firstRepair(uiState: FlowerWhispUiState): Repair? = when {
    !uiState.capabilities.overlayEnabled -> Repair("Allow bubble overlay", Icons.Outlined.Layers, 0)
    !uiState.capabilities.accessibilityEnabled -> Repair("Enable text insertion", Icons.Outlined.AccessibilityNew, 1)
    !uiState.capabilities.microphoneGranted -> Repair("Allow microphone", Icons.Outlined.Mic, 2)
    else -> null
}

@Composable
private fun HomeScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    ScreenColumn("home-screen") {
        ScreenHeader("Home", "Live readiness and dictation controls")
        val recoverable = uiState.history.firstOrNull {
            it.recoveryAudioPath != null && it.status != DictationStatus.COMPLETE
        }
        if (recoverable != null) {
            FeatureSurface(selected = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Warning, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your recording is safe", style = MaterialTheme.typography.titleLarge)
                        Text("The previous dictation did not finish. Retry it without recording again.", color = SecondaryText)
                    }
                }
                PrimaryAction("Retry transcript", Icons.Outlined.Refresh) {
                    actions.onRetryHistory(recoverable.id)
                }
            }
        }
        val repair = firstRepair(uiState)
        FeatureSurface(selected = repair == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (repair == null) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null, tint = if (repair == null) MintStrong else Warning, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (repair == null) "Ready to dictate" else "Action required", style = MaterialTheme.typography.titleLarge)
                    Text(if (repair == null) "Bubble, insertion, and microphone are available." else repair!!.label, color = SecondaryText)
                }
            }
            if (repair != null) PrimaryAction(repair.label, repair.icon, onClick = repair.action(actions))
            else PrimaryAction("Start dictation", Icons.Outlined.Mic, onClick = actions.onStart)
        }

        SectionTitle("Bubble")
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            FlowerWhispBubble(
                state = uiState.bubbleState,
                elapsedSeconds = uiState.elapsedSeconds,
                onStart = actions.onStart,
                onFinish = actions.onFinish,
                onCancel = actions.onCancel,
                onRetry = actions.onRetry,
                onCopy = actions.onCopy,
                onOpenApp = actions.onOpenApp,
            )
        }

        SectionTitle("Capabilities")
        CapabilityActionRow("Bubble overlay", uiState.capabilities.overlayEnabled, Icons.Outlined.Layers, actions.onRequestOverlay)
        RowDivider()
        CapabilityActionRow("Text insertion", uiState.capabilities.accessibilityEnabled, Icons.Outlined.AccessibilityNew, actions.onRequestAccessibility)
        RowDivider()
        CapabilityActionRow("Microphone", uiState.capabilities.microphoneGranted, Icons.Outlined.Mic, actions.onRequestMicrophone)
        RowDivider()
        CapabilityActionRow("Notifications", uiState.capabilities.notificationsGranted, Icons.Outlined.Notifications, actions.onRequestNotifications)

        uiState.serviceMessage?.let { message ->
            SectionTitle("Service")
            FeatureSurface {
                Text(message, color = Error)
                PrimaryAction("Restart service", Icons.Outlined.RestartAlt, onClick = actions.onRestartService)
            }
        }
        if (uiState.settings.snoozedUntilEpochMs > System.currentTimeMillis() || uiState.bubbleState is BubbleState.Snoozed) {
            PrimaryAction("Wake bubble", Icons.Outlined.CheckCircle, onClick = actions.onWake)
        } else {
            SecondaryAction("Snooze bubble", Icons.Outlined.Snooze, onClick = actions.onSnooze)
        }
    }
}

@Composable
private fun CapabilityActionRow(label: String, ready: Boolean, icon: ImageVector, repair: () -> Unit) {
    ActionRow(
        icon = if (ready) Icons.Outlined.CheckCircle else icon,
        title = label,
        description = if (ready) "Available" else "Open the required Android setting",
        value = if (ready) "On" else "Repair",
        tint = if (ready) MintStrong else Warning,
        onClick = repair,
    )
}

@Composable
private fun HistoryScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    val selected = uiState.selectedDictation
    if (selected != null) {
        HistoryDetail(selected, actions)
        return
    }
    ScreenColumn("history-screen") {
        ScreenHeader("History", "Raw speech, refined text, and processing outcome")
        OutlinedTextField(
            value = uiState.historyQuery,
            onValueChange = actions.onSearchHistory,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("history-search"),
            label = { Text("Search history") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            colors = fieldColors(),
        )
        when {
            uiState.historyLoading -> StatusPanel(Icons.Outlined.History, "Loading history", "Your saved dictations are being loaded.")
            uiState.historyError != null -> {
                StatusPanel(Icons.Outlined.ErrorOutline, "History unavailable", uiState.historyError, Error)
                PrimaryAction("Retry", Icons.Outlined.Refresh, onClick = actions.onRetry)
            }
            uiState.history.isEmpty() && uiState.historyQuery.isNotBlank() -> StatusPanel(Icons.Outlined.Search, "No matches", "Try a different word or clear the search.")
            uiState.history.isEmpty() -> StatusPanel(Icons.Outlined.History, "No dictations yet", "Finished dictations will appear here with their raw and refined text.")
            else -> uiState.history.forEach { item ->
                HistoryListItem(item, actions)
                RowDivider()
            }
        }
    }
}

@Composable
private fun HistoryListItem(item: Dictation, actions: FlowerWhispActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { actions.onOpenHistory(item.id) }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatDate(item.createdAtEpochMs), style = MaterialTheme.typography.labelMedium, color = SecondaryText, modifier = Modifier.weight(1f))
            OutcomeLabel(item.status)
            MinimumIconButton(
                if (item.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                if (item.isFavorite) "Remove favorite" else "Add favorite",
                if (item.isFavorite) Mint else SecondaryText,
            ) { actions.onFavoriteHistory(item.id, !item.isFavorite) }
        }
        Text(item.refinedText.ifBlank { item.originalText }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Text("${item.wordCount} words · ${formatDuration(item.durationMs)} · ${item.language.displayName}", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    }
}

@Composable
private fun HistoryDetail(item: Dictation, actions: FlowerWhispActions) {
    ScreenColumn("history-detail") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MinimumIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to history", onClick = actions.onCloseHistory)
            Text("Dictation detail", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
            MinimumIconButton(
                if (item.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                if (item.isFavorite) "Remove favorite" else "Add favorite",
                if (item.isFavorite) Mint else PrimaryText,
            ) { actions.onFavoriteHistory(item.id, !item.isFavorite) }
        }
        Text(formatDate(item.createdAtEpochMs), color = SecondaryText)
        SectionTitle("Raw")
        TranscriptBlock(item.originalText.ifBlank { "No raw transcript was saved." })
        SectionTitle("Refined")
        TranscriptBlock(item.refinedText.ifBlank { "No refined text was produced." })
        SectionTitle("Outcome")
        FeatureSurface {
            OutcomeLabel(item.status)
            Text(outcomeDescription(item.status), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            if (item.recoveryAudioPath != null) Text("Recovery audio is available.", style = MaterialTheme.typography.bodyMedium, color = Warning)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Copy", Icons.Outlined.ContentCopy) { actions.onCopyHistory(item.id) }
            SecondaryAction("Share", Icons.Outlined.Share) { actions.onShareHistory(item.id) }
        }
        if (item.status != DictationStatus.COMPLETE) PrimaryAction("Retry processing", Icons.Outlined.Refresh) { actions.onRetryHistory(item.id) }
        SecondaryAction("Delete", Icons.Outlined.DeleteOutline) { actions.onDeleteHistory(item.id) }
    }
}

@Composable
private fun TranscriptBlock(text: String) {
    Surface(color = SurfaceBlack, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun OutcomeLabel(status: DictationStatus) {
    val (label, tint) = when (status) {
        DictationStatus.COMPLETE -> "Processed" to MintStrong
        DictationStatus.RECORDING -> "Recording" to Warning
        DictationStatus.PROCESSING -> "Processing" to Warning
        DictationStatus.INSERTION_FAILED -> "Not inserted" to Error
        DictationStatus.TRANSCRIPTION_FAILED -> "Transcription failed" to Error
        DictationStatus.REFINEMENT_FAILED -> "Refinement failed" to Error
        DictationStatus.CANCELLED -> "Cancelled" to SecondaryText
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
}

private fun outcomeDescription(status: DictationStatus): String = when (status) {
    DictationStatus.COMPLETE -> "Transcription and refinement completed. This record does not claim insertion unless the insertion layer reports it separately."
    DictationStatus.RECORDING -> "Recording had not finished when this record was saved."
    DictationStatus.PROCESSING -> "Processing had not finished when this record was saved."
    DictationStatus.INSERTION_FAILED -> "Text was produced but direct insertion failed. Copy the refined text to recover."
    DictationStatus.TRANSCRIPTION_FAILED -> "Audio could not be transcribed. Retry is available when recovery audio exists."
    DictationStatus.REFINEMENT_FAILED -> "Raw text exists, but refinement failed."
    DictationStatus.CANCELLED -> "This dictation was cancelled."
}

@Composable
private fun LibraryScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    ScreenColumn("library-screen") {
        ScreenHeader("Library", "Words, reusable text, and writing style")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LibrarySection.entries.forEach { section ->
                SelectRow(section.label, selected = uiState.librarySection == section) { actions.onLibrarySectionChanged(section) }
            }
        }
        when (uiState.librarySection) {
            LibrarySection.DICTIONARY -> DictionarySection(uiState.dictionary, actions)
            LibrarySection.SNIPPETS -> SnippetSection(uiState.snippets, actions)
            LibrarySection.STYLE -> StyleSection(uiState.settings.writingStyle, actions)
        }
    }
}

@Composable
private fun DictionarySection(entries: List<DictionaryEntry>, actions: FlowerWhispActions) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DictionaryEntry?>(null) }
    PrimaryAction("Add word", Icons.Outlined.Add, onClick = { adding = true })
    if (entries.isEmpty()) StatusPanel(Icons.AutoMirrored.Outlined.LibraryBooks, "Dictionary is empty", "Add names or terms FlowerWhisp should preserve.")
    entries.forEach { entry ->
        EditableItem(
            title = entry.spelling,
            description = listOf(entry.pronunciationOrContext, entry.replacement).filter(String::isNotBlank).joinToString(" · ").ifBlank { "Preserve this spelling" },
            onEdit = { editing = entry },
            onDelete = { actions.onDeleteDictionary(entry.id) },
        )
    }
    if (adding || editing != null) {
        DictionaryEditorDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { value ->
                if (editing == null) actions.onAddDictionary(value) else actions.onEditDictionary(value)
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun SnippetSection(snippets: List<Snippet>, actions: FlowerWhispActions) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    PrimaryAction("Add snippet", Icons.Outlined.Add, onClick = { adding = true })
    if (snippets.isEmpty()) StatusPanel(Icons.AutoMirrored.Outlined.TextSnippet, "No snippets", "Add a spoken trigger and the text it should expand to.")
    snippets.forEach { snippet ->
        EditableItem(snippet.trigger, snippet.expansion, { editing = snippet }, { actions.onDeleteSnippet(snippet.id) })
    }
    if (adding || editing != null) {
        SnippetEditorDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { value ->
                if (editing == null) actions.onAddSnippet(value) else actions.onEditSnippet(value)
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun DictionaryEditorDialog(
    initial: DictionaryEntry?,
    onDismiss: () -> Unit,
    onSave: (DictionaryEntry) -> Unit,
) {
    var spelling by remember(initial) { mutableStateOf(initial?.spelling.orEmpty()) }
    var context by remember(initial) { mutableStateOf(initial?.pronunciationOrContext.orEmpty()) }
    var replacement by remember(initial) { mutableStateOf(initial?.replacement.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add dictionary word" else "Edit dictionary word") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(spelling, { spelling = it }, label = { Text("Correct spelling") }, singleLine = true)
                OutlinedTextField(context, { context = it }, label = { Text("Pronunciation or context") })
                OutlinedTextField(replacement, { replacement = it }, label = { Text("Optional replacement") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = spelling.isNotBlank(),
                onClick = {
                    onSave(
                        DictionaryEntry(
                            id = initial?.id ?: 0,
                            spelling = spelling.trim(),
                            pronunciationOrContext = context.trim(),
                            replacement = replacement.trim(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = SurfaceBlack,
    )
}

@Composable
private fun SnippetEditorDialog(
    initial: Snippet?,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit,
) {
    var trigger by remember(initial) { mutableStateOf(initial?.trigger.orEmpty()) }
    var expansion by remember(initial) { mutableStateOf(initial?.expansion.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add snippet" else "Edit snippet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(trigger, { trigger = it }, label = { Text("Spoken trigger") }, singleLine = true)
                OutlinedTextField(expansion, { expansion = it }, label = { Text("Expanded text") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(
                enabled = trigger.isNotBlank() && expansion.isNotBlank(),
                onClick = { onSave(Snippet(initial?.id ?: 0, trigger.trim(), expansion.trim())) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = SurfaceBlack,
    )
}

@Composable
private fun EditableItem(title: String, description: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        MinimumIconButton(Icons.Outlined.Edit, "Edit $title", onClick = onEdit)
        MinimumIconButton(Icons.Outlined.DeleteOutline, "Delete $title", Error, onDelete)
    }
    RowDivider()
}

@Composable
private fun StyleSection(selected: WritingStyle, actions: FlowerWhispActions) {
    WritingStyle.entries.forEach { style ->
        SelectRow(style.displayName, style.instruction, style == selected) { actions.onWritingStyleChanged(style) }
    }
}

@Composable
private fun SettingsScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    ScreenColumn("settings-screen") {
        ScreenHeader("Settings", "Dictation, bubble, provider, and privacy controls")

        SectionTitle("Language")
        LanguageMode.entries.forEach { mode ->
            SelectRow(mode.displayName, selected = uiState.settings.language == mode) { actions.onLanguageChanged(mode) }
        }

        SectionTitle("Dictation")
        SwitchRow("Automatic punctuation", "Add sentence boundaries and punctuation.", uiState.settings.autoPunctuation, actions.onAutoPunctuationChanged)
        RowDivider()
        SwitchRow("Remove filler words", "Remove fillers only when meaning stays intact.", uiState.settings.removeFillers, actions.onRemoveFillersChanged)
        RowDivider()
        SwitchRow("Spoken corrections", "Keep the final version after a spoken correction.", uiState.settings.spokenCorrections, actions.onSpokenCorrectionsChanged)
        RowDivider()
        SwitchRow("AI refinement", "Polish the transcript using the selected writing style.", uiState.settings.aiRefinement, actions.onAiRefinementChanged)

        SectionTitle("Writing style")
        WritingStyle.entries.forEach { style ->
            SelectRow(style.displayName, selected = uiState.settings.writingStyle == style) { actions.onWritingStyleChanged(style) }
        }

        SectionTitle("Bubble size")
        BubbleSize.entries.forEach { size -> SelectRow(size.name.lowercase().replaceFirstChar(Char::uppercase), selected = uiState.settings.bubbleSize == size) { actions.onBubbleSizeChanged(size) } }
        SectionTitle("Bubble opacity")
        BubbleOpacity.entries.forEach { opacity -> SelectRow(opacity.name.lowercase().replaceFirstChar(Char::uppercase), selected = uiState.settings.bubbleOpacity == opacity) { actions.onBubbleOpacityChanged(opacity) } }
        SectionTitle("Idle bubble")
        IdleBehavior.entries.forEach { behavior ->
            val description = if (behavior == IdleBehavior.SHRINK) "Use the compact ready state." else "Keep the full ready control visible."
            SelectRow(behavior.name.lowercase().replaceFirstChar(Char::uppercase), description, uiState.settings.idleBehavior == behavior) { actions.onIdleBehaviorChanged(behavior) }
        }

        SectionTitle("Feedback and motion")
        SwitchRow("Haptics", "Confirm start and finish with touch feedback.", uiState.settings.haptics, actions.onHapticsChanged)
        RowDivider()
        SwitchRow("Reduce motion", "Use immediate state changes without scale animation.", uiState.settings.reduceMotion, actions.onReduceMotionChanged)

        SectionTitle("Provider key")
        SwitchRow(
            "Mock development mode",
            "Use deterministic local sample output. Turn this off to send audio and refinement requests to Groq.",
            uiState.settings.useMockEngines,
            actions.onUseMockEnginesChanged,
        )
        RowDivider()
        Text(if (uiState.groqApiKeyConfigured) "A Groq API key is saved securely." else "No Groq API key is saved.", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            label = { Text("Groq API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            colors = fieldColors(),
        )
        PrimaryAction("Save API key", Icons.Outlined.Lock, enabled = apiKey.isNotBlank()) {
            actions.onSaveApiKey(apiKey.trim())
            apiKey = ""
        }
        if (uiState.groqApiKeyConfigured) SecondaryAction("Clear saved key", Icons.Outlined.DeleteOutline, actions.onClearApiKey)

        SectionTitle("Refinement prompt")
        OutlinedTextField(
            value = uiState.refinementPromptDraft,
            onValueChange = actions.onRefinementPromptChanged,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            label = { Text("Instructions") },
            minLines = 5,
            colors = fieldColors(),
        )

        SectionTitle("Privacy")
        SwitchRow(
            "Privacy mode",
            "Do not retain successful transcripts in History. Failed recordings remain available for recovery. Cloud processing still follows the provider selected above.",
            uiState.settings.privacyMode,
            actions.onPrivacyChanged,
        )

        SectionTitle("Android access")
        ActionRow(Icons.Outlined.Layers, "Bubble overlay", if (uiState.capabilities.overlayEnabled) "Available" else "Required for the floating bubble", if (uiState.capabilities.overlayEnabled) "On" else "Repair", if (uiState.capabilities.overlayEnabled) MintStrong else Warning, actions.onRequestOverlay)
        RowDivider()
        ActionRow(Icons.Outlined.AccessibilityNew, "Text insertion", if (uiState.capabilities.accessibilityEnabled) "Available" else "Required to insert in the focused field", if (uiState.capabilities.accessibilityEnabled) "On" else "Repair", if (uiState.capabilities.accessibilityEnabled) MintStrong else Warning, actions.onRequestAccessibility)
        RowDivider()
        ActionRow(Icons.Outlined.Mic, "Microphone", if (uiState.capabilities.microphoneGranted) "Available" else "Required while recording", if (uiState.capabilities.microphoneGranted) "On" else "Repair", if (uiState.capabilities.microphoneGranted) MintStrong else Warning, actions.onRequestMicrophone)
        RowDivider()
        ActionRow(Icons.Outlined.Notifications, "Notifications", if (uiState.capabilities.notificationsGranted) "Available" else "Required for reliable foreground operation", if (uiState.capabilities.notificationsGranted) "On" else "Repair", if (uiState.capabilities.notificationsGranted) MintStrong else Warning, actions.onRequestNotifications)

        SectionTitle("Service")
        if (uiState.settings.snoozedUntilEpochMs > System.currentTimeMillis() || uiState.bubbleState is BubbleState.Snoozed) {
            PrimaryAction("Wake bubble", Icons.Outlined.CheckCircle, onClick = actions.onWake)
        } else SecondaryAction("Snooze bubble", Icons.Outlined.Snooze, actions.onSnooze)
        SecondaryAction("Restart service", Icons.Outlined.RestartAlt, actions.onRestartService)
    }
}

@Composable
private fun ScreenColumn(tag: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun StatusPanel(icon: ImageVector, title: String, description: String, tint: Color = SecondaryText) {
    FeatureSurface {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PrimaryText,
    unfocusedTextColor = PrimaryText,
    focusedBorderColor = Mint,
    unfocusedBorderColor = Outline,
    focusedLabelColor = Mint,
    unfocusedLabelColor = SecondaryText,
    cursorColor = Mint,
    focusedContainerColor = SurfaceBlack,
    unfocusedContainerColor = SurfaceBlack,
)

private fun formatDate(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs.coerceAtLeast(0) / 1_000)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
