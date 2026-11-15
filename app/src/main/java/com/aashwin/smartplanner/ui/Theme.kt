package com.aashwin.smartplanner.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aashwin.smartplanner.data.Prefs

private val Light = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFE65100),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFFFB74D),
)

@Composable
fun PlannerTheme(themePref: Int, content: @Composable () -> Unit) {
    val dark = when (themePref) {
        Prefs.THEME_DARK -> true
        Prefs.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Priority is shown with an icon + text label, never only a color. */
object PriorityStyle {
    val high = Color(0xFFD32F2F)
    val medium = Color(0xFFF9A825)
    val low = Color(0xFF607D8B)
}
