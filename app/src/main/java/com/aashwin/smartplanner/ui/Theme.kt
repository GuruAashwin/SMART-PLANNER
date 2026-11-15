package com.aashwin.smartplanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.aashwin.smartplanner.data.Prefs

/** Black + gold (dark) and white + gold (light). Fixed palette, no wallpaper colours. */
object Gold {
    val bright = Color(0xFFD4AF37)   // gold on black
    val deep = Color(0xFFA07C1C)     // gold on white (readable contrast)
    val paleLight = Color(0xFFF4EBCF)
    val paleDark = Color(0xFF2A220A)
}

private val DarkGold = darkColorScheme(
    primary = Gold.bright,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A2F0B),
    onPrimaryContainer = Color(0xFFF3DE9A),
    secondary = Gold.bright,
    onSecondary = Color.Black,
    secondaryContainer = Gold.paleDark,
    onSecondaryContainer = Color(0xFFF3DE9A),
    tertiary = Gold.bright,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    surfaceBright = Color(0xFF242424),
    surfaceDim = Color.Black,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF242424),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
    errorContainer = Color(0xFF3B1212),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF2F2F2),
    inverseOnSurface = Color.Black,
    inversePrimary = Gold.deep,
)

private val LightGold = lightColorScheme(
    primary = Gold.deep,
    onPrimary = Color.Black,
    primaryContainer = Gold.paleLight,
    onPrimaryContainer = Color(0xFF3A2C00),
    secondary = Gold.deep,
    onSecondary = Color.Black,
    secondaryContainer = Gold.paleLight,
    onSecondaryContainer = Color(0xFF3A2C00),
    tertiary = Gold.deep,
    onTertiary = Color.Black,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2EFE7),
    onSurfaceVariant = Color(0xFF66625A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Color(0xFFF7F5EF),
    surfaceContainerHigh = Color(0xFFF1EEE6),
    surfaceContainerHighest = Color(0xFFEBE7DC),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE8E4DA),
    outline = Color(0xFFD3CCBA),
    outlineVariant = Color(0xFFEAE5D8),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF111111),
    inverseOnSurface = Color.White,
    inversePrimary = Gold.bright,
)

private val base = Typography()
private val AppType = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun isDark(themePref: Int): Boolean = when (themePref) {
    Prefs.THEME_DARK -> true
    Prefs.THEME_LIGHT -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun PlannerTheme(themePref: Int, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark(themePref)) DarkGold else LightGold,
        typography = AppType,
        content = content,
    )
}
