package com.flowerwhisp.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowerwhisp.mobile.ui.theme.Clay
import com.flowerwhisp.mobile.ui.theme.Ink
import com.flowerwhisp.mobile.ui.theme.Outline
import com.flowerwhisp.mobile.ui.theme.PrimaryText
import com.flowerwhisp.mobile.ui.theme.SecondaryText
import com.flowerwhisp.mobile.ui.theme.SurfaceInk
import com.flowerwhisp.mobile.ui.theme.SurfaceSelected

@Composable
fun ScreenHeader(title: String, description: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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

@Composable
fun PrimaryAction(label: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        color = if (enabled) Clay else SurfaceSelected,
        contentColor = if (enabled) Ink else SecondaryText,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(label, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SecondaryAction(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        contentColor = PrimaryText,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        border = BorderStroke(1.dp, Outline),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label)
        }
    }
}

@Composable
fun FeatureSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (selected) SurfaceSelected else SurfaceInk,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) Clay.copy(alpha = 0.58f) else Outline),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = tint.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(42.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.labelMedium, color = tint)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SecondaryText)
    }
}

@Composable
fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Clay,
                uncheckedThumbColor = SecondaryText,
                uncheckedTrackColor = SurfaceSelected,
                uncheckedBorderColor = Outline,
            ),
        )
    }
}

@Composable
fun SelectRow(title: String, description: String? = null, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) SurfaceSelected else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(1.dp, Clay.copy(alpha = 0.65f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) Clay else Color.Transparent,
                border = BorderStroke(1.dp, if (selected) Clay else SecondaryText),
                modifier = Modifier.size(18.dp),
            ) {}
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            }
        }
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = Outline.copy(alpha = 0.72f), thickness = 1.dp)
}

@Composable
fun MinimumIconButton(icon: ImageVector, label: String, tint: Color = PrimaryText, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}
