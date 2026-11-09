package com.mindpalace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Brand: deep teal (matches the launcher icon, #234E52) on warm paper, with a muted amber accent.
// Static colors on purpose (no dynamic color) so the app looks the same on every phone.

private val LightColors = lightColorScheme(
    primary = Color(0xFF234E52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EBE8),
    onPrimaryContainer = Color(0xFF0A2A2D),
    secondary = Color(0xFF8A6A3B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E6CC),
    onSecondaryContainer = Color(0xFF2E200A),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1B1C1B),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1B1C1B),
    surfaceVariant = Color(0xFFE6E9E7),
    onSurfaceVariant = Color(0xFF424847),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F4F0),
    surfaceContainer = Color(0xFFF1EFEA),
    surfaceContainerHigh = Color(0xFFEBE9E4),
    surfaceContainerHighest = Color(0xFFE5E3DE),
    outline = Color(0xFF727877),
    outlineVariant = Color(0xFFC2C8C6),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD1CB),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF1F4A4E),
    onPrimaryContainer = Color(0xFFD3EBE8),
    secondary = Color(0xFFE0C28E),
    onSecondary = Color(0xFF3F2E0F),
    secondaryContainer = Color(0xFF584423),
    onSecondaryContainer = Color(0xFFF4E6CC),
    background = Color(0xFF121514),
    onBackground = Color(0xFFE3E3E1),
    surface = Color(0xFF121514),
    onSurface = Color(0xFFE3E3E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBFC8C5),
    surfaceContainerLowest = Color(0xFF0D100F),
    surfaceContainerLow = Color(0xFF181B1A),
    surfaceContainer = Color(0xFF1C1F1E),
    surfaceContainerHigh = Color(0xFF262928),
    surfaceContainerHighest = Color(0xFF313433),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1814),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun MindPalaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
