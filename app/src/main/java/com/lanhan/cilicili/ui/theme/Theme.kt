package com.lanhan.cilicili.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lanhan.cilicili.domain.model.ThemeMode

@Immutable
data class InstrumentColors(
    val isDark: Boolean,
    val bg: Color,
    val panel: Color,
    val chip: Color,
    val dock: Color,
    val text: Color,
    val muted: Color,
    val hairline: Color,
    val accent: Color,
    val onAccent: Color,
    val ok: Color,
    val warn: Color,
    val fail: Color,
    val track: Color,
    val tick: Color,
    val activeBg: Color,
    val activeText: Color,
    val switchOffBg: Color,
    val switchOffKnob: Color,
    val dotGrid: Color,
    val stealthBg: Color,
    val stealthSurface: Color,
    val stealthInk: Color,
    val stealthBorder: Color,
    val stealthChipBg: Color,
)

private val GraphitePalette = InstrumentColors(
    isDark = true,
    bg = Color(0xFF101216),
    panel = Color(0xFF181B21),
    chip = Color(0xFF21252D),
    dock = Color(0xFF0C0E12),
    text = Color(0xFFF3F5F8),
    muted = Color(0xFF9AA3B2),
    hairline = Color(0x26FFFFFF),
    accent = Color(0xFFFF4F1F),
    onAccent = Color(0xFFFFFFFF),
    ok = Color(0xFF22C55E),
    warn = Color(0xFFEAB308),
    fail = Color(0xFF475060),
    track = Color(0xFF2F3540),
    tick = Color(0xFF646D7C),
    activeBg = Color(0xFFF3F5F8),
    activeText = Color(0xFF101216),
    switchOffBg = Color(0xFF2B303A),
    switchOffKnob = Color(0xFF9AA3B2),
    dotGrid = Color(0x16FFFFFF),
    stealthBg = Color(0xFF131722),
    stealthSurface = Color(0xFF171C28),
    stealthInk = Color(0xFFA5B4FC),
    stealthBorder = Color(0x4DA5B4FC),
    stealthChipBg = Color(0x1FA5B4FC),
)

private val BraunLightPalette = InstrumentColors(
    isDark = false,
    bg = Color(0xFFEDEEF0),
    panel = Color(0xFFF9FAF8),
    chip = Color(0xFFE3E5DF),
    dock = Color(0xFFE3E5E0),
    text = Color(0xFF131518),
    muted = Color(0xFF525864),
    hairline = Color(0x2E131518),
    accent = Color(0xFFFF4F1F),
    onAccent = Color(0xFFFFFFFF),
    ok = Color(0xFF16A34A),
    warn = Color(0xFFCA8A04),
    fail = Color(0xFFB2B7AE),
    track = Color(0xFFC5C9C0),
    tick = Color(0xFF6B7280),
    activeBg = Color(0xFF181B20),
    activeText = Color(0xFFF9FAF8),
    switchOffBg = Color(0xFFCFD3CA),
    switchOffKnob = Color(0xFF525864),
    dotGrid = Color(0x1A131518),
    stealthBg = Color(0xFFE5EAF2),
    stealthSurface = Color(0xFFF3F6FA),
    stealthInk = Color(0xFF2B3445),
    stealthBorder = Color(0x522B3445),
    stealthChipBg = Color(0x1A2B3445),
)

val LocalInstrumentColors = staticCompositionLocalOf { GraphitePalette }

object InstrumentTheme {
    val colors: InstrumentColors
        @Composable get() = LocalInstrumentColors.current
}

private val GraphiteColorScheme = darkColorScheme(
    primary = GraphitePalette.accent,
    onPrimary = GraphitePalette.onAccent,
    primaryContainer = Color(0xFF3A1A12),
    onPrimaryContainer = Color(0xFFFFB59E),
    secondary = GraphitePalette.text,
    onSecondary = GraphitePalette.bg,
    secondaryContainer = GraphitePalette.chip,
    onSecondaryContainer = GraphitePalette.text,
    tertiary = GraphitePalette.warn,
    onTertiary = Color(0xFF181400),
    tertiaryContainer = Color(0xFF332908),
    onTertiaryContainer = Color(0xFFFDE047),
    background = GraphitePalette.bg,
    onBackground = GraphitePalette.text,
    surface = GraphitePalette.panel,
    onSurface = GraphitePalette.text,
    surfaceVariant = GraphitePalette.chip,
    onSurfaceVariant = GraphitePalette.muted,
    surfaceContainerLow = GraphitePalette.panel,
    surfaceContainer = GraphitePalette.panel,
    surfaceContainerHigh = GraphitePalette.chip,
    outline = GraphitePalette.hairline,
    outlineVariant = GraphitePalette.hairline,
    error = Color(0xFFEF4444),
    onError = Color.White,
)

private val BraunLightColorScheme = lightColorScheme(
    primary = BraunLightPalette.accent,
    onPrimary = BraunLightPalette.onAccent,
    primaryContainer = Color(0xFFFFE4DB),
    onPrimaryContainer = Color(0xFF5C1500),
    secondary = BraunLightPalette.text,
    onSecondary = BraunLightPalette.panel,
    secondaryContainer = BraunLightPalette.chip,
    onSecondaryContainer = BraunLightPalette.text,
    tertiary = BraunLightPalette.warn,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF713F12),
    background = BraunLightPalette.bg,
    onBackground = BraunLightPalette.text,
    surface = BraunLightPalette.panel,
    onSurface = BraunLightPalette.text,
    surfaceVariant = BraunLightPalette.chip,
    onSurfaceVariant = BraunLightPalette.muted,
    surfaceContainerLow = BraunLightPalette.panel,
    surfaceContainer = BraunLightPalette.panel,
    surfaceContainerHigh = BraunLightPalette.chip,
    outline = BraunLightPalette.hairline,
    outlineVariant = BraunLightPalette.hairline,
    error = Color(0xFFDC2626),
    onError = Color.White,
)

@Composable
fun CiliTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val palette = if (darkTheme) GraphitePalette else BraunLightPalette
    val colorScheme = if (darkTheme) GraphiteColorScheme else BraunLightColorScheme

    CompositionLocalProvider(LocalInstrumentColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
