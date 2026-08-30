package com.flowerwhisp.mobile.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowerwhisp.mobile.domain.model.BubbleOpacity
import com.flowerwhisp.mobile.domain.model.BubbleSize
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.AppearanceMode
import com.flowerwhisp.mobile.domain.model.CleanupLevel
import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.DictionaryScope
import com.flowerwhisp.mobile.domain.model.IdleBehavior
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.RetentionMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.StyleContext
import com.flowerwhisp.mobile.domain.model.TransformProfile
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.model.cleanupPrompt
import com.flowerwhisp.mobile.domain.model.styleFor
import com.flowerwhisp.mobile.domain.model.styleInstructionsFor
import com.flowerwhisp.mobile.domain.insights.InsightSnapshot
import com.flowerwhisp.mobile.domain.insights.calculateInsights
import com.flowerwhisp.mobile.R
import com.flowerwhisp.mobile.ui.bubble.FlowerWhispBubble
import com.flowerwhisp.mobile.ui.components.ActionRow
import com.flowerwhisp.mobile.ui.components.CompactAction
import com.flowerwhisp.mobile.ui.components.CompactToggle
import com.flowerwhisp.mobile.ui.components.FeatureSurface
import com.flowerwhisp.mobile.ui.components.FlowerWhispTextField
import com.flowerwhisp.mobile.ui.components.MinimumIconButton
import com.flowerwhisp.mobile.ui.components.PrimaryAction
import com.flowerwhisp.mobile.ui.components.RowDivider
import com.flowerwhisp.mobile.ui.components.ScreenHeader
import com.flowerwhisp.mobile.ui.components.SecondaryAction
import com.flowerwhisp.mobile.ui.components.SectionTitle
import com.flowerwhisp.mobile.ui.components.SelectRow
import com.flowerwhisp.mobile.ui.components.SwitchRow
import com.flowerwhisp.mobile.ui.components.ValueRow
import com.flowerwhisp.mobile.ui.components.WhispDialog
import com.flowerwhisp.mobile.ui.components.whispSurface
import com.flowerwhisp.mobile.ui.theme.Error
import com.flowerwhisp.mobile.ui.theme.Clay
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
    FlowerWhispTheme(appearanceMode = uiState.settings.appearanceMode) {
        CompositionLocalProvider(LocalContentColor provides PrimaryText) {
            Box(modifier = Modifier.fillMaxSize().background(Ink)) {
                if (!uiState.onboardingComplete) {
                    OnboardingScreen(uiState, actions)
                } else {
                    AppShell(uiState, actions)
                }
                uiState.transformPreview?.let { preview ->
                    TransformPreviewDialog(preview, actions)
                }
            }
        }
    }
}

@Composable
private fun AppShell(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val compactDrawerWidth = maxWidth * 0.84f
        var drawerOpen by rememberSaveable { mutableStateOf(false) }
        val navigate: (FlowerWhispDestination) -> Unit = { destination ->
            drawerOpen = false
            actions.onNavigate(destination)
        }
        BackHandler(enabled = drawerOpen) { drawerOpen = false }
        Box(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    DestinationDrawer(
                        selected = uiState.destination,
                        onNavigate = navigate,
                        modifier = Modifier.fillMaxHeight().width(224.dp),
                        floating = false,
                    )
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Outline.copy(alpha = 0.72f)))
                    AnimatedDestinationContent(uiState, actions, null, Modifier.weight(1f))
                }
            } else {
                AnimatedDestinationContent(
                    uiState = uiState,
                    actions = actions,
                    onOpenDrawer = { drawerOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = if (uiState.settings.reduceMotion) EnterTransition.None else fadeIn(tween(140)),
                    exit = if (uiState.settings.reduceMotion) ExitTransition.None else fadeOut(tween(100)),
                ) {
                    val scrimInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.62f))
                            .clickable(
                                interactionSource = scrimInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = { drawerOpen = false },
                            )
                            .semantics { contentDescription = "Close navigation" },
                    )
                }
                AnimatedVisibility(
                    visible = drawerOpen,
                    modifier = Modifier.align(Alignment.CenterStart),
                    enter = if (uiState.settings.reduceMotion) {
                        EnterTransition.None
                    } else {
                        slideInHorizontally(tween(180)) { -it }
                    },
                    exit = if (uiState.settings.reduceMotion) {
                        ExitTransition.None
                    } else {
                        slideOutHorizontally(tween(140)) { -it }
                    },
                ) {
                    DestinationDrawer(
                        selected = uiState.destination,
                        onNavigate = navigate,
                        modifier = Modifier.fillMaxHeight().width(compactDrawerWidth),
                        floating = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedDestinationContent(
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    onOpenDrawer: (() -> Unit)?,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = uiState.destination,
        transitionSpec = {
            if (uiState.settings.reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(180)) togetherWith fadeOut(tween(120))).using(SizeTransform(clip = false))
            }
        },
        modifier = modifier,
        label = "destination-transition",
    ) { destination ->
        DestinationContent(destination, uiState, actions, onOpenDrawer, Modifier.fillMaxSize())
    }
}

@Composable
private fun DestinationContent(
    destination: FlowerWhispDestination,
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    onOpenDrawer: (() -> Unit)?,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (destination) {
            FlowerWhispDestination.HOME -> DictationScreen(uiState, actions, onOpenDrawer)
            FlowerWhispDestination.HISTORY -> HistoryScreen(uiState, actions, onOpenDrawer)
            FlowerWhispDestination.DICTIONARY -> LibraryScreen(uiState, actions, LibrarySection.DICTIONARY, onOpenDrawer)
            FlowerWhispDestination.SNIPPETS -> LibraryScreen(uiState, actions, LibrarySection.SNIPPETS, onOpenDrawer)
            FlowerWhispDestination.STYLE -> LibraryScreen(uiState, actions, LibrarySection.STYLE, onOpenDrawer)
            FlowerWhispDestination.TRANSFORMS -> TransformsScreen(uiState, actions, onOpenDrawer)
            FlowerWhispDestination.SCRATCHPAD -> ScratchpadScreen(uiState, actions, onOpenDrawer)
            FlowerWhispDestination.INSIGHTS -> InsightsScreen(uiState, onOpenDrawer)
            FlowerWhispDestination.SETTINGS -> SettingsScreen(uiState, actions, onOpenDrawer)
        }
    }
}

@Composable
private fun DestinationDrawer(
    selected: FlowerWhispDestination,
    onNavigate: (FlowerWhispDestination) -> Unit,
    modifier: Modifier,
    floating: Boolean,
) {
    val drawerInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .whispSurface(
                color = SurfaceInk,
                shape = if (floating) {
                    RoundedCornerShape(0.dp)
                } else {
                    RoundedCornerShape(0.dp)
                },
                borderColor = if (floating) Outline else null,
            )
            .clickable(
                interactionSource = drawerInteraction,
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.flowerwhisp_logo),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(34.dp),
            )
            Text("FlowerWhisp", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FlowerWhispDestination.entries.filterNot { it == FlowerWhispDestination.SETTINGS }.forEach { destination ->
                DestinationDrawerItem(destination, selected == destination, onNavigate)
            }
        }
        RowDivider()
        DestinationDrawerItem(
            FlowerWhispDestination.SETTINGS,
            selected == FlowerWhispDestination.SETTINGS,
            onNavigate,
        )
    }
}

@Composable
private fun DestinationDrawerItem(
    destination: FlowerWhispDestination,
    selected: Boolean,
    onNavigate: (FlowerWhispDestination) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (pressed) SurfaceSelected else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = { onNavigate(destination) },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("nav-${destination.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(3.dp).height(22.dp).background(if (selected) Clay else Color.Transparent, RoundedCornerShape(50)))
        Icon(destination.icon(), contentDescription = null, tint = if (selected) PrimaryText else SecondaryText, modifier = Modifier.size(20.dp))
        Text(destination.label, style = MaterialTheme.typography.bodyLarge, color = if (selected) PrimaryText else SecondaryText)
    }
}

@Composable
private fun DrawerTrigger(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lineColor = if (pressed) Clay else PrimaryText
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) SurfaceSelected else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Open navigation" }
            .testTag("open-navigation"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.6.dp.toPx()
            val left = 2.dp.toPx()
            val right = size.width - 2.dp.toPx()
            listOf(5.dp.toPx(), 11.dp.toPx(), 17.dp.toPx()).forEach { y ->
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun DestinationHeader(title: String, onOpenDrawer: (() -> Unit)?) {
    if (onOpenDrawer == null) {
        ScreenHeader(title)
    } else {
        ScreenHeader(title = title, leading = { DrawerTrigger(onOpenDrawer) })
    }
}

private fun FlowerWhispDestination.icon(): ImageVector = when (this) {
    FlowerWhispDestination.HOME -> Icons.Outlined.GraphicEq
    FlowerWhispDestination.HISTORY -> Icons.Outlined.History
    FlowerWhispDestination.DICTIONARY -> Icons.AutoMirrored.Outlined.LibraryBooks
    FlowerWhispDestination.SNIPPETS -> Icons.AutoMirrored.Outlined.TextSnippet
    FlowerWhispDestination.STYLE -> Icons.Outlined.FormatPaint
    FlowerWhispDestination.TRANSFORMS -> Icons.Outlined.Refresh
    FlowerWhispDestination.SCRATCHPAD -> Icons.Outlined.Edit
    FlowerWhispDestination.INSIGHTS -> Icons.Outlined.Insights
    FlowerWhispDestination.SETTINGS -> Icons.Outlined.Settings
}

@Composable
private fun OnboardingScreen(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    val step = uiState.onboardingStep
    // API keys must never enter saved-instance state or process-restoration bundles.
    var providerKey by remember { mutableStateOf("") }
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
                Text("FlowerWhisp", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 9.dp).weight(1f))
            }
            OnboardingProgress(step)
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (uiState.settings.reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(220)) togetherWith fadeOut(tween(140))).using(SizeTransform(clip = false))
                    }
                },
                label = "onboarding-step",
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val currentDetails = onboardingDetails(current)
                    Text(currentDetails.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                    currentDetails.body?.let { body ->
                        Text(body, style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
                    }
                    when (current) {
                        OnboardingStep.ACCESS -> AccessChecklist(uiState, actions)
                        OnboardingStep.MICROPHONE -> MicrophonePermissionCard(uiState, actions)
                        OnboardingStep.PROVIDER -> ProviderSetup(
                            apiKey = providerKey,
                            onApiKeyChange = { providerKey = it },
                            configured = uiState.groqApiKeyConfigured,
                        )
                        OnboardingStep.TEST -> TestPreview(uiState, actions)
                        OnboardingStep.READY -> ReadyChecklist(uiState)
                        OnboardingStep.WELCOME -> Unit
                    }
                    uiState.serviceMessage?.takeIf(String::isNotBlank)?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = if (message.contains("saved", ignoreCase = true)) Clay else SecondaryText)
                    }
                }
            }
            OnboardingAction(
                step = step,
                uiState = uiState,
                providerKey = providerKey,
                actions = actions,
                onProviderKeyConsumed = { providerKey = "" },
            )
        }
    }
}

private data class OnboardingDetails(val title: String, val body: String? = null)

private fun onboardingDetails(step: OnboardingStep): OnboardingDetails = when (step) {
    OnboardingStep.WELCOME -> OnboardingDetails("Dictate anywhere")
    OnboardingStep.ACCESS -> OnboardingDetails("Access")
    OnboardingStep.MICROPHONE -> OnboardingDetails("Microphone")
    OnboardingStep.PROVIDER -> OnboardingDetails("Connect Groq", "Transcription and cleanup")
    OnboardingStep.TEST -> OnboardingDetails("Test dictation")
    OnboardingStep.READY -> OnboardingDetails("Ready")
}

@Composable
private fun OnboardingAction(
    step: OnboardingStep,
    uiState: FlowerWhispUiState,
    providerKey: String,
    actions: FlowerWhispActions,
    onProviderKeyConsumed: () -> Unit,
) {
    when (step) {
        OnboardingStep.WELCOME -> PrimaryAction("Continue", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.ACCESS) })
        OnboardingStep.ACCESS -> when {
            !uiState.capabilities.overlayEnabled -> PrimaryAction("Allow overlay", Icons.Outlined.Layers, onClick = actions.onRequestOverlay)
            !uiState.capabilities.textInsertionReady -> PrimaryAction("Enable insertion", Icons.Outlined.AccessibilityNew, onClick = actions.onRequestAccessibility)
            else -> PrimaryAction("Continue", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.MICROPHONE) })
        }
        OnboardingStep.MICROPHONE -> if (uiState.capabilities.microphoneGranted) {
            PrimaryAction("Continue", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.PROVIDER) })
        } else PrimaryAction("Allow microphone", Icons.Outlined.Mic, onClick = actions.onRequestMicrophone)
        OnboardingStep.PROVIDER -> if (uiState.groqApiKeyConfigured) {
            PrimaryAction("Continue", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.TEST) })
        } else {
            PrimaryAction(
                "Save key",
                Icons.Outlined.Lock,
                enabled = providerKey.trim().length >= 10,
                onClick = {
                    actions.onSaveApiKey(providerKey.trim())
                    onProviderKeyConsumed()
                },
            )
        }
        OnboardingStep.TEST -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (uiState.bubbleState is BubbleState.Recording) {
                PrimaryAction("Finish test", Icons.Outlined.Stop, onClick = actions.onFinish)
            } else {
                PrimaryAction("Start test", Icons.Outlined.Mic, onClick = actions.onOnboardingRealTest)
            }
            SecondaryAction("Continue", Icons.AutoMirrored.Outlined.ArrowForward, onClick = { actions.onAdvanceOnboarding(OnboardingStep.READY) })
        }
        OnboardingStep.READY -> {
            val repair = firstRepair(uiState)
            when {
                !uiState.groqApiKeyConfigured && !uiState.settings.useMockEngines -> {
                    PrimaryAction("Connect Groq", Icons.Outlined.Lock) {
                        actions.onAdvanceOnboarding(OnboardingStep.PROVIDER)
                    }
                }
                repair == null -> PrimaryAction("Finish setup", Icons.Outlined.Check, onClick = actions.onCompleteOnboarding)
                else -> PrimaryAction(repair.label, repair.icon, onClick = repair.action(actions))
            }
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
        OnboardingStep.PROVIDER -> Icons.Outlined.Lock
        OnboardingStep.TEST -> Icons.Outlined.TouchApp
        OnboardingStep.READY -> Icons.Outlined.CheckCircle
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .whispSurface(color = Clay.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp), borderColor = null),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Clay, modifier = Modifier.size(26.dp))
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
            title = "Overlay",
            ready = uiState.capabilities.overlayEnabled,
            action = actions.onRequestOverlay,
        )
        PermissionRow(
            title = "Text insertion",
            ready = uiState.capabilities.textInsertionReady,
            action = actions.onRequestAccessibility,
        )
    }
}

@Composable
private fun MicrophonePermissionCard(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    PermissionRow(
        title = "Microphone",
        ready = uiState.capabilities.microphoneGranted,
        action = actions.onRequestMicrophone,
    )
}

@Composable
private fun PermissionRow(title: String, description: String? = null, ready: Boolean, action: () -> Unit) {
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
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SecondaryText) }
        }
        Text(if (ready) "On" else "Set up", style = MaterialTheme.typography.labelMedium, color = if (ready) Clay else Warning)
    }
}

@Composable
private fun ProviderSetup(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    configured: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Groq Cloud", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (configured) "Saved" else "No key", style = MaterialTheme.typography.labelMedium, color = if (configured) Clay else SecondaryText)
        }
        if (!configured) {
            FlowerWhispTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = "Groq API key",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                supportingText = "Stored with Android Keystore",
            )
        }
    }
}

@Composable
private fun TestPreview(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    var testText by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowerWhispTextField(
            value = testText,
            onValueChange = { testText = it },
            label = "Tap here first",
            minLines = 3,
            supportingText = "Start, speak, then finish",
        )
        if (uiState.bubbleState !is BubbleState.Ready && uiState.bubbleState !is BubbleState.Hidden) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FlowerWhispBubble(
                    state = uiState.bubbleState,
                    elapsedSeconds = uiState.elapsedSeconds,
                    onStart = actions.onOnboardingRealTest,
                    onFinish = actions.onFinish,
                    onCancel = actions.onCancel,
                    onRetry = actions.onRetry,
                    onCopy = actions.onCopy,
                    onOpenApp = actions.onOpenApp,
                    reduceMotion = uiState.settings.reduceMotion,
                    hapticsEnabled = uiState.settings.haptics,
                )
            }
        }
    }
}

@Composable
private fun ReadyChecklist(uiState: FlowerWhispUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CapabilityLine("Bubble overlay", uiState.capabilities.overlayEnabled)
        CapabilityLine("Text insertion", uiState.capabilities.textInsertionReady)
        CapabilityLine("Microphone", uiState.capabilities.microphoneGranted)
        CapabilityLine("Groq", uiState.groqApiKeyConfigured)
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
    !uiState.capabilities.textInsertionReady -> Repair("Enable text insertion", Icons.Outlined.AccessibilityNew, 1)
    !uiState.capabilities.microphoneGranted -> Repair("Allow microphone", Icons.Outlined.Mic, 2)
    else -> null
}

@Composable
private fun DictationScreen(
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    onOpenDrawer: (() -> Unit)?,
) {
    ScreenColumn("dictation-screen") {
        DestinationHeader("Dictation", onOpenDrawer)
        val recoverable = uiState.history.firstOrNull {
            it.recoveryAudioPath != null && it.status != DictationStatus.COMPLETE
        }
        if (recoverable != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusDot(false)
                Text("Recording saved", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "Retry",
                    color = Clay,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(role = Role.Button) { actions.onRetryHistory(recoverable.id) }.padding(12.dp),
                )
            }
        }

        val repair = firstRepair(uiState)
        val providerReady = uiState.settings.useMockEngines || uiState.groqApiKeyConfigured
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusDot(ready = repair == null && providerReady)
                Text(
                    when {
                        !providerReady -> "Groq key required"
                        repair != null -> repair.label
                        else -> "Ready"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (uiState.bubbleState.isActiveDictationState()) {
                DictationInstrument(uiState, actions)
            } else if (providerReady && repair == null) {
                Text("Focus a text field. Use the bubble.", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
            if (!providerReady) {
                SecondaryAction("Open provider settings", Icons.Outlined.Lock) {
                    actions.onNavigate(FlowerWhispDestination.SETTINGS)
                }
            } else if (repair != null) {
                PrimaryAction(repair.label, repair.icon, onClick = repair.action(actions))
            }
        }

        uiState.serviceMessage?.takeIf(String::isNotBlank)?.let { message ->
            Text(message, color = if (uiState.bubbleState is BubbleState.ServiceError || uiState.bubbleState is BubbleState.AccessibilityError) Error else SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
        if (uiState.settings.snoozedUntilEpochMs > System.currentTimeMillis() || uiState.bubbleState is BubbleState.Snoozed) {
            PrimaryAction("Wake bubble", Icons.Outlined.CheckCircle, onClick = actions.onWake)
        }
        if (uiState.history.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction("Copy last", Icons.Outlined.ContentCopy, actions.onCopyLastTranscript)
                SecondaryAction("All history", Icons.Outlined.History) {
                    actions.onNavigate(FlowerWhispDestination.HISTORY)
                }
            }
        }
        RecentDictations(uiState.history, actions)
    }
}

private fun BubbleState.isActiveDictationState(): Boolean = when (this) {
    BubbleState.Hidden, BubbleState.Ready, is BubbleState.Snoozed -> false
    else -> true
}

@Composable
private fun DictationInstrument(uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    val state = uiState.bubbleState
    val waveformColor = when (state) {
        is BubbleState.Recording -> Clay
        is BubbleState.Success -> Resolved
        is BubbleState.AccessibilityError, is BubbleState.ServiceError -> Error
        else -> SecondaryText
    }
    val liveLevel = (state as? BubbleState.Recording)?.level?.coerceIn(0f, 1f) ?: 0.14f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 38.dp)) {
            val centerY = size.height / 2f
            val gap = size.width / 24f
            repeat(25) { index ->
                val distance = kotlin.math.abs(index - 12) / 12f
                val pattern = 0.35f + ((index * 7) % 9) / 12f
                val height = size.height * (0.12f + (1f - distance * 0.55f) * pattern * (0.18f + liveLevel * 0.62f))
                val x = index * gap
                drawLine(
                    color = waveformColor.copy(alpha = if (index in 10..14) 0.9f else 0.42f),
                    start = androidx.compose.ui.geometry.Offset(x, centerY - height / 2f),
                    end = androidx.compose.ui.geometry.Offset(x, centerY + height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Box(Modifier.align(Alignment.Center)) {
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
                hapticsEnabled = uiState.settings.haptics,
            )
        }
    }
}

@Composable
private fun RecentDictations(history: List<Dictation>, actions: FlowerWhispActions) {
    val recent = history.take(3)
    SectionTitle("Recent")
    if (recent.isEmpty()) {
        Text("No dictations yet", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
private fun InsightsScreen(uiState: FlowerWhispUiState, onOpenDrawer: (() -> Unit)?) {
    val snapshot = remember(uiState.history) { calculateInsights(uiState.history) }
    ScreenColumn("insights-screen") {
        DestinationHeader("Insights", onOpenDrawer)
        if (!snapshot.hasData) {
            StatusPanel(Icons.Outlined.GraphicEq, "No data", tint = Clay)
        } else {
            InsightMetricGrid(snapshot)
            SectionTitle("Last seven days")
            ActivityBars(snapshot)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Language", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(snapshot.mostUsedLanguage?.displayName ?: "—", style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
            }
        }
    }
}

@Composable
private fun InsightMetricGrid(snapshot: InsightSnapshot) {
    Column {
        InsightMetric("Sessions", snapshot.totalSessions.toString())
        RowDivider()
        InsightMetric("Words", snapshot.totalWords.toString())
        RowDivider()
        InsightMetric("Speaking time", formatSpeakingTime(snapshot.speakingTimeMs))
        RowDivider()
        InsightMetric("Average words", snapshot.averageWordsPerSession.toString())
    }
}

@Composable
private fun InsightMetric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = PrimaryText)
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
private fun HistoryScreen(
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    onOpenDrawer: (() -> Unit)?,
) {
    val selected = uiState.selectedDictation
    if (selected != null) {
        HistoryDetail(selected, uiState, actions)
        return
    }
    val visibleHistory = remember(uiState.history, uiState.historyQuery) {
        val query = uiState.historyQuery.trim()
        if (query.isEmpty()) {
            uiState.history
        } else {
            uiState.history.filter { item ->
                item.originalText.contains(query, ignoreCase = true) ||
                    item.safeText.contains(query, ignoreCase = true) ||
                    item.refinedText.contains(query, ignoreCase = true)
            }
        }
    }
    ScreenColumn("history-screen") {
        DestinationHeader("History", onOpenDrawer)
        FlowerWhispTextField(
            value = uiState.historyQuery,
            onValueChange = actions.onSearchHistory,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("history-search"),
            label = "Search history",
            leadingContent = { Icon(Icons.Outlined.Search, contentDescription = null, tint = SecondaryText) },
            singleLine = true,
        )
        when {
            uiState.historyLoading -> StatusPanel(Icons.Outlined.History, "Loading")
            uiState.historyError != null -> {
                StatusPanel(Icons.Outlined.ErrorOutline, "History unavailable", uiState.historyError, Error)
                PrimaryAction("Retry", Icons.Outlined.Refresh, onClick = actions.onRefreshHistory)
            }
            visibleHistory.isEmpty() && uiState.historyQuery.isNotBlank() -> StatusPanel(Icons.Outlined.Search, "No matches")
            uiState.history.isEmpty() -> StatusPanel(Icons.Outlined.History, "No dictations")
            else -> visibleHistory.forEach { item ->
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
private fun HistoryDetail(item: Dictation, uiState: FlowerWhispUiState, actions: FlowerWhispActions) {
    var showActions by rememberSaveable(item.id) { mutableStateOf(false) }
    var pendingDelete by remember(item.id) { mutableStateOf<HistoryDeleteTarget?>(null) }
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
        SectionTitle("Transcript")
        TranscriptBlock(item.refinedText.ifBlank { item.safeText.ifBlank { item.originalText } })
        if (item.originalText.isNotBlank() && item.originalText != item.refinedText) {
            SectionTitle("Original")
            TranscriptBlock(item.originalText)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutcomeLabel(item.status)
            Text(cleanupLabel(item.cleanupStatus), style = MaterialTheme.typography.labelMedium, color = SecondaryText)
            Spacer(Modifier.weight(1f))
            if (item.recoveryAudioPath != null) Text("Audio saved", style = MaterialTheme.typography.labelMedium, color = Warning)
        }
        item.cleanupError?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Error) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (item.recoveryAudioPath != null) {
                MinimumIconButton(
                    if (uiState.playingDictationId == item.id) Icons.Outlined.Stop else Icons.Outlined.GraphicEq,
                    if (uiState.playingDictationId == item.id) "Stop audio" else "Play audio",
                    Clay,
                ) { actions.onPlayHistory(item.id) }
            }
            MinimumIconButton(Icons.Outlined.ContentCopy, "Copy transcript") { actions.onCopyHistory(item.id) }
            MinimumIconButton(Icons.Outlined.MoreHoriz, "More transcript actions") { showActions = true }
        }
    }
    if (showActions) {
        HistoryActionsDialog(
            item = item,
            transforms = uiState.transforms.filter { it.enabled },
            onDismiss = { showActions = false },
            onRequestDeleteAudio = {
                showActions = false
                pendingDelete = HistoryDeleteTarget.AUDIO
            },
            onRequestDeleteTranscript = {
                showActions = false
                pendingDelete = HistoryDeleteTarget.TRANSCRIPT
            },
            actions = actions,
        )
    }
    pendingDelete?.let { target ->
        DeleteConfirmationDialog(
            title = when (target) {
                HistoryDeleteTarget.AUDIO -> "Delete saved audio?"
                HistoryDeleteTarget.TRANSCRIPT -> if (item.recoveryAudioPath == null) {
                    "Delete this transcript?"
                } else {
                    "Delete this transcript and its audio?"
                }
            },
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                when (target) {
                    HistoryDeleteTarget.AUDIO -> actions.onDeleteHistoryAudio(item.id)
                    HistoryDeleteTarget.TRANSCRIPT -> actions.onDeleteHistory(item.id)
                }
            },
        )
    }
}

private enum class HistoryDeleteTarget { AUDIO, TRANSCRIPT }

@Composable
private fun HistoryActionsDialog(
    item: Dictation,
    transforms: List<TransformProfile>,
    onDismiss: () -> Unit,
    onRequestDeleteAudio: () -> Unit,
    onRequestDeleteTranscript: () -> Unit,
    actions: FlowerWhispActions,
) {
    WhispDialog(
        title = "Dictation actions",
        onDismiss = onDismiss,
        confirmLabel = "Done",
        showCancelAction = false,
        onConfirm = onDismiss,
    ) {
        if (item.originalText.isNotBlank() && item.originalText != item.refinedText) {
            ActionRow(Icons.AutoMirrored.Outlined.Undo, "Undo AI edit", onClick = {
                actions.onUndoHistoryCleanup(item.id)
                onDismiss()
            })
            RowDivider()
        }
        if (item.recoveryAudioPath != null) {
            ActionRow(Icons.Outlined.Refresh, "Retry transcript", onClick = {
                actions.onRetryHistory(item.id)
                onDismiss()
            })
            RowDivider()
            ActionRow(Icons.Outlined.AudioFile, "Extract audio", onClick = {
                actions.onExportHistoryAudio(item.id)
                onDismiss()
            })
            RowDivider()
        }
        ActionRow(Icons.Outlined.Share, "Share text", onClick = {
            actions.onShareHistory(item.id)
            onDismiss()
        })
        RowDivider()
        ActionRow(Icons.Outlined.Edit, "Send to Scratchpad", onClick = {
            actions.onSendHistoryToScratchpad(item.id)
            onDismiss()
        })
        if (transforms.isNotEmpty()) {
            SectionTitle("Transform")
            transforms.forEachIndexed { index, transform ->
                ActionRow(Icons.Outlined.Refresh, transform.name, onClick = {
                    actions.onRunHistoryTransform(item.id, transform.id)
                    onDismiss()
                })
                if (index < transforms.lastIndex) RowDivider()
            }
        }
        if (item.recoveryAudioPath != null) {
            SectionTitle("Recording")
            ActionRow(Icons.Outlined.DeleteOutline, "Delete saved audio", tint = Error, onClick = onRequestDeleteAudio)
        }
        SectionTitle("Delete")
        ActionRow(Icons.Outlined.DeleteOutline, "Delete transcript", tint = Error, onClick = onRequestDeleteTranscript)
    }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WhispDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = "Delete",
        confirmDanger = true,
        onConfirm = onConfirm,
    ) {}
}

@Composable
private fun TranscriptBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .whispSurface(color = SurfaceInk, shape = RoundedCornerShape(16.dp), borderColor = Outline),
    ) {
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

private fun cleanupLabel(status: CleanupStatus): String = when (status) {
    CleanupStatus.DISABLED -> "Cleanup off"
    CleanupStatus.APPLIED -> "Cleaned"
    CleanupStatus.UNCHANGED -> "Unchanged"
    CleanupStatus.FAILED -> "Safe fallback"
}

@Composable
private fun LibraryScreen(
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    section: LibrarySection,
    onOpenDrawer: (() -> Unit)?,
) {
    ScreenColumn("library-${section.name.lowercase()}") {
        DestinationHeader(section.label, onOpenDrawer)
        when (section) {
            LibrarySection.DICTIONARY -> DictionarySection(uiState.dictionary, actions)
            LibrarySection.SNIPPETS -> SnippetSection(uiState.snippets, actions)
            LibrarySection.STYLE -> StyleSection(uiState.settings, actions)
        }
    }
}

@Composable
private fun DictionarySection(entries: List<DictionaryEntry>, actions: FlowerWhispActions) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DictionaryEntry?>(null) }
    PrimaryAction("Add word", Icons.Outlined.Add, onClick = { adding = true })
    if (entries.isEmpty()) StatusPanel(Icons.AutoMirrored.Outlined.LibraryBooks, "No entries")
    entries.forEach { entry ->
        EditableItem(
            title = entry.spelling,
            description = listOf(entry.pronunciationOrContext, entry.replacement).filter(String::isNotBlank).joinToString(" · "),
            enabled = entry.enabled,
            onEnabledChange = { actions.onDictionaryEnabledChanged(entry, it) },
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
    if (snippets.isEmpty()) StatusPanel(Icons.AutoMirrored.Outlined.TextSnippet, "No snippets")
    snippets.forEach { snippet ->
        EditableItem(
            title = snippet.trigger,
            description = snippet.expansion,
            enabled = snippet.enabled,
            onEnabledChange = { actions.onSnippetEnabledChanged(snippet, it) },
            onEdit = { editing = snippet },
            onDelete = { actions.onDeleteSnippet(snippet.id) },
        )
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
    var correctMisspelling by remember(initial) {
        mutableStateOf(initial == null || initial.replacement.isNotBlank() && initial.replacement != initial.spelling)
    }
    var scope by remember(initial) { mutableStateOf(initial?.scope ?: DictionaryScope.ALL) }
    var isProtected by remember(initial) { mutableStateOf(initial?.isProtected ?: true) }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    WhispDialog(
        title = if (initial == null) "Add to dictionary" else "Edit dictionary entry",
        onDismiss = onDismiss,
        confirmLabel = if (initial == null) "Add word" else "Save",
        confirmEnabled = spelling.isNotBlank() && (!correctMisspelling || replacement.isNotBlank()),
        onConfirm = {
            onSave(
                DictionaryEntry(
                    id = initial?.id ?: 0,
                    spelling = spelling.trim(),
                    pronunciationOrContext = context.trim(),
                    replacement = if (correctMisspelling) replacement.trim() else spelling.trim(),
                    scope = scope,
                    isProtected = isProtected,
                    enabled = enabled,
                ),
            )
        },
    ) {
        FlowerWhispTextField(spelling, { spelling = it }, label = "Word or phrase", singleLine = true)
        SwitchRow("Correct a misspelling", checked = correctMisspelling, onCheckedChange = { correctMisspelling = it })
        if (correctMisspelling) {
            FlowerWhispTextField(replacement, { replacement = it }, label = "Correct it to", singleLine = true)
        }
        FlowerWhispTextField(context, { context = it }, label = "Context", supportingText = "Optional")
        SectionTitle("Applies to")
        DictionaryScope.entries.forEach { value ->
            SelectRow(value.dictionaryLabel(), selected = scope == value) { scope = value }
        }
        SwitchRow("Protect spelling", "Keep it exact during cleanup", checked = isProtected, onCheckedChange = { isProtected = it })
    }
}

@Composable
private fun SnippetEditorDialog(
    initial: Snippet?,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit,
) {
    var trigger by remember(initial) { mutableStateOf(initial?.trigger.orEmpty()) }
    var expansion by remember(initial) { mutableStateOf(initial?.expansion.orEmpty()) }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    WhispDialog(
        title = if (initial == null) "Add snippet" else "Edit snippet",
        onDismiss = onDismiss,
        confirmLabel = if (initial == null) "Add snippet" else "Save",
        confirmEnabled = trigger.isNotBlank() && expansion.isNotBlank(),
        onConfirm = { onSave(Snippet(initial?.id ?: 0, trigger.trim(), expansion.trim(), enabled)) },
    ) {
        FlowerWhispTextField(trigger, { trigger = it }, label = "Trigger", singleLine = true)
        FlowerWhispTextField(expansion, { expansion = it }, label = "Expanded text", minLines = 3)
    }
}

@Composable
private fun EditableItem(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by rememberSaveable(title) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        CompactToggle(enabled, "$title enabled", onEnabledChange)
        MinimumIconButton(Icons.Outlined.MoreHoriz, "More actions for $title") { showActions = true }
    }
    RowDivider()
    if (showActions) {
        WhispDialog(
            title = title,
            onDismiss = { showActions = false },
            confirmLabel = "Done",
            showCancelAction = false,
            onConfirm = { showActions = false },
        ) {
            ActionRow(Icons.Outlined.Edit, "Edit", onClick = {
                showActions = false
                onEdit()
            })
            RowDivider()
            ActionRow(Icons.Outlined.DeleteOutline, "Delete", tint = Error, onClick = {
                showActions = false
                confirmDelete = true
            })
        }
    }
    if (confirmDelete) {
        DeleteConfirmationDialog(
            title = "Delete $title?",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

private fun DictionaryScope.dictionaryLabel(): String = when (this) {
    DictionaryScope.ALL -> "Everywhere"
    DictionaryScope.PERSONAL -> "Personal apps"
    DictionaryScope.WORK -> "Work apps"
}

@Composable
private fun StyleSection(
    settings: com.flowerwhisp.mobile.domain.model.AppSettings,
    actions: FlowerWhispActions,
) {
    var context by rememberSaveable { mutableStateOf(StyleContext.PERSONAL) }
    var editingInstructions by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleContext.entries.forEach { item ->
            CompactAction(
                label = item.displayName,
                accent = item == context,
                onClick = { context = item },
            )
        }
    }
    Text(context.appLabels(), style = MaterialTheme.typography.labelMedium, color = SecondaryText)
    val selected = settings.styleFor(context)
    context.availableStyles().forEach { style ->
        SelectRow(style.displayName, selected = style == selected) {
            actions.onContextWritingStyleChanged(context, style)
        }
    }
    ValueRow(
        title = "Instructions",
        value = if (settings.styleInstructionsFor(context).isBlank()) "Default" else "Custom",
        onClick = { editingInstructions = true },
    )
    SectionTitle("Cleanup")
    ValueRow("Auto cleanup", settings.cleanupLevel.displayName) {
        actions.onNavigate(FlowerWhispDestination.SETTINGS)
    }
    if (editingInstructions) {
        ContextStyleInstructionsDialog(
            context = context,
            initial = settings.styleInstructionsFor(context),
            onDismiss = { editingInstructions = false },
            onSave = { value ->
                actions.onContextStyleInstructionsChanged(context, value)
                editingInstructions = false
            },
        )
    }
}

@Composable
private fun ContextStyleInstructionsDialog(
    context: StyleContext,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var instructions by remember(context, initial) { mutableStateOf(initial) }
    WhispDialog(
        title = "${context.displayName} instructions",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = { onSave(instructions) },
    ) {
        FlowerWhispTextField(
            value = instructions,
            onValueChange = { instructions = it.take(2_000) },
            label = "Instructions",
            minLines = 5,
            supportingText = "Style only. Meaning stays unchanged.",
        )
    }
}

private fun StyleContext.availableStyles(): List<WritingStyle> = when (this) {
    StyleContext.PERSONAL -> listOf(WritingStyle.FORMAL, WritingStyle.CASUAL, WritingStyle.VERY_CASUAL)
    StyleContext.WORK -> listOf(WritingStyle.FORMAL, WritingStyle.CASUAL, WritingStyle.ENTHUSIASTIC)
    StyleContext.EMAIL -> listOf(WritingStyle.FORMAL, WritingStyle.CASUAL)
    StyleContext.OTHER -> listOf(WritingStyle.NATURAL, WritingStyle.PROFESSIONAL, WritingStyle.CONCISE)
}

private fun StyleContext.appLabels(): String = when (this) {
    StyleContext.PERSONAL -> "WhatsApp · Messages · Telegram · Signal"
    StyleContext.WORK -> "Slack · Teams · LinkedIn"
    StyleContext.EMAIL -> "Gmail · Outlook · Email"
    StyleContext.OTHER -> "Docs · Notes · Browsers"
}

@Composable
private fun TransformsScreen(
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    onOpenDrawer: (() -> Unit)?,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TransformProfile?>(null) }
    ScreenColumn("transforms-screen") {
        DestinationHeader("Transforms", onOpenDrawer)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CompactAction("New transform", Icons.Outlined.Add, onClick = { adding = true })
        }
        if (uiState.transforms.isEmpty()) StatusPanel(Icons.Outlined.Refresh, "No transforms")
        uiState.transforms.forEach { transform ->
            TransformItem(
                transform = transform,
                onEnabledChange = { actions.onSaveTransform(transform.copy(enabled = it)) },
                onEdit = { editing = transform },
                onDelete = { actions.onDeleteTransform(transform.id) },
            )
        }
    }
    if (adding || editing != null) {
        TransformEditorDialog(
            initial = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { transform ->
                actions.onSaveTransform(transform)
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun TransformItem(
    transform: TransformProfile,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by rememberSaveable(transform.id, transform.name) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(transform.id, transform.name) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(transform.name, style = MaterialTheme.typography.titleMedium)
            if (transform.description.isNotBlank()) {
                Text(transform.description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        CompactToggle(transform.enabled, "${transform.name} enabled", onEnabledChange)
        MinimumIconButton(Icons.Outlined.MoreHoriz, "More actions for ${transform.name}") { showActions = true }
    }
    RowDivider()
    if (showActions) {
        WhispDialog(
            title = transform.name,
            onDismiss = { showActions = false },
            confirmLabel = "Done",
            showCancelAction = false,
            onConfirm = { showActions = false },
        ) {
            ActionRow(Icons.Outlined.Edit, "Edit prompt", onClick = {
                showActions = false
                onEdit()
            })
            if (!transform.builtIn) {
                RowDivider()
                ActionRow(Icons.Outlined.DeleteOutline, "Delete", tint = Error, onClick = {
                    showActions = false
                    confirmDelete = true
                })
            }
        }
    }
    if (confirmDelete) {
        DeleteConfirmationDialog(
            title = "Delete ${transform.name}?",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun TransformEditorDialog(
    initial: TransformProfile?,
    onDismiss: () -> Unit,
    onSave: (TransformProfile) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var instructions by remember(initial) { mutableStateOf(initial?.instructions.orEmpty()) }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    WhispDialog(
        title = if (initial == null) "Add transform" else "Edit transform",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        confirmEnabled = name.isNotBlank() && instructions.isNotBlank(),
        onConfirm = {
            onSave(
                TransformProfile(
                    id = initial?.id ?: 0,
                    name = name.trim(),
                    description = description.trim(),
                    instructions = instructions.trim(),
                    enabled = enabled,
                    builtIn = initial?.builtIn ?: false,
                ),
            )
        },
    ) {
        FlowerWhispTextField(name, { name = it }, "Name", singleLine = true)
        FlowerWhispTextField(description, { description = it }, "Description", singleLine = true)
        FlowerWhispTextField(instructions, { instructions = it }, "Instructions", minLines = 5)
        SwitchRow("Enabled", checked = enabled, onCheckedChange = { enabled = it })
    }
}

@Composable
private fun ScratchpadScreen(
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    onOpenDrawer: (() -> Unit)?,
) {
    var draft by rememberSaveable(uiState.settings.scratchpad) { mutableStateOf(uiState.settings.scratchpad) }
    ScreenColumn("scratchpad-screen") {
        DestinationHeader("Scratchpad", onOpenDrawer)
        FlowerWhispTextField(
            value = draft,
            onValueChange = { draft = it },
            label = "Text",
            modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
            minLines = 12,
        )
        PrimaryAction("Save", Icons.Outlined.Check, enabled = draft != uiState.settings.scratchpad) {
            actions.onSaveScratchpad(draft)
        }
        uiState.transforms.filter { it.enabled }.forEach { transform ->
            ActionRow(Icons.Outlined.Refresh, transform.name, onClick = { actions.onRunScratchpadTransform(transform.id) })
            RowDivider()
        }
        if (uiState.transformBusy) Text("Working", style = MaterialTheme.typography.labelMedium, color = Clay)
    }
}

@Composable
private fun TransformPreviewDialog(preview: TransformPreview, actions: FlowerWhispActions) {
    WhispDialog(
        title = preview.name,
        onDismiss = actions.onDismissTransform,
        confirmLabel = "Copy",
        onConfirm = {
            actions.onCopy(preview.result)
            actions.onDismissTransform()
        },
    ) {
        TranscriptBlock(preview.result)
    }
}

@Composable
private fun SettingsScreen(
    uiState: FlowerWhispUiState,
    actions: FlowerWhispActions,
    onOpenDrawer: (() -> Unit)?,
) {
    // Keep the plaintext draft in this composition only; the repository stores
    // only the encrypted value after an explicit save.
    var apiKey by remember { mutableStateOf("") }
    var panel by rememberSaveable { mutableStateOf<SettingsPanel?>(null) }
    var confirmRemoveKey by rememberSaveable { mutableStateOf(false) }
    ScreenColumn("settings-screen") {
        DestinationHeader("Settings", onOpenDrawer)

        uiState.serviceMessage?.takeIf(String::isNotBlank)?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }

        SectionTitle("Provider")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Groq Cloud", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (uiState.groqApiKeyConfigured) "Saved" else "No key", style = MaterialTheme.typography.labelMedium, color = if (uiState.groqApiKeyConfigured) Clay else Warning)
        }
        FlowerWhispTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth().testTag("groq-api-key"),
            label = if (uiState.groqApiKeyConfigured) "Replace Groq key" else "Groq API key",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            supportingText = "Stored with Android Keystore",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactAction("Save key", Icons.Outlined.Lock, enabled = apiKey.trim().length >= 10) {
                actions.onSaveApiKey(apiKey.trim())
                apiKey = ""
            }
            if (uiState.groqApiKeyConfigured) {
                CompactAction("Remove", Icons.Outlined.DeleteOutline, accent = false, onClick = { confirmRemoveKey = true })
            }
        }
        ValueRow("Transcription model", uiState.settings.groqTranscriptionModel) { panel = SettingsPanel.TRANSCRIPTION_MODEL }
        RowDivider()
        ValueRow("Cleanup model", uiState.settings.groqRefinementModel) { panel = SettingsPanel.CLEANUP_MODEL }

        SectionTitle("Writing")
        ValueRow("Language", uiState.settings.language.displayName) { panel = SettingsPanel.LANGUAGE }
        RowDivider()
        ValueRow("Cleanup", uiState.settings.cleanupLevel.displayName) { panel = SettingsPanel.CLEANUP }
        RowDivider()
        ValueRow("Style", "By app") {
            actions.onNavigate(FlowerWhispDestination.STYLE)
        }

        SectionTitle("Dictation")
        SwitchRow("Automatic punctuation", checked = uiState.settings.autoPunctuation, onCheckedChange = actions.onAutoPunctuationChanged)
        RowDivider()
        SwitchRow("Remove filler words", checked = uiState.settings.removeFillers, onCheckedChange = actions.onRemoveFillersChanged)
        RowDivider()
        SwitchRow("Spoken corrections", checked = uiState.settings.spokenCorrections, onCheckedChange = actions.onSpokenCorrectionsChanged)

        SectionTitle("Privacy")
        ValueRow("Retention", uiState.settings.retentionMode.settingsLabel()) { panel = SettingsPanel.RETENTION }

        SectionTitle("Appearance")
        ValueRow("Theme", uiState.settings.appearanceMode.displayName) { panel = SettingsPanel.APPEARANCE }
        RowDivider()
        ValueRow("Bubble size", uiState.settings.bubbleSize.settingsLabel()) { panel = SettingsPanel.BUBBLE_SIZE }
        RowDivider()
        ValueRow("Bubble opacity", uiState.settings.bubbleOpacity.settingsLabel()) { panel = SettingsPanel.BUBBLE_OPACITY }
        RowDivider()
        ValueRow("Idle bubble", uiState.settings.idleBehavior.settingsLabel()) { panel = SettingsPanel.IDLE_BEHAVIOR }

        SectionTitle("Feedback")
        SwitchRow("Haptics", checked = uiState.settings.haptics, onCheckedChange = actions.onHapticsChanged)
        RowDivider()
        SwitchRow("Sound cues", checked = uiState.settings.playSounds, onCheckedChange = actions.onPlaySoundsChanged)
        RowDivider()
        SwitchRow("Pause other audio", checked = uiState.settings.muteMusicWhileDictating, onCheckedChange = actions.onMuteMusicChanged)
        RowDivider()
        SwitchRow("Reduce motion", checked = uiState.settings.reduceMotion, onCheckedChange = actions.onReduceMotionChanged)

        SectionTitle("Android access")
        ActionRow(Icons.Outlined.Layers, "Overlay", value = if (uiState.capabilities.overlayEnabled) "On" else "Fix", tint = if (uiState.capabilities.overlayEnabled) Clay else Warning, onClick = actions.onRequestOverlay)
        RowDivider()
        ActionRow(Icons.Outlined.AccessibilityNew, "Text insertion", value = if (uiState.capabilities.textInsertionReady) "On" else "Fix", tint = if (uiState.capabilities.textInsertionReady) Clay else Warning, onClick = actions.onRequestAccessibility)
        RowDivider()
        ActionRow(Icons.Outlined.Mic, "Microphone", value = if (uiState.capabilities.microphoneGranted) "On" else "Fix", tint = if (uiState.capabilities.microphoneGranted) Clay else Warning, onClick = actions.onRequestMicrophone)
        RowDivider()
        ActionRow(Icons.Outlined.Notifications, "Notifications", value = if (uiState.capabilities.notificationsGranted) "On" else "Fix", tint = if (uiState.capabilities.notificationsGranted) Clay else Warning, onClick = actions.onRequestNotifications)

        SectionTitle("Service")
        if (uiState.settings.snoozedUntilEpochMs > System.currentTimeMillis() || uiState.bubbleState is BubbleState.Snoozed) {
            PrimaryAction("Wake bubble", Icons.Outlined.CheckCircle, onClick = actions.onWake)
        } else SecondaryAction("Snooze bubble", Icons.Outlined.Snooze, actions.onSnooze)
        SecondaryAction("Restart service", Icons.Outlined.RestartAlt, actions.onRestartService)
    }

    when (panel) {
        SettingsPanel.LANGUAGE -> SettingsChoiceDialog(
            title = "Dictation language",
            values = LanguageMode.entries,
            selected = uiState.settings.language,
            label = LanguageMode::displayName,
            onSelect = actions.onLanguageChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.TRANSCRIPTION_MODEL -> SettingsChoiceDialog(
            title = "Transcription model",
            values = listOf("whisper-large-v3", "whisper-large-v3-turbo"),
            selected = uiState.settings.groqTranscriptionModel,
            label = { it },
            onSelect = actions.onTranscriptionModelChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.CLEANUP_MODEL -> SettingsChoiceDialog(
            title = "Cleanup model",
            values = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b"),
            selected = uiState.settings.groqRefinementModel,
            label = { it },
            onSelect = actions.onRefinementModelChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.RETENTION -> SettingsChoiceDialog(
            title = "Retention",
            values = RetentionMode.entries,
            selected = uiState.settings.retentionMode,
            label = RetentionMode::settingsLabel,
            onSelect = actions.onRetentionChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.APPEARANCE -> SettingsChoiceDialog(
            title = "Theme",
            values = AppearanceMode.entries,
            selected = uiState.settings.appearanceMode,
            label = AppearanceMode::displayName,
            onSelect = actions.onAppearanceChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.BUBBLE_SIZE -> SettingsChoiceDialog(
            title = "Bubble size",
            values = BubbleSize.entries,
            selected = uiState.settings.bubbleSize,
            label = BubbleSize::settingsLabel,
            onSelect = actions.onBubbleSizeChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.BUBBLE_OPACITY -> SettingsChoiceDialog(
            title = "Bubble opacity",
            values = BubbleOpacity.entries,
            selected = uiState.settings.bubbleOpacity,
            label = BubbleOpacity::settingsLabel,
            onSelect = actions.onBubbleOpacityChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.IDLE_BEHAVIOR -> SettingsChoiceDialog(
            title = "Idle bubble",
            values = IdleBehavior.entries,
            selected = uiState.settings.idleBehavior,
            label = IdleBehavior::settingsLabel,
            onSelect = actions.onIdleBehaviorChanged,
            onDismiss = { panel = null },
        )
        SettingsPanel.CLEANUP -> CleanupSettingsDialog(uiState.settings, actions) { panel = null }
        null -> Unit
    }
    if (confirmRemoveKey) {
        DeleteConfirmationDialog(
            title = "Remove the Groq key?",
            onDismiss = { confirmRemoveKey = false },
            onConfirm = {
                confirmRemoveKey = false
                actions.onClearApiKey()
            },
        )
    }
}

private enum class SettingsPanel {
    LANGUAGE,
    CLEANUP,
    TRANSCRIPTION_MODEL,
    CLEANUP_MODEL,
    RETENTION,
    APPEARANCE,
    BUBBLE_SIZE,
    BUBBLE_OPACITY,
    IDLE_BEHAVIOR,
}

@Composable
private fun <T> SettingsChoiceDialog(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    WhispDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = "Done",
        showCancelAction = false,
        onConfirm = onDismiss,
    ) {
        values.forEach { value ->
            SelectRow(label(value), selected = selected == value) { onSelect(value) }
        }
    }
}

@Composable
private fun CleanupSettingsDialog(
    settings: com.flowerwhisp.mobile.domain.model.AppSettings,
    actions: FlowerWhispActions,
    onDismiss: () -> Unit,
) {
    var level by rememberSaveable { mutableStateOf(settings.cleanupLevel) }
    var prompt by rememberSaveable { mutableStateOf(settings.cleanupPrompt(level)) }
    WhispDialog(
        title = "Cleanup",
        description = "Original transcript stays available",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = {
            actions.onCleanupLevelChanged(level)
            actions.onCleanupPromptChanged(level, prompt)
            onDismiss()
        },
    ) {
        CleanupLevel.entries.forEach { candidate ->
            SelectRow(candidate.displayName, selected = level == candidate) {
                level = candidate
                prompt = settings.cleanupPrompt(candidate)
            }
        }
        FlowerWhispTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = "Instructions",
            minLines = 6,
        )
    }
}

private fun RetentionMode.settingsLabel(): String = when (this) {
    RetentionMode.FOREVER -> "Keep forever"
    RetentionMode.HOURS_24 -> "Delete after 24 hours"
    RetentionMode.NEVER -> "Never store transcript text"
}

private fun BubbleSize.settingsLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)
private fun BubbleOpacity.settingsLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)
private fun IdleBehavior.settingsLabel(): String = when (this) {
    IdleBehavior.FULL -> "Always full"
    IdleBehavior.SHRINK -> "Shrink when idle"
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
private fun StatusPanel(icon: ImageVector, title: String, description: String? = null, tint: Color = SecondaryText) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        description?.let { Text(it, color = SecondaryText, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun formatDate(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs.coerceAtLeast(0) / 1_000)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
