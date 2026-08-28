package com.flowerwhisp.mobile.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.material.icons.outlined.Insights
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import com.flowerwhisp.mobile.domain.insights.InsightSnapshot
import com.flowerwhisp.mobile.domain.insights.calculateInsights
import com.flowerwhisp.mobile.R
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
import com.flowerwhisp.mobile.ui.theme.Clay
import com.flowerwhisp.mobile.ui.theme.ClayStrong
import com.flowerwhisp.mobile.ui.theme.FlowerWhispTheme
import com.flowerwhisp.mobile.ui.theme.Ink
import com.flowerwhisp.mobile.ui.theme.MutedText
import com.flowerwhisp.mobile.ui.theme.Outline
import com.flowerwhisp.mobile.ui.theme.PrimaryText
import com.flowerwhisp.mobile.ui.theme.Resolved
import com.flowerwhisp.mobile.ui.theme.SecondaryText
import com.flowerwhisp.mobile.ui.theme.SurfaceElevated
import com.flowerwhisp.mobile.ui.theme.SurfaceInk
import com.flowerwhisp.mobile.ui.theme.SurfaceSelected
import com.flowerwhisp.mobile.ui.theme.Warning
import java.text.DateFormat
import java.util.Date

@Composable
fun FlowerWhispApp(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    FlowerWhispTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
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
        Box(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    DestinationRail(uiState.destination, actions.onNavigate)
                    HorizontalDivider(Modifier.fillMaxHeight().width(1.dp), color = Outline.copy(alpha = 0.72f))
                    AnimatedDestinationContent(uiState, actions, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    AnimatedDestinationContent(uiState, actions, Modifier.weight(1f))
                    DestinationBar(uiState.destination, actions.onNavigate)
                }
            }
        }
    }
}

@Composable
private fun AnimatedDestinationContent(uiState: FlowerWhispUiState, actions: FlowerWhispActions, modifier: Modifier) {
    AnimatedContent(
        targetState = uiState.destination,
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(120))).using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "destination-transition",
    ) { destination ->
        DestinationContent(destination, uiState, actions, Modifier.fillMaxSize())
    }
}

@Composable
private fun DestinationContent(
    destination: FlowerWhispDestination,
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (destination) {
            FlowerWhispDestination.HOME -> DictationScreen(uiState, actions)
            FlowerWhispDestination.INSIGHTS -> InsightsScreen(uiState)
            FlowerWhispDestination.HISTORY -> HistoryScreen(uiState, actions)
            FlowerWhispDestination.LIBRARY -> LibraryScreen(uiState, actions)
            FlowerWhispDestination.SETTINGS -> SettingsScreen(uiState, actions)
        }
    }
}

@Composable
private fun DestinationBar(selected: FlowerWhispDestination, onNavigate: (FlowerWhispDestination) -> Unit) {
    Surface(
        color = SurfaceInk,
        contentColor = PrimaryText,
        border = BorderStroke(1.dp, Outline.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        FlowerWhispDestination.entries.forEach { destination ->
            DestinationNavItem(destination, selected == destination, onNavigate)
        }
        }
    }
}

@Composable
private fun DestinationRail(selected: FlowerWhispDestination, onNavigate: (FlowerWhispDestination) -> Unit) {
    Surface(
        color = SurfaceInk,
        contentColor = PrimaryText,
        modifier = Modifier.fillMaxHeight().width(112.dp),
    ) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 14.dp)) {
            Image(
                painter = painterResource(R.drawable.flowerwhisp_logo),
                contentDescription = "FlowerWhisp",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            )
            Text(
                "FLOWERWHISP",
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryText,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp, bottom = 22.dp),
            )
        FlowerWhispDestination.entries.forEach { destination ->
            DestinationRailItem(destination, selected == destination, onNavigate)
        }
            Spacer(Modifier.weight(1f))
            Text("PRIVATE BY DEFAULT", style = MaterialTheme.typography.labelMedium, color = MutedText, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun RowScope.DestinationNavItem(destination: FlowerWhispDestination, selected: Boolean, onNavigate: (FlowerWhispDestination) -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button) { onNavigate(destination) }
            .testTag("nav-${destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .background(if (selected) Clay else Color.Transparent, RoundedCornerShape(50)),
        )
        Icon(destination.icon(), contentDescription = null, tint = if (selected) Clay else SecondaryText, modifier = Modifier.size(21.dp))
        Text(destination.label, style = MaterialTheme.typography.labelMedium, color = if (selected) PrimaryText else SecondaryText, maxLines = 1)
    }
}

@Composable
private fun DestinationRailItem(destination: FlowerWhispDestination, selected: Boolean, onNavigate: (FlowerWhispDestination) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(role = Role.Button) { onNavigate(destination) }
            .background(if (selected) SurfaceSelected else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("nav-${destination.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(destination.icon(), contentDescription = null, tint = if (selected) Clay else SecondaryText, modifier = Modifier.size(21.dp))
        Text(destination.label, style = MaterialTheme.typography.labelLarge, color = if (selected) PrimaryText else SecondaryText)
    }
}

private fun FlowerWhispDestination.icon(): ImageVector = when (this) {
    FlowerWhispDestination.HOME -> Icons.Outlined.GraphicEq
    FlowerWhispDestination.INSIGHTS -> Icons.Outlined.Insights
    FlowerWhispDestination.HISTORY -> Icons.Outlined.History
    FlowerWhispDestination.LIBRARY -> Icons.AutoMirrored.Outlined.LibraryBooks
    FlowerWhispDestination.SETTINGS -> Icons.Outlined.Settings
}

@Composable
private fun OnboardingScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    val step = uiState.onboardingStep
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
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.flowerwhisp_logo),
                    contentDescription = "FlowerWhisp",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.size(42.dp),
                )
                Text("FLOWERWHISP", style = MaterialTheme.typography.labelLarge, color = SecondaryText, modifier = Modifier.padding(start = 9.dp).weight(1f))
                Text(
                    "Skip setup",
                    style = MaterialTheme.typography.labelLarge,
                    color = SecondaryText,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = actions.onSkipOnboarding)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
            OnboardingProgress(step)
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(220)) togetherWith fadeOut(tween(140))).using(SizeTransform(clip = false))
                },
                label = "onboarding-step",
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OnboardingGlyph(current)
                    val currentDetails = onboardingDetails(current)
                    Text(currentDetails.kicker.uppercase(), style = MaterialTheme.typography.labelMedium, color = Clay)
                    Text(currentDetails.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                    Text(currentDetails.body, style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
                    when (current) {
                        OnboardingStep.ACCESS -> AccessChecklist(uiState, actions)
                        OnboardingStep.MICROPHONE -> MicrophonePermissionCard(uiState, actions)
                        OnboardingStep.TEST -> TestPreview(actions)
                        OnboardingStep.READY -> ReadyChecklist(uiState)
                        OnboardingStep.WELCOME -> WelcomeExample()
                    }
                }
            }
            OnboardingAction(step, uiState, actions)
        }
    }
}

private data class OnboardingDetails(val kicker: String, val title: String, val body: String)

private fun onboardingDetails(step: OnboardingStep): OnboardingDetails = when (step) {
    OnboardingStep.WELCOME -> OnboardingDetails("A quieter way to write", "Write what you mean", "Speak naturally. FlowerWhisp turns your voice into clean text wherever you type, without replacing your keyboard.")
    OnboardingStep.ACCESS -> OnboardingDetails("One-time access", "Keep the bubble close", "Choose where FlowerWhisp can appear and how it can insert text. You can change these permissions any time in Settings.")
    OnboardingStep.MICROPHONE -> OnboardingDetails("Your voice, on request", "Give your words a voice", "Microphone access is used only while you are recording a dictation. Nothing starts until you tap the bubble.")
    OnboardingStep.TEST -> OnboardingDetails("Make it real", "Try one short dictation", "Focus a normal text field, tap FlowerWhisp, and say a sentence. The first successful insertion is the whole point of setup.")
    OnboardingStep.READY -> OnboardingDetails("Setup check", "Ready when you are", "The essentials are in place. Start from the Dictate tab or use the bubble in any supported text field.")
}

@Composable
private fun OnboardingAction(step: OnboardingStep, uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    when (step) {
        OnboardingStep.WELCOME -> PrimaryAction("Set up in about a minute", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.ACCESS) })
        OnboardingStep.ACCESS -> when {
            !uiState.capabilities.overlayEnabled -> PrimaryAction("Allow bubble overlay", Icons.Outlined.Layers, onClick = actions.onRequestOverlay)
            !uiState.capabilities.accessibilityEnabled -> PrimaryAction("Enable text insertion", Icons.Outlined.AccessibilityNew, onClick = actions.onRequestAccessibility)
            else -> PrimaryAction("Continue to microphone", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.MICROPHONE) })
        }
        OnboardingStep.MICROPHONE -> if (uiState.capabilities.microphoneGranted) {
            PrimaryAction("Continue to the test", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.TEST) })
        } else PrimaryAction("Allow microphone", Icons.Outlined.Mic, onClick = actions.onRequestMicrophone)
        OnboardingStep.TEST -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryAction("Start a short test", Icons.Outlined.Mic, onClick = actions.onOnboardingRealTest)
            SecondaryAction("Continue to final check", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.READY) })
        }
        OnboardingStep.READY -> {
            val repair = firstRepair(uiState)
            if (repair == null) PrimaryAction("Start dictating", Icons.Outlined.Check, onClick = actions.onCompleteOnboarding)
            else PrimaryAction(repair.label, repair.icon, onClick = repair.action(actions))
        }
    }
}

@Composable
private fun OnboardingProgress(step: OnboardingStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingStep.entries.forEach { item ->
            Box(
                Modifier
                    .weight(1f)
                    .height(if (item == step) 5.dp else 3.dp)
                    .background(if (item.ordinal <= step.ordinal) Clay else Outline, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun OnboardingGlyph(step: OnboardingStep) {
    val icon = when (step) {
        OnboardingStep.WELCOME -> Icons.Outlined.GraphicEq
        OnboardingStep.ACCESS -> Icons.Outlined.Layers
        OnboardingStep.MICROPHONE -> Icons.Outlined.Mic
        OnboardingStep.TEST -> Icons.Outlined.TouchApp
        OnboardingStep.READY -> Icons.Outlined.CheckCircle
    }
    Surface(
        color = Clay.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Clay, modifier = Modifier.padding(15.dp))
    }
}

@Composable
private fun WelcomeExample() {
    FeatureSurface {
        Text("Say", style = MaterialTheme.typography.labelMedium, color = Clay)
        Text("“Send the recap by Friday.”", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(18) { index ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((5 + (index * 7 % 16)).dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (index % 5 == 0) Clay else SecondaryText.copy(alpha = 0.62f)),
                )
            }
        }
    }
}

@Composable
private fun AccessChecklist(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermissionRow(
            title = "Bubble overlay",
            description = "Keep the dictation control above the app you are writing in.",
            ready = uiState.capabilities.overlayEnabled,
            action = actions.onRequestOverlay,
        )
        PermissionRow(
            title = "Text insertion",
            description = "Place the finished text at the cursor in the focused field.",
            ready = uiState.capabilities.accessibilityEnabled,
            action = actions.onRequestAccessibility,
        )
    }
}

@Composable
private fun MicrophonePermissionCard(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    PermissionRow(
        title = "Microphone",
        description = "Used only while FlowerWhisp is actively recording.",
        ready = uiState.capabilities.microphoneGranted,
        action = actions.onRequestMicrophone,
    )
}

@Composable
private fun PermissionRow(title: String, description: String, ready: Boolean, action: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceInk, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = action)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(11.dp).background(if (ready) Clay else Warning, RoundedCornerShape(50)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        Text(if (ready) "Ready" else "Set up", style = MaterialTheme.typography.labelMedium, color = if (ready) Clay else Warning)
    }
}

@Composable
private fun TestPreview(actions: FlowerWhispActions) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Try the instrument", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FlowerWhispBubble(
                state = BubbleState.Ready,
                elapsedSeconds = 0,
                onStart = actions.onOnboardingRealTest,
                onFinish = {},
                onCancel = {},
                onRetry = {},
                onCopy = {},
                onOpenApp = {},
            )
        }
        Text("Focus a text field first. The test uses the same bubble you will use every day.", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReadyChecklist(uiState: FlowerWhispUiState) {
    FeatureSurface(selected = firstRepair(uiState) == null) {
        CapabilityLine("Bubble overlay", uiState.capabilities.overlayEnabled)
        CapabilityLine("Text insertion", uiState.capabilities.accessibilityEnabled)
        CapabilityLine("Microphone", uiState.capabilities.microphoneGranted)
        CapabilityLine("Notifications", uiState.capabilities.notificationsGranted, optional = true)
    }
}

@Composable
private fun CapabilityLine(label: String, ready: Boolean, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(
            if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = if (ready) Clay else Warning,
            modifier = Modifier.size(20.dp),
        )
        Text("$label · ${if (ready) "Ready" else if (optional) "Optional" else "Needs setup"}", style = MaterialTheme.typography.bodyMedium)
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
private fun DictationScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    ScreenColumn("dictation-screen") {
        ScreenHeader("Dictate", "Speak naturally. Keep your hands on the field.")
        val recoverable = uiState.history.firstOrNull {
            it.recoveryAudioPath != null && it.status != DictationStatus.COMPLETE
        }
        if (recoverable != null) {
            FeatureSurface {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Warning, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Recording recovered", style = MaterialTheme.typography.titleLarge)
                        Text("Your audio is safe. Retry the transcript without recording again.", color = SecondaryText)
                    }
                }
                PrimaryAction("Retry transcript", Icons.Outlined.Refresh) { actions.onRetryHistory(recoverable.id) }
            }
        }

        val repair = firstRepair(uiState)
        FeatureSurface(selected = repair == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(ready = repair == null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (repair == null) "Ready to dictate" else "Finish setup first", style = MaterialTheme.typography.titleLarge)
                    Text(if (repair == null) "The bubble is ready wherever you can type." else repair.label, color = SecondaryText)
                }
                Text(if (repair == null) "LIVE" else "SETUP", style = MaterialTheme.typography.labelMedium, color = if (repair == null) Clay else Warning)
            }
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                val state = if (uiState.bubbleState is BubbleState.Hidden) BubbleState.Ready else uiState.bubbleState
                FlowerWhispBubble(
                    state = state,
                    elapsedSeconds = uiState.elapsedSeconds,
                    onStart = actions.onStart,
                    onFinish = actions.onFinish,
                    onCancel = actions.onCancel,
                    onRetry = actions.onRetry,
                    onCopy = actions.onCopy,
                    onOpenApp = actions.onOpenApp,
                    reduceMotion = uiState.settings.reduceMotion,
                )
            }
            if (repair != null) {
                PrimaryAction(repair.label, repair.icon, onClick = repair.action(actions))
            } else {
                Text("Tap the instrument to start. Hold it for push-to-talk.", color = SecondaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }

        FeatureSurface {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = Clay, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Current voice setup", style = MaterialTheme.typography.titleMedium)
                    Text("${uiState.settings.language.displayName} · ${uiState.settings.writingStyle.displayName}", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Adjust in Settings", style = MaterialTheme.typography.labelMedium, color = Clay)
            }
        }

        SectionTitle("Access", "These checks keep the instrument reliable across apps.")
        CapabilityActionRow("Bubble overlay", uiState.capabilities.overlayEnabled, Icons.Outlined.Layers, actions.onRequestOverlay)
        RowDivider()
        CapabilityActionRow("Text insertion", uiState.capabilities.accessibilityEnabled, Icons.Outlined.AccessibilityNew, actions.onRequestAccessibility)
        RowDivider()
        CapabilityActionRow("Microphone", uiState.capabilities.microphoneGranted, Icons.Outlined.Mic, actions.onRequestMicrophone)
        RowDivider()
        CapabilityActionRow("Notifications", uiState.capabilities.notificationsGranted, Icons.Outlined.Notifications, actions.onRequestNotifications)

        RecentDictations(uiState.history, actions)

        uiState.serviceMessage?.let { message ->
            SectionTitle("Needs attention")
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
private fun RecentDictations(history: List<Dictation>, actions: FlowerWhispActions) {
    val recent = history.take(3)
    SectionTitle("Recent dictations")
    if (recent.isEmpty()) {
        Text("Your finished dictations will appear here.", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            recent.forEachIndexed { index, item ->
                RecentDictationRow(item, actions)
                if (index < recent.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun RecentDictationRow(item: Dictation, actions: FlowerWhispActions) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { actions.onOpenHistory(item.id) }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (item.status == DictationStatus.COMPLETE) Clay else Warning, RoundedCornerShape(50)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.refinedText.ifBlank { item.originalText }.ifBlank { "Untitled dictation" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text("${item.wordCount} words · ${formatDuration(item.durationMs)}", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        Text(formatDate(item.createdAtEpochMs), style = MaterialTheme.typography.labelMedium, color = MutedText)
    }
}

@Composable
private fun StatusDot(ready: Boolean) {
    Box(Modifier.size(12.dp).background(if (ready) Clay else Warning, RoundedCornerShape(50)))
}

@Composable
private fun InsightsScreen(uiState: FlowerWhispUiState) {
    val snapshot = remember(uiState.history) { calculateInsights(uiState.history) }
    ScreenColumn("insights-screen") {
        ScreenHeader("Insights", "A small, honest view of your dictation habit")
        if (!snapshot.hasData) {
            StatusPanel(Icons.Outlined.GraphicEq, "Your first insight is waiting", "Complete a dictation and FlowerWhisp will show your sessions, words, and speaking time here.", Clay)
        } else {
            InsightMetricGrid(snapshot)
            SectionTitle("Last seven days", "Completed dictations only")
            ActivityBars(snapshot)
            FeatureSurface {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = Clay, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Most used language", style = MaterialTheme.typography.titleMedium)
                        Text(snapshot.mostUsedLanguage?.displayName ?: "Not enough data yet", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    }
                }
            }
            Text("These numbers stay on this device and reflect completed records in History.", style = MaterialTheme.typography.bodyMedium, color = MutedText)
        }
    }
}

@Composable
private fun InsightMetricGrid(snapshot: InsightSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightMetric("Sessions", snapshot.totalSessions.toString(), "completed", Modifier.weight(1f))
            InsightMetric("Words", snapshot.totalWords.toString(), "refined", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightMetric("Speaking time", formatSpeakingTime(snapshot.speakingTimeMs), "recorded", Modifier.weight(1f))
            InsightMetric("Average", snapshot.averageWordsPerSession.toString(), "words / session", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InsightMetric(label: String, value: String, detail: String, modifier: Modifier) {
    Surface(modifier = modifier, color = SurfaceInk, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SecondaryText)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = PrimaryText)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        }
    }
}

@Composable
private fun ActivityBars(snapshot: InsightSnapshot) {
    val maxWords = snapshot.recentDays.maxOfOrNull { it.words }?.coerceAtLeast(1) ?: 1
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        snapshot.recentDays.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height((16f + 86f * day.words / maxWords).dp).background(if (day.words > 0) Clay else SurfaceSelected, RoundedCornerShape(7.dp)))
                Text(day.date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelMedium, color = MutedText)
            }
        }
    }
}

private fun formatSpeakingTime(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

@Composable
private fun CapabilityActionRow(label: String, ready: Boolean, icon: ImageVector, repair: () -> Unit) {
    ActionRow(
        icon = if (ready) Icons.Outlined.CheckCircle else icon,
        title = label,
        description = if (ready) "Available" else "Open the required Android setting",
        value = if (ready) "Ready" else "Fix",
        tint = if (ready) Clay else Warning,
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
                if (item.isFavorite) Clay else SecondaryText,
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
                if (item.isFavorite) Clay else PrimaryText,
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
    Surface(color = SurfaceInk, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun OutcomeLabel(status: DictationStatus) {
    val (label, tint) = when (status) {
        DictationStatus.COMPLETE -> "Processed" to Resolved
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
        containerColor = SurfaceInk,
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
        containerColor = SurfaceInk,
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
        ActionRow(Icons.Outlined.Layers, "Bubble overlay", if (uiState.capabilities.overlayEnabled) "Available" else "Required for the floating bubble", if (uiState.capabilities.overlayEnabled) "Ready" else "Fix", if (uiState.capabilities.overlayEnabled) Clay else Warning, actions.onRequestOverlay)
        RowDivider()
        ActionRow(Icons.Outlined.AccessibilityNew, "Text insertion", if (uiState.capabilities.accessibilityEnabled) "Available" else "Required to insert in the focused field", if (uiState.capabilities.accessibilityEnabled) "Ready" else "Fix", if (uiState.capabilities.accessibilityEnabled) Clay else Warning, actions.onRequestAccessibility)
        RowDivider()
        ActionRow(Icons.Outlined.Mic, "Microphone", if (uiState.capabilities.microphoneGranted) "Available" else "Required while recording", if (uiState.capabilities.microphoneGranted) "Ready" else "Fix", if (uiState.capabilities.microphoneGranted) Clay else Warning, actions.onRequestMicrophone)
        RowDivider()
        ActionRow(Icons.Outlined.Notifications, "Notifications", if (uiState.capabilities.notificationsGranted) "Available" else "Required for reliable foreground operation", if (uiState.capabilities.notificationsGranted) "Ready" else "Fix", if (uiState.capabilities.notificationsGranted) Clay else Warning, actions.onRequestNotifications)

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
    focusedBorderColor = Clay,
    unfocusedBorderColor = Outline,
    focusedLabelColor = Clay,
    unfocusedLabelColor = SecondaryText,
    cursorColor = Clay,
    focusedContainerColor = SurfaceInk,
    unfocusedContainerColor = SurfaceInk,
)

private fun formatDate(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs.coerceAtLeast(0) / 1_000)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
