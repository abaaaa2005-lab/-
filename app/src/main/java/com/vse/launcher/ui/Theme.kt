package com.vse.launcher.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.vse.launcher.data.Settings
import com.vse.launcher.data.ThemeMode

@Composable
fun isDark(settings: Settings): Boolean = when (settings.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

@Composable
fun LauncherTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val accent = Color(settings.accent)
    val ctx = LocalContext.current
    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = lerp(accent, Color.White, 0.25f),
            onPrimary = Color.Black,
            primaryContainer = lerp(accent, Color.Black, 0.45f),
            onPrimaryContainer = Color.White,
            secondaryContainer = lerp(accent, Color(0xFF1C1C22), 0.75f),
            surface = Color(0xFF121216),
            background = Color(0xFF0E0E12),
            surfaceVariant = Color(0xFF24242C),
            surfaceContainer = Color(0xFF1A1A20),
            surfaceContainerHigh = Color(0xFF22222A),
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, Color.White, 0.75f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
            secondaryContainer = lerp(accent, Color.White, 0.85f),
            surface = Color(0xFFFAFAFD),
            background = Color(0xFFF4F4F8),
        )
    }
    if (settings.theme == ThemeMode.AMOLED) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainer = Color(0xFF0A0A0A), surfaceContainerHigh = Color(0xFF121212),
            surfaceVariant = Color(0xFF161616),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
