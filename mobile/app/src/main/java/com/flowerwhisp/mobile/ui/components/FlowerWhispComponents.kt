package com.flowerwhisp.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowerwhisp.mobile.ui.theme.Clay
import com.flowerwhisp.mobile.ui.theme.ClayStrong
import com.flowerwhisp.mobile.ui.theme.Error
import com.flowerwhisp.mobile.ui.theme.OnClay
import com.flowerwhisp.mobile.ui.theme.Outline
import com.flowerwhisp.mobile.ui.theme.PrimaryText
import com.flowerwhisp.mobile.ui.theme.SecondaryText
import com.flowerwhisp.mobile.ui.theme.SurfaceElevated
import com.flowerwhisp.mobile.ui.theme.SurfaceInk
import com.flowerwhisp.mobile.ui.theme.SurfaceSelected
import kotlinx.coroutines.launch

private val PanelShape = RoundedCornerShape(14.dp)
private val ControlShape = RoundedCornerShape(8.dp)

/** A deliberately explicit surface primitive. It carries no Material elevation or default shape. */
@Composable
fun Modifier.whispSurface(
    color: Color = SurfaceInk,
    shape: Shape = PanelShape,
    borderColor: Color? = Outline,
    borderWidth: Dp = 1.dp,
): Modifier {
    val filled = this
        .clip(shape)
        .background(color)
    return if (borderColor == null) {
        filled
    } else {
        filled.border(BorderStroke(borderWidth, borderColor), shape)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(title: String, supporting: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = PrimaryText)
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    }
}

/**
 * Low-level input with an authored label treatment. The parent form remains scrollable and
 * this requester nudges the focused field into view when the IME appears.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FlowerWhispTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingContent: (@Composable (() -> Unit))? = null,
    supportingText: String? = null,
    errorText: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val shape = ControlShape
    val floatingLabel = focused || value.isNotEmpty()
    val borderColor = when {
        errorText != null -> Error
        focused -> Clay
        else -> Outline
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 56.dp else 104.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
            .whispSurface(color = SurfaceInk, shape = shape, borderColor = borderColor, borderWidth = if (focused) 1.5.dp else 1.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = PrimaryText),
        cursorBrush = SolidColor(Clay),
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (floatingLabel) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            errorText != null -> Error
                            focused -> Clay
                            else -> SecondaryText
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (leadingContent != null) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingContent() }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = if (floatingLabel) 0.dp else 4.dp),
                    ) {
                        if (!floatingLabel && value.isEmpty()) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
                        }
                        innerTextField()
                    }
                }
                val support = errorText ?: supportingText
                if (support != null) {
                    Text(
                        support,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (errorText != null) Error else SecondaryText,
                    )
                }
            }
        },
    )
}

@Composable
fun PrimaryAction(label: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = when {
        !enabled -> SurfaceSelected
        pressed -> ClayStrong
        else -> Clay
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .whispSurface(color = fill, shape = ControlShape, borderColor = if (enabled) Clay else Outline)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) OnClay else SecondaryText, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(label, color = if (enabled) OnClay else SecondaryText, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SecondaryAction(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    CompactAction(label = label, icon = icon, accent = false, onClick = onClick)
}

/** Compact authored action used in dialogs and the overlay bubble. */
@Composable
fun CompactAction(
    label: String,
    icon: ImageVector? = null,
    accent: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = when {
        danger && pressed -> Error.copy(alpha = 0.82f)
        danger -> Error
        !accent && pressed -> SurfaceSelected
        accent && pressed -> ClayStrong
        accent -> Clay
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> SecondaryText
        danger -> OnClay
        accent -> OnClay
        else -> PrimaryText
    }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .whispSurface(
                color = if (enabled) fill else SurfaceSelected,
                shape = ControlShape,
                borderColor = when {
                    danger && enabled -> Error
                    accent && enabled -> Clay
                    else -> Outline
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = content, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun FeatureSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .whispSurface(
                color = if (selected) SurfaceSelected else SurfaceInk,
                shape = PanelShape,
                borderColor = if (selected) Clay.copy(alpha = 0.68f) else Outline,
            ),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    value: String? = null,
    tint: Color = Clay,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(ControlShape)
            .background(if (pressed) SurfaceSelected else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .whispSurface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), borderColor = null),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.labelMedium, color = tint)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SecondaryText)
    }
}

/** Quiet disclosure row for dense settings screens. */
@Composable
fun ValueRow(
    title: String,
    value: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(ControlShape)
            .background(if (pressed) SurfaceSelected else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 156.dp),
        )
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(20.dp))
    }
}

/** A drawn toggle with an editorial on/off label instead of the Android switch silhouette. */
@Composable
fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(ControlShape)
            .background(if (pressed) SurfaceSelected else Color.Transparent)
            .semantics {
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        ToggleVisual(checked = checked)
    }
}

@Composable
private fun ToggleVisual(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 24.dp)
            .whispSurface(
                color = if (checked) Clay.copy(alpha = 0.2f) else SurfaceSelected,
                shape = RoundedCornerShape(6.dp),
                borderColor = if (checked) Clay else Outline,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(3.dp)
                .size(16.dp)
                .background(if (checked) Clay else SecondaryText, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
fun CompactToggle(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 48.dp)
            .clip(ControlShape)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        ToggleVisual(checked)
    }
}

@Composable
fun SelectRow(title: String, description: String? = null, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .whispSurface(
                color = when {
                    pressed -> SurfaceSelected
                    selected -> SurfaceSelected.copy(alpha = 0.78f)
                    else -> Color.Transparent
                },
                shape = ControlShape,
                borderColor = if (selected) Clay.copy(alpha = 0.7f) else null,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionMark(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(BorderStroke(1.dp, if (selected) Clay else SecondaryText), CircleShape)
            .padding(4.dp)
            .background(if (selected) Clay else Color.Transparent, CircleShape),
    )
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Outline.copy(alpha = 0.72f)))
}

@Composable
fun MinimumIconButton(icon: ImageVector, label: String, tint: Color = PrimaryText, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (pressed) SurfaceSelected else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/**
 * Product-owned modal shell. Android still supplies the modal window and back handling, but
 * the scrim, panel, close affordance, fields, and actions are all FlowerWhisp-owned.
 */
@Composable
fun WhispDialog(
    title: String,
    description: String? = null,
    onDismiss: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean = true,
    confirmDanger: Boolean = false,
    showCancelAction: Boolean = true,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val scrimSource = remember { MutableInteractionSource() }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.66f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(12.dp)
                .clickable(interactionSource = scrimSource, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .heightIn(max = maxHeight)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .whispSurface(color = SurfaceElevated, shape = PanelShape, borderColor = Outline),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    }
                    MinimumIconButton(Icons.Outlined.Close, "Close dialog", SecondaryText, onDismiss)
                }
                RowDivider()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
                RowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showCancelAction) {
                        CompactAction("Cancel", accent = false, onClick = onDismiss)
                        Spacer(Modifier.width(8.dp))
                    }
                    CompactAction(confirmLabel, accent = true, danger = confirmDanger, enabled = confirmEnabled, onClick = onConfirm)
                }
            }
        }
    }
}
