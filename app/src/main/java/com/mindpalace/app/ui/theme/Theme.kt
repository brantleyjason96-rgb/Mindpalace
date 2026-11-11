package com.mindpalace.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// Brand: warm paper + ink, with a single terracotta accent — calm, tactile, private.
// A notebook you keep, not a dashboard you monitor. Static colors on purpose (no dynamic
// color) so the app looks the same on every phone, and the mood is never accidentally undone
// by someone's wallpaper-derived Material You palette.

private val LightColors = lightColorScheme(
    primary = Color(0xFFAE5A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1DAC8),
    onPrimaryContainer = Color(0xFF47220B),
    secondary = Color(0xFF6B7154),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E6CC),
    onSecondaryContainer = Color(0xFF272B16),
    background = Color(0xFFF7F2E7),
    onBackground = Color(0xFF2B2418),
    surface = Color(0xFFF7F2E7),
    onSurface = Color(0xFF2B2418),
    surfaceVariant = Color(0xFFEAE1D1),
    onSurfaceVariant = Color(0xFF4D4535),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF2EBDD),
    surfaceContainer = Color(0xFFECE4D3),
    surfaceContainerHigh = Color(0xFFE6DDC9),
    surfaceContainerHighest = Color(0xFFE0D6C0),
    outline = Color(0xFF8A7F67),
    outlineVariant = Color(0xFFD1C5AC),
    error = Color(0xFFA33B2B),
    errorContainer = Color(0xFFF4D8CF),
    onErrorContainer = Color(0xFF3D0F05),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE08A56),
    onPrimary = Color(0xFF3F1F0B),
    primaryContainer = Color(0xFF5A2E16),
    onPrimaryContainer = Color(0xFFF1DAC8),
    secondary = Color(0xFFADB389),
    onSecondary = Color(0xFF32371E),
    secondaryContainer = Color(0xFF474D32),
    onSecondaryContainer = Color(0xFFE2E6CC),
    background = Color(0xFF1C1712),
    onBackground = Color(0xFFEBE2D3),
    surface = Color(0xFF1C1712),
    onSurface = Color(0xFFEBE2D3),
    surfaceVariant = Color(0xFF473F2F),
    onSurfaceVariant = Color(0xFFD1C5AC),
    surfaceContainerLowest = Color(0xFF14110D),
    surfaceContainerLow = Color(0xFF221C15),
    surfaceContainer = Color(0xFF272019),
    surfaceContainerHigh = Color(0xFF322B22),
    surfaceContainerHighest = Color(0xFF3D352A),
    outline = Color(0xFF9C9178),
    outlineVariant = Color(0xFF473F2F),
    error = Color(0xFFEAA695),
    errorContainer = Color(0xFF5C1E13),
    onErrorContainer = Color(0xFFF4D8CF),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Serif accent on titles only (headings, note titles) — the notebook cue. Body stays sans
// for readability. FontFamily.Serif is the platform's built-in generic serif, so this is
// free: no font files to bundle, no dependency to add.
private val base = Typography()
private val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
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

/** User-facing override of the system light/dark setting; SYSTEM follows the phone as before. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** One SharedPreferences int — not worth a DataStore dependency for a single setting. */
object ThemePrefs {
    private const val FILE = "mp_prefs"
    private const val KEY = "theme_mode"

    fun load(context: Context): ThemeMode =
        ThemeMode.entries.getOrElse(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY, ThemeMode.LIGHT.ordinal),
        ) { ThemeMode.LIGHT }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY, mode.ordinal).apply()
    }
}
