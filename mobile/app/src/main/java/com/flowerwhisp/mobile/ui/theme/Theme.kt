package com.flowerwhisp.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val OLEDBlack = Color(0xFF050505)
val SurfaceBlack = Color(0xFF101210)
val SurfaceSelected = Color(0xFF172019)
val Outline = Color(0xFF283029)
val PrimaryText = Color(0xFFF4F7F4)
val SecondaryText = Color(0xFFB8C2BA)
val Mint = Color(0xFFB8F5D0)
val MintStrong = Color(0xFF9EE5B2)
val Error = Color(0xFFFFB4AB)
val Warning = Color(0xFFF2C97D)

private val FlowerWhispColors = darkColorScheme(
    primary = Mint,
    onPrimary = OLEDBlack,
    primaryContainer = SurfaceSelected,
    onPrimaryContainer = PrimaryText,
    secondary = SecondaryText,
    onSecondary = OLEDBlack,
    secondaryContainer = SurfaceSelected,
    onSecondaryContainer = PrimaryText,
    background = OLEDBlack,
    onBackground = PrimaryText,
    surface = SurfaceBlack,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceSelected,
    onSurfaceVariant = SecondaryText,
    outline = Outline,
    error = Error,
    onError = OLEDBlack,
)

private val FlowerWhispTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun FlowerWhispTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlowerWhispColors,
        typography = FlowerWhispTypography,
        content = content,
    )
}
