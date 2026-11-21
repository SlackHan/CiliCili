package com.lanhan.cilicili.ui.theme

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
import com.lanhan.cilicili.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFF006A60),
    surface = Color(0xFFF9F9FC),
    background = Color(0xFFF9F9FC),
    surfaceContainerLow = Color(0xFFF3F4F9),
    surfaceContainer = Color(0xFFEFEFF5),
    surfaceContainerHigh = Color(0xFFECEEF4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3B4858),
    tertiary = Color(0xFF53DBC9),
    surface = Color(0xFF111318),
    background = Color(0xFF111318),
    surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF262A32),
)

@Composable
fun CiliTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
