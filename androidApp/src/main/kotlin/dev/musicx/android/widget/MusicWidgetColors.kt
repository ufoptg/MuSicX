package dev.musicx.android.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.musicx.shared.settings.ThemePaletteStyle
import dev.musicx.shared.ui.theme.themeColorScheme

internal fun musicWidgetColorScheme(
    artworkSeed: Color?,
    isDark: Boolean,
    systemScheme: () -> ColorScheme,
): ColorScheme = artworkSeed?.let { themeColorScheme(it, isDark, ThemePaletteStyle.TONAL_SPOT) } ?: systemScheme()
