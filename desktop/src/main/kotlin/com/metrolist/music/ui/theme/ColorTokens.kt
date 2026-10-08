package com.metrolist.music.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MuSicX Project Design System - Black & White Theme
 * Colors follow Material Design 3 with black & white focus
 */

/** Primary colors - Black & White Theme */
object Colors {
    // Background colors
    const val BACKGROUND: Int = 0xFFFFFFFF      // Pure white
    const val SURFACE: Int = 0xFFF8F8F8           // Very light gray
    const val SURFACE_ELEVATED: Int = 0xFFF0F0F0 // Light gray
    const val SURFACE_DISABLED: Int = 0xFFE8E8E8  // Disabled surfaces
    
    // Text colors
    const val TEXT_PRIMARY: Int = 0xFF000000      // Pure black (high contrast)
    const val TEXT_SECONDARY: Int = 0xFF666666    // Medium gray
    const val TEXT_TERTIARY: Int = 0xFF999999     // Light gray
    const val TEXT_DISABLED: Int = 0xFFCCCCCC     // Disabled text
    
    // Accent colors (kept minimal)
    const val ACCENT: Int = 0xFF000000           // Black for primary actions
    const val ACCENT_HOVER: Int = 0xFF333333      // Darker black for hover
    const val ACCENT_PRESSED: Int = 0xFF1A1A1A    // Even darker black
    
    // Semantic colors (Material Design inspired)
    const val ERROR: Int = 0xFFD32F2F            // Material error red
    const val WARNING: Int = 0xFFF57C00          // Material warning orange
    const val SUCCESS: Int = 0xFF388E3C           // Material success green
    const val INFO: Int = 0xFF1976D2              // Material info blue
    
    // Overlay and translucency (note: these are rgba strings, may need special handling)
    const val OVERLAY_DARK: String = "rgba(0, 0, 0, 0.6)"    // Dark overlay
    const val OVERLAY_LIGHT: String = "rgba(255, 255, 255, 0.8)" // Light overlay
    
    // Border colors
    const val BORDER_PRIMARY: Int = 0xFFE0E0E0     // Primary border
    const val BORDER_SUBTLE: Int = 0xFFEEEEEE     // Subtle border
    const val BORDER_DISABLED: Int = 0xFFF5F5F5   // Disabled border
}

/** Shadow levels (following Material Design 3 elevation) */
object Elevation {
    // No shadow (surface at ground level)
    const val NONE: Dp = 0.dp
    
    // Extra small shadow
    const val XS: Dp = 1.dp
    
    // Small shadow
    const val SM: Dp = 3.dp
    
    // Medium shadow
    const val MD: Dp = 6.dp
    
    // Large shadow
    const val LG: Dp = 8.dp
    
    // Extra large shadow
    const val XL: Dp = 12.dp
    
    // XX large shadow
    const val XXL: Dp = 16.dp
}

/** Border radius tokens following Material Design 3 */
object BorderRadius {
    // Border radius values
    const val NONE: Dp = 0.dp
    const val XXXS: Dp = 2.dp
    const val XXS: Dp = 4.dp
    const val XS: Dp = 8.dp
    const val SM: Dp = 12.dp
    const val MD: Dp = 16.dp
    const val LG: Dp = 28.dp
    const val XL: Dp = 40.dp
    const val XXL: Dp = 56.dp
    
    // Component-specific radii
    const val COMPONENT_SMALL: Dp = 4.dp
    const val COMPONENT_MEDIUM: Dp = 8.dp
    const val COMPONENT_LARGE: Dp = 12.dp
    
    // Icon button radius
    const val ICON_BUTTON: Dp = 20.dp
    
    // Card/image radius
    const val CARD: Dp = 12.dp
    const val DIALOG: Dp = 28.dp
}

/** Elevation/shadow tokens */
object Elevation {
    // Shadow levels (following Material Design 3 elevation)
    const val NONE: Dp = 0.dp
    const val XS: Dp = 1.dp
    const val SM: Dp = 3.dp
    const val MD: Dp = 6.dp
    const val LG: Dp = 8.dp
    const val XL: Dp = 12.dp
    const val XXL: Dp = 16.dp
    const val XXXL: Dp = 24.dp
    
    // Shadow colors
    const val SHADOW_LIGHT: String = "rgba(0, 0, 0, 0.06)"
    const val SHADOW_MEDIUM: String = "rgba(0, 0, 0, 0.12)"
    const val SHADOW_DARK: String = "rgba(0, 0, 0, 0.24)"
    const val SHADOW_INTENSIVE: String = "rgba(0, 0, 0, 0.48)"
}

/** Icon size tokens */
object IconSize {
    const val XXXS: Dp = 12.dp
    const val XXS: Dp = 16.dp
    const val XS: Dp = 20.dp
    const val SM: Dp = 24.dp
    const val MD: Dp = 32.dp
    const val LG: Dp = 36.dp
    const val XL: Dp = 48.dp
    const val XXL: Dp = 64.dp
}