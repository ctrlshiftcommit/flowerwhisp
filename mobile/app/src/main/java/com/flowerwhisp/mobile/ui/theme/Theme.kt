package com.flowerwhisp.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.flowerwhisp.mobile.R
import com.flowerwhisp.mobile.domain.model.AppearanceMode

/**
 * Neutral monochrome tokens shared by Compose and the floating bubble.
 * The canvas is deliberately pure black or pure white; no warm tint or
 * dynamic-color override is allowed to change the product's appearance.
 */
private data class FlowerWhispPalette(
    val ink: Color,
    val surfaceInk: Color,
    val surfaceElevated: Color,
    val surfaceSelected: Color,
    val outline: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val error: Color,
    val warning: Color,
    val resolved: Color,
)

private val DarkPalette = FlowerWhispPalette(
    ink = Color(0xFF000000),
    surfaceInk = Color(0xFF080808),
    surfaceElevated = Color(0xFF101010),
    surfaceSelected = Color(0xFF1A1A1A),
    outline = Color(0xFF2C2C2C),
    primaryText = Color(0xFFFFFFFF),
    secondaryText = Color(0xFFA3A3A3),
    mutedText = Color(0xFF737373),
    accent = Color(0xFFFFFFFF),
    accentPressed = Color(0xFFE5E5E5),
    onAccent = Color(0xFF000000),
    error = Color(0xFFFF7A70),
    warning = Color(0xFFFFB454),
    resolved = Color(0xFFFFFFFF),
)

private val LightPalette = FlowerWhispPalette(
    ink = Color(0xFFFFFFFF),
    surfaceInk = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF7F7F7),
    surfaceSelected = Color(0xFFEDEDED),
    outline = Color(0xFFD4D4D4),
    primaryText = Color(0xFF111111),
    secondaryText = Color(0xFF666666),
    mutedText = Color(0xFF8A8A8A),
    accent = Color(0xFF111111),
    accentPressed = Color(0xFF000000),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFB42318),
    warning = Color(0xFF8A4B08),
    resolved = Color(0xFF111111),
)

private val LocalFlowerWhispPalette = compositionLocalOf { DarkPalette }
private val LocalFlowerWhispDark = compositionLocalOf { true }

val Ink: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.ink
val SurfaceInk: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.surfaceInk
val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.surfaceElevated
val SurfaceSelected: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.surfaceSelected
val Outline: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.outline
val PrimaryText: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.primaryText
val SecondaryText: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.secondaryText
val MutedText: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.mutedText

// Source-compatible names for the existing component layer. These are now
// strict monochrome action tokens, not terracotta colors.
val Clay: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.accent
val ClayStrong: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.accentPressed
val OnClay: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.onAccent
val Error: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.error
val Warning: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.warning
val Resolved: Color @Composable @ReadOnlyComposable get() = LocalFlowerWhispPalette.current.resolved
val IsDarkTheme: Boolean @Composable @ReadOnlyComposable get() = LocalFlowerWhispDark.current

private fun flowerWhispColorScheme(palette: FlowerWhispPalette, dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.surfaceSelected,
            onPrimaryContainer = palette.primaryText,
            secondary = palette.secondaryText,
            onSecondary = palette.ink,
            background = palette.ink,
            onBackground = palette.primaryText,
            surface = palette.surfaceInk,
            onSurface = palette.primaryText,
            surfaceVariant = palette.surfaceSelected,
            onSurfaceVariant = palette.secondaryText,
            outline = palette.outline,
            error = palette.error,
            onError = palette.ink,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.surfaceSelected,
            onPrimaryContainer = palette.primaryText,
            secondary = palette.secondaryText,
            onSecondary = palette.ink,
            background = palette.ink,
            onBackground = palette.primaryText,
            surface = palette.surfaceInk,
            onSurface = palette.primaryText,
            surfaceVariant = palette.surfaceSelected,
            onSurfaceVariant = palette.secondaryText,
            outline = palette.outline,
            error = palette.error,
            onError = palette.surfaceInk,
        )
    }

/** Exact UI family used by the desktop renderer's --font-ui token. */
val DmSansFontFamily = FontFamily(
    Font(R.font.dm_sans_variable, FontWeight.Normal),
    Font(R.font.dm_sans_variable, FontWeight.Medium),
    Font(R.font.dm_sans_variable, FontWeight.SemiBold),
    Font(R.font.dm_sans_variable, FontWeight.Bold),
)

private fun dmSansStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = DmSansFontFamily,
    fontWeight = weight,
    fontSynthesis = FontSynthesis.None,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private val FlowerWhispTypography = Typography(
    displayLarge = dmSansStyle(44, 48, FontWeight.SemiBold, -1.45f),
    displayMedium = dmSansStyle(40, 44, FontWeight.SemiBold, -1.25f),
    displaySmall = dmSansStyle(36, 40, FontWeight.SemiBold, -1.05f),
    headlineLarge = dmSansStyle(30, 35, FontWeight.SemiBold, -0.85f),
    headlineMedium = dmSansStyle(24, 29, FontWeight.SemiBold, -0.55f),
    headlineSmall = dmSansStyle(21, 26, FontWeight.SemiBold, -0.35f),
    titleLarge = dmSansStyle(20, 25, FontWeight.SemiBold, -0.25f),
    titleMedium = dmSansStyle(16, 22, FontWeight.Medium, -0.08f),
    titleSmall = dmSansStyle(14, 20, FontWeight.Medium),
    bodyLarge = dmSansStyle(16, 24),
    bodyMedium = dmSansStyle(14, 20),
    bodySmall = dmSansStyle(12, 18),
    labelLarge = dmSansStyle(14, 20, FontWeight.Medium),
    labelMedium = dmSansStyle(12, 16, FontWeight.Medium, 0.05f),
    labelSmall = dmSansStyle(11, 14, FontWeight.Medium, 0.08f),
)

@Composable
fun FlowerWhispTheme(
    appearanceMode: AppearanceMode = AppearanceMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (appearanceMode) {
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    ApplySystemBarAppearance(dark)
    CompositionLocalProvider(
        LocalFlowerWhispPalette provides palette,
        LocalFlowerWhispDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = flowerWhispColorScheme(palette, dark),
            typography = FlowerWhispTypography,
            content = content,
        )
    }
}

@Composable
private fun ApplySystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}
