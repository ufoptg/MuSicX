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
    const val BACKGROUND: Int = 0xFFFFFFFF.toInt()      // Pure white
    const val SURFACE: Int = 0xFFF8F8F8.toInt()           // Very light gray
    const val SURFACE_ELEVATED: Int = 0xFFF0F0F0.toInt() // Light gray
    const val SURFACE_DISABLED: Int = 0xFFE8E8E8.toInt()  // Disabled surfaces
    
    // Text colors
    const val TEXT_PRIMARY: Int = 0xFF000000.toInt()      // Pure black (high contrast)
    const val TEXT_SECONDARY: Int = 0xFF666666.toInt()    // Medium gray
    const val TEXT_TERTIARY: Int = 0xFF999999.toInt()     // Light gray
    const val TEXT_DISABLED: Int = 0xFFCCCCCC.toInt()     // Disabled text
    
    // Accent colors (kept minimal)
    const val ACCENT: Int = 0xFF000000.toInt()           // Black for primary actions
    const val ACCENT_HOVER: Int = 0xFF333333.toInt()      // Darker black for hover
    const val ACCENT_PRESSED: Int = 0xFF1A1A1A.toInt()    // Even darker black
    
    // Semantic colors (Material Design inspired)
    const val ERROR: Int = 0xFFD32F2F.toInt()            // Material error red
    const val WARNING: Int = 0xFFF57C00.toInt()          // Material warning orange
    const val SUCCESS: Int = 0xFF388E3C.toInt()           // Material success green
    const val INFO: Int = 0xFF1976D2.toInt()              // Material info blue
    
    // Overlay and translucency (note: these are rgba strings, may need special handling)
    const val OVERLAY_DARK: String = "rgba(0, 0, 0, 0.6)"    // Dark overlay
    const val OVERLAY_LIGHT: String = "rgba(255, 255, 255, 0.8)" // Light overlay
    
    // Border colors
    const val BORDER_PRIMARY: Int = 0xFFE0E0E0.toInt()     // Primary border
    const val BORDER_SUBTLE: Int = 0xFFEEEEEE.toInt()     // Subtle border
    const val BORDER_DISABLED: Int = 0xFFF5F5F5.toInt()   // Disabled border
}

/** Shadow levels (following Material Design 3 elevation) */
object Elevation {
    // No shadow (surface at ground level)
    val NONE: Dp = 0.dp
    
    // Extra small shadow
    val XS: Dp = 1.dp
    
    // Small shadow
    val SM: Dp = 3.dp
    
    // Medium shadow
    val MD: Dp = 6.dp
    
    // Large shadow
    val LG: Dp = 8.dp
    
    // Extra large shadow
    val XL: Dp = 12.dp
    
    // XX large shadow
    val XXL: Dp = 16.dp
}

/** Border radius tokens following Material Design 3 */
object BorderRadius {
    // Border radius values
    val NONE: Dp = 0.dp
    val XXXS: Dp = 2.dp
    val XXS: Dp = 4.dp
    val XS: Dp = 8.dp
    val SM: Dp = 12.dp
    val MD: Dp = 16.dp
    val LG: Dp = 28.dp
    val XL: Dp = 40.dp
    val XXL: Dp = 56.dp
    
    // Component-specific radii
    val COMPONENT_SMALL: Dp = 4.dp
    val COMPONENT_MEDIUM: Dp = 8.dp
    val COMPONENT_LARGE: Dp = 12.dp
    
    // Icon button radius
    val ICON_BUTTON: Dp = 20.dp
    
    // Card/image radius
    val CARD: Dp = 12.dp
    val DIALOG: Dp = 28.dp
}



/** Icon size tokens */
object IconSize {
    val XXXS: Dp = 12.dp
    val XXS: Dp = 16.dp
    val XS: Dp = 20.dp
    val SM: Dp = 24.dp
    val MD: Dp = 32.dp
    val LG: Dp = 36.dp
    val XL: Dp = 48.dp
    val XXL: Dp = 64.dp
}