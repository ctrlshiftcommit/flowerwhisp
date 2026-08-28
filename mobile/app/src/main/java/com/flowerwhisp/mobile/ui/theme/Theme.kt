package com.flowerwhisp.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * FlowerWhisp's visual language is deliberately warm and ink-led. These tokens
 * are shared by the app surfaces and the floating overlay so the two entry
 * points feel like one instrument.
 */
val Ink = Color(0xFF0C0B0A)
val SurfaceInk = Color(0xFF161412)
val SurfaceElevated = Color(0xFF201D19)
val SurfaceSelected = Color(0xFF29231E)
val Outline = Color(0xFF3A342D)
val PrimaryText = Color(0xFFF5F0E7)
val SecondaryText = Color(0xFFBDB4A8)
val MutedText = Color(0xFF8C847A)
val Clay = Color(0xFFD17A5A)
val ClayStrong = Color(0xFFB85D43)
val OnClay = Color(0xFF1C110D)
val Error = Color(0xFFFFB3A7)
val Warning = Color(0xFFE4BC83)
val Resolved = Color(0xFFE4BC83)

private val FlowerWhispColors = darkColorScheme(
    primary = Clay,
    onPrimary = OnClay,
    primaryContainer = SurfaceSelected,
    onPrimaryContainer = PrimaryText,
    secondary = SecondaryText,
    onSecondary = Ink,
    secondaryContainer = SurfaceSelected,
    onSecondaryContainer = PrimaryText,
    background = Ink,
    onBackground = PrimaryText,
    surface = SurfaceInk,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceSelected,
    onSurfaceVariant = SecondaryText,
    outline = Outline,
    error = Error,
    onError = Ink,
)

private val FlowerWhispTypography = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FlowerWhispTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlowerWhispColors,
        typography = FlowerWhispTypography,
        content = content,
    )
}
