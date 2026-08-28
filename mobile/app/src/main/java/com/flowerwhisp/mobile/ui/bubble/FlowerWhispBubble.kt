package com.flowerwhisp.mobile.ui.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import com.flowerwhisp.mobile.ui.theme.Error
import com.flowerwhisp.mobile.ui.theme.Mint
import com.flowerwhisp.mobile.ui.theme.OLEDBlack
import com.flowerwhisp.mobile.ui.theme.Outline
import com.flowerwhisp.mobile.ui.theme.PrimaryText
import com.flowerwhisp.mobile.ui.theme.SecondaryText
import com.flowerwhisp.mobile.ui.theme.SurfaceBlack
import com.flowerwhisp.mobile.ui.theme.Warning
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
) {
    if (state is BubbleState.Hidden) return

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(150))).using(
                SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(220) }),
            )
        },
        label = "bubble-state",
    ) { current ->
        Surface(
            color = SurfaceBlack,
            contentColor = PrimaryText,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
            shadowElevation = 6.dp,
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                .semantics { contentDescription = bubbleDescription(current, elapsedSeconds) },
        ) {
            when (current) {
                BubbleState.Hidden -> Unit
                BubbleState.Ready -> ReadyBubble(onStart)
                is BubbleState.Recording -> RecordingBubble(current.level, elapsedSeconds, onFinish, onCancel)
                is BubbleState.Processing -> ProcessingBubble(current.stage, onCancel)
                is BubbleState.Success -> SuccessBubble(current.inserted, onOpenApp)
                is BubbleState.InsertionFallback -> FallbackBubble(current.text, onCopy, onOpenApp)
                is BubbleState.AccessibilityError -> ErrorBubble(
                    title = "Insertion unavailable",
                    message = current.message,
                    onRetry = onRetry,
                    onOpenApp = onOpenApp,
                )
                is BubbleState.ServiceError -> ErrorBubble(
                    title = if (current.recoverableRecordingId != null) "Recording saved" else "Dictation stopped",
                    message = current.message,
                    onRetry = onRetry,
                    onOpenApp = onOpenApp,
                )
                BubbleState.Reconnecting -> ReconnectingBubble(onRetry, onCancel)
                is BubbleState.Snoozed -> SnoozedBubble(onOpenApp)
            }
        }
    }
}

@Composable
private fun ReadyBubble(onStart: () -> Unit) {
    IconButton(
        onClick = onStart,
        modifier = Modifier.size(width = 56.dp, height = 48.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Mint),
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = "Start dictation")
    }
}

@Composable
private fun RecordingBubble(level: Float, elapsedSeconds: Long, onFinish: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LevelBars(level)
        Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.labelLarge, color = PrimaryText)
        BubbleIconButton(Icons.Outlined.Stop, "Finish dictation", Mint, onFinish)
        BubbleIconButton(Icons.Outlined.Cancel, "Cancel dictation", SecondaryText, onCancel)
    }
}

@Composable
private fun LevelBars(level: Float) {
    val normalized = level.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .width(44.dp)
            .height(32.dp)
            .semantics { contentDescription = "Microphone level ${(normalized * 100).roundToInt()} percent" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(0.46f, 0.72f, 1f, 0.72f, 0.46f).forEachIndexed { index, scale ->
            val height = (6f + normalized * 22f * scale).dp
            Box(
                Modifier
                    .width(5.dp)
                    .height(height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index == 2 || normalized > 0.55f) Mint else Color.White),
            )
        }
    }
}

@Composable
private fun ProcessingBubble(stage: ProcessingStage, onCancel: () -> Unit) {
    val label = when (stage) {
        ProcessingStage.TRANSCRIBING -> "Transcribing"
        ProcessingStage.REFINING -> "Refining"
        ProcessingStage.INSERTING -> "Inserting"
    }
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = Mint)
        Text(label, style = MaterialTheme.typography.labelLarge)
        BubbleIconButton(Icons.Outlined.Cancel, "Cancel processing", SecondaryText, onCancel)
    }
}

@Composable
private fun SuccessBubble(inserted: Boolean, onOpenApp: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Mint)
        Text(if (inserted) "Inserted" else "Text ready", style = MaterialTheme.typography.labelLarge)
        if (!inserted) BubbleIconButton(Icons.AutoMirrored.Outlined.Launch, "Open FlowerWhisp", SecondaryText, onOpenApp)
    }
}

@Composable
private fun FallbackBubble(text: String, onCopy: (String) -> Unit, onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Couldn’t insert", fontWeight = FontWeight.SemiBold)
        Text("Copy the text, then paste it in the focused field.", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton("Copy", Icons.Outlined.ContentCopy, onClick = { onCopy(text) })
            CompactButton("Open app", Icons.AutoMirrored.Outlined.Launch, onClick = onOpenApp)
        }
    }
}

@Composable
private fun ErrorBubble(title: String, message: String, onRetry: () -> Unit, onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Error)
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        Text(message, color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton("Retry", Icons.Outlined.Refresh, onRetry)
            CompactButton("Open app", Icons.AutoMirrored.Outlined.Launch, onOpenApp)
        }
    }
}

@Composable
private fun ReconnectingBubble(onRetry: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Reconnecting", style = MaterialTheme.typography.labelLarge, color = Warning)
        BubbleIconButton(Icons.Outlined.Refresh, "Retry connection", Mint, onRetry)
        BubbleIconButton(Icons.Outlined.Cancel, "Cancel reconnecting", SecondaryText, onCancel)
    }
}

@Composable
private fun SnoozedBubble(onOpenApp: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Bubble snoozed", style = MaterialTheme.typography.labelLarge, color = SecondaryText)
        BubbleIconButton(Icons.AutoMirrored.Outlined.Launch, "Open FlowerWhisp to wake bubble", Mint, onOpenApp)
    }
}

@Composable
private fun BubbleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

@Composable
private fun CompactButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = OLEDBlack),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
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
