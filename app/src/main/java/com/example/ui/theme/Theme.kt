package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class LiquidGlassTokens(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val orbPurple: Color,
    val orbNavy: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val navBarSurface: Color
)

private val LightGlassTokens = LiquidGlassTokens(
    isDark = false,
    backgroundTop = PureWhite,
    backgroundMid = SoftIndigoTint,
    backgroundBottom = SoftLavenderTint,
    orbPurple = RoyalPurple.copy(alpha = 0.16f),
    orbNavy = MidnightNavy.copy(alpha = 0.08f),
    glassSurface = PureWhite.copy(alpha = 0.74f),
    glassSurfaceElevated = PureWhite.copy(alpha = 0.90f),
    glassBorderTop = PureWhite.copy(alpha = 0.95f),
    glassBorderBottom = SubtleBorderLight.copy(alpha = 0.85f),
    navBarSurface = PureWhite.copy(alpha = 0.86f)
)

private val DarkGlassTokens = LiquidGlassTokens(
    isDark = true,
    backgroundTop = DeepNavy,
    backgroundMid = MidnightNavy,
    backgroundBottom = Color(0xFF13112B),
    orbPurple = RoyalPurple.copy(alpha = 0.28f),
    orbNavy = SoftIndigo.copy(alpha = 0.22f),
    glassSurface = SlateNavy.copy(alpha = 0.62f),
    glassSurfaceElevated = SlateNavy.copy(alpha = 0.82f),
    glassBorderTop = PureWhite.copy(alpha = 0.18f),
    glassBorderBottom = LuminousLavender.copy(alpha = 0.08f),
    navBarSurface = MidnightNavy.copy(alpha = 0.88f)
)

val LocalGlassTokens = staticCompositionLocalOf { LightGlassTokens }

private val LightColorScheme = lightColorScheme(
    primary = RoyalPurple,
    onPrimary = PureWhite,
    primaryContainer = SoftLavenderTint,
    onPrimaryContainer = DeepPurple,
    secondary = MidnightNavy,
    onSecondary = PureWhite,
    secondaryContainer = SoftIndigoTint,
    onSecondaryContainer = MidnightNavy,
    tertiary = SoftIndigo,
    onTertiary = PureWhite,
    background = PorcelainMist,
    onBackground = MidnightNavy,
    surface = PureWhite,
    onSurface = MidnightNavy,
    surfaceVariant = SoftIndigoTint,
    onSurfaceVariant = MutedNavy,
    outline = SubtleBorderLight,
    error = RoseDanger,
    onError = PureWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = PureWhite,
    primaryContainer = DeepPurple,
    onPrimaryContainer = LuminousLavender,
    secondary = LuminousLavender,
    onSecondary = DeepNavy,
    secondaryContainer = SlateNavy,
    onSecondaryContainer = PureWhite,
    tertiary = SoftIndigo,
    onTertiary = PureWhite,
    background = DeepNavy,
    onBackground = PorcelainMist,
    surface = MidnightNavy,
    onSurface = PorcelainMist,
    surfaceVariant = SlateNavy,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    error = RoseDanger,
    onError = PureWhite
)

val PdfMakerShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PdfMakerTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val glassTokens = if (darkTheme) DarkGlassTokens else LightGlassTokens

    CompositionLocalProvider(LocalGlassTokens provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PdfMakerTypography,
            shapes = PdfMakerShapes,
            content = content
        )
    }
}
