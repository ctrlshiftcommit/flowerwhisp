package com.flowerwhisp.mobile.ui.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import com.flowerwhisp.mobile.ui.theme.Clay
import com.flowerwhisp.mobile.ui.theme.Error
import com.flowerwhisp.mobile.ui.theme.Outline
import com.flowerwhisp.mobile.ui.theme.PrimaryText
import com.flowerwhisp.mobile.ui.theme.Resolved
import com.flowerwhisp.mobile.ui.theme.SecondaryText
import com.flowerwhisp.mobile.ui.theme.SurfaceElevated
import com.flowerwhisp.mobile.ui.theme.Warning
import com.flowerwhisp.mobile.ui.components.CompactAction
import com.flowerwhisp.mobile.ui.components.MinimumIconButton
import com.flowerwhisp.mobile.ui.components.whispSurface
import kotlin.math.roundToInt

@Composable
fun FlowerWhispBubble(
    state: BubbleState,
    elapsedSeconds: Long,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenApp: () -> Unit,
    reduceMotion: Boolean = false,
    hapticsEnabled: Boolean = true,
) {
    if (state is BubbleState.Hidden) return
    val hapticFeedback = LocalHapticFeedback.current
    val withFeedback: (() -> Unit) -> () -> Unit = { action ->
        {
            if (hapticsEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
        }
    }
    val copyWithFeedback: (String) -> Unit = { value ->
        if (hapticsEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        onCopy(value)
    }

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(if (reduceMotion) 0 else 180)) togetherWith fadeOut(tween(if (reduceMotion) 0 else 130))).using(
                SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(if (reduceMotion) 0 else 240) }),
            )
        },
        label = "bubble-state",
    ) { current ->
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = 56.dp)
                .whispSurface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(22.dp),
                    borderColor = if (current is BubbleState.Recording) Clay.copy(alpha = 0.72f) else Outline,
                )
                .semantics { contentDescription = bubbleDescription(current, elapsedSeconds) },
        ) {
            when (current) {
                BubbleState.Hidden -> Unit
                BubbleState.Ready -> ReadyBubble(withFeedback(onStart), reduceMotion)
                is BubbleState.Recording -> RecordingBubble(current.level, elapsedSeconds, withFeedback(onFinish), withFeedback(onCancel))
                is BubbleState.Processing -> ProcessingBubble(current.stage, withFeedback(onCancel), reduceMotion)
                is BubbleState.Success -> SuccessBubble(current.inserted, withFeedback(onOpenApp))
                is BubbleState.InsertionFallback -> FallbackBubble(current.text, copyWithFeedback, withFeedback(onOpenApp))
                is BubbleState.AccessibilityError -> ErrorBubble(
                    title = "Insertion unavailable",
                    message = current.message,
                    onRetry = withFeedback(onRetry),
                    onOpenApp = withFeedback(onOpenApp),
                )
                is BubbleState.ServiceError -> ErrorBubble(
                    title = if (current.recoverableRecordingId != null) "Recording saved" else "Dictation stopped",
                    message = current.message,
                    onRetry = withFeedback(onRetry),
                    onOpenApp = withFeedback(onOpenApp),
                )
                BubbleState.Reconnecting -> ReconnectingBubble(withFeedback(onRetry), withFeedback(onCancel))
                is BubbleState.Snoozed -> SnoozedBubble(withFeedback(onOpenApp))
            }
        }
    }
}

@Composable
private fun ReadyBubble(onStart: () -> Unit, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "ready-breath")
    val breath by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
        label = "ready-breath-scale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (pressed) Clay.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onStart,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WhispGlyph(
            modifier = Modifier.size(30.dp),
            accent = Clay,
            scale = if (reduceMotion) 1f else breath,
        )
    }
}

@Composable
private fun RecordingBubble(level: Float, elapsedSeconds: Long, onFinish: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LevelBars(level)
        Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.labelLarge, color = PrimaryText)
        BubbleIconButton(Icons.Outlined.Stop, "Finish dictation", Clay, onFinish)
        BubbleIconButton(Icons.Outlined.Cancel, "Cancel dictation", SecondaryText, onCancel)
    }
}

@Composable
private fun LevelBars(level: Float) {
    val normalized = level.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .width(48.dp)
            .height(34.dp)
            .semantics { contentDescription = "Microphone level ${(normalized * 100).roundToInt()} percent" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(0.42f, 0.68f, 1f, 0.68f, 0.42f).forEachIndexed { index, scale ->
            val target = (6f + normalized * 24f * scale).dp
            val height by animateFloatAsState(target.value, tween(120), label = "level-$index")
            Box(
                Modifier
                    .width(5.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index == 2) Clay else PrimaryText.copy(alpha = 0.78f)),
            )
        }
    }
}

@Composable
private fun ProcessingBubble(stage: ProcessingStage, onCancel: () -> Unit, reduceMotion: Boolean) {
    val label = when (stage) {
        ProcessingStage.TRANSCRIBING -> "Transcribing"
        ProcessingStage.REFINING -> "Refining"
        ProcessingStage.INSERTING -> "Inserting"
    }
    Row(
        modifier = Modifier.padding(start = 15.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProcessingGlyph(stage, reduceMotion)
        Text(label, style = MaterialTheme.typography.labelLarge)
        BubbleIconButton(Icons.Outlined.Cancel, "Cancel processing", SecondaryText, onCancel)
    }
}

@Composable
private fun ProcessingGlyph(stage: ProcessingStage, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "processing-dots")
    val clay = Clay
    val primaryText = PrimaryText
    val pulse by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "processing-pulse",
    )
    Canvas(Modifier.size(24.dp)) {
        val active = stage.ordinal
        listOf(0.25f, 0.5f, 0.75f).forEachIndexed { index, fraction ->
            drawCircle(
                color = if (index == active) clay else primaryText.copy(alpha = 0.36f),
                radius = 3.1.dp.toPx() * if (index == active && !reduceMotion) pulse else 1f,
                center = androidx.compose.ui.geometry.Offset(size.width * fraction, size.height / 2f),
            )
        }
    }
}

@Composable
private fun SuccessBubble(inserted: Boolean, onOpenApp: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 15.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        WhispGlyph(Modifier.size(25.dp), accent = Resolved, scale = 1f, resolved = true)
        Text(if (inserted) "Inserted" else "Text ready", style = MaterialTheme.typography.labelLarge)
        if (!inserted) BubbleIconButton(Icons.AutoMirrored.Outlined.Launch, "Open FlowerWhisp", SecondaryText, onOpenApp)
    }
}

@Composable
private fun FallbackBubble(text: String, onCopy: (String) -> Unit, onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier.width(300.dp).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("Could not insert", fontWeight = FontWeight.Medium)
        Text("Copy the text, then paste it in the focused field.", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactAction("Copy", Icons.Outlined.ContentCopy, onClick = { onCopy(text) })
            CompactAction("Open app", Icons.AutoMirrored.Outlined.Launch, onClick = onOpenApp)
        }
    }
}

@Composable
private fun ErrorBubble(title: String, message: String, onRetry: () -> Unit, onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier.width(300.dp).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Error)
            Text(title, fontWeight = FontWeight.Medium)
        }
        Text(message, color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactAction("Retry", Icons.Outlined.Refresh, onClick = onRetry)
            CompactAction("Open app", Icons.AutoMirrored.Outlined.Launch, onClick = onOpenApp)
        }
    }
}

@Composable
private fun ReconnectingBubble(onRetry: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 15.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Reconnecting", style = MaterialTheme.typography.labelLarge, color = Warning)
        BubbleIconButton(Icons.Outlined.Refresh, "Retry connection", Clay, onRetry)
        BubbleIconButton(Icons.Outlined.Cancel, "Cancel reconnecting", SecondaryText, onCancel)
    }
}

@Composable
private fun SnoozedBubble(onOpenApp: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 15.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Bubble snoozed", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
        BubbleIconButton(Icons.AutoMirrored.Outlined.Launch, "Open FlowerWhisp to wake bubble", Clay, onOpenApp)
    }
}

@Composable
private fun BubbleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    MinimumIconButton(icon, label, tint, onClick)
}

@Composable
private fun WhispGlyph(
    modifier: Modifier,
    accent: Color,
    scale: Float,
    resolved: Boolean = false,
) {
    val primaryText = PrimaryText
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val radius = size.minDimension * 0.28f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = accent.copy(alpha = 0.18f),
            radius = radius * scale * 1.75f,
            center = center,
        )
        if (resolved) {
            drawLine(
                color = accent,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.52f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.72f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = accent,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.72f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.28f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        } else {
            drawArc(
                color = accent,
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.17f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.56f),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            drawLine(
                color = primaryText,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.82f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = primaryText,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.82f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.82f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun bubbleDescription(state: BubbleState, elapsedSeconds: Long): String = when (state) {
    BubbleState.Hidden -> "FlowerWhisp hidden"
    BubbleState.Ready -> "FlowerWhisp ready. Start dictation"
    is BubbleState.Recording -> "Recording, ${formatElapsed(elapsedSeconds)}, microphone level ${(state.level.coerceIn(0f, 1f) * 100).roundToInt()} percent"
    is BubbleState.Processing -> "Processing: ${state.stage.name.lowercase()}"
    is BubbleState.Success -> if (state.inserted) "Dictation inserted" else "Dictation text ready"
    is BubbleState.InsertionFallback -> "Insertion failed. Copy and paste recovery available"
    is BubbleState.AccessibilityError -> "Insertion unavailable. ${state.message}"
    is BubbleState.ServiceError -> "Dictation error. ${state.message}"
    BubbleState.Reconnecting -> "FlowerWhisp reconnecting"
    is BubbleState.Snoozed -> "FlowerWhisp bubble snoozed"
}

private fun formatElapsed(seconds: Long): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
