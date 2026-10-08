package com.metrolist.music.ui.theme

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

/** Spacing tokens following Material Design 3 (in dp) */
object Spacing {
    // Standard spacing scale
    const val NONE: String = "0dp"
    const val XXXS: String = "2dp"
    const val XXS: String = "4dp"
    const val XS: String = "8dp"
    const val SM: String = "12dp"
    const val MD: String = "16dp"
    const val LG: String = "24dp"
    const val XL: String = "32dp"
    const val XXL: String = "48dp"
    const val XXXL: String = "64dp"
    
    // Special spacing values
    const val HALF: String = "50%"
    const val QUARTER: String = "25%"
    const val THREE_QUARTERS: String = "75%"
}

/** Typography tokens following Material Design 3 */
object Typography {
    // Font families
    const val PRIMARY: String = "Roboto, sans-serif"
    const val MONOSPACE: String = ""Monaco, Menlo, Consolas, "Courier New", monospace"
    
    // Font weights
    const val LIGHT: String = "300"
    const val NORMAL: String = "400"
    const val MEDIUM: String = "500"
    const val BOLD: String = "700"
    
    // Font sizes (in sp)
    const val DISPLAY_LARGE: String = "57sp"
    const val DISPLAY_MEDIUM: String = "45sp"
    const val DISPLAY_SMALL: String = "36sp"
    const val HEADLINE_LARGE: String = "32sp"
    const val HEADLINE_MEDIUM: String = "28sp"
    const val HEADLINE_SMALL: String = "24sp"
    const val TITLE_LARGE: String = "22sp"
    const val TITLE_MEDIUM: String = "16sp"
    const val TITLE_SMALL: String = "14sp"
    const val BODY_LARGE: String = "16sp"
    const val BODY_MEDIUM: String = "14sp"
    const val BODY_SMALL: String = "12sp"
    const val LABEL_LARGE: String = "14sp"
    const val LABEL_MEDIUM: String = "12sp"
    const val LABEL_SMALL: String = "11sp"
    
    // Line heights
    const val LINE_HEIGHT_NONE: String = "100%"
    const val LINE_HEIGHT_SHORT: String = "120%"
    const val LINE_HEIGHT_DEFAULT: String = "140%"
    const val LINE_HEIGHT_MIXED: String = "160%"
    
    // Letter spacing
    const val TRACKING_MIN: String = "-0.25px"
    const val TRACKING_NORMAL: String = "0.5px"
    const val TRACKING_MAX: String = "1.5px"
}

/** Border radius tokens following Material Design 3 */
object BorderRadius {
    // Border radius values
    const val NONE: String = "0dp"
    const val XXXS: String = "2dp"
    const val XXS: String = "4dp"
    const val XS: String = "8dp"
    const val SM: String = "12dp"
    const val MD: String = "16dp"
    const val LG: String = "28dp"
    const val XL: String = "40dp"
    const val XXL: String = "56dp"
    
    // Component-specific radii
    const val COMPONENT_SMALL: String = "4dp"
    const val COMPONENT_MEDIUM: String = "8dp"
    const val COMPONENT_LARGE: String = "12dp"
    
    // Icon button radius
    const val ICON_BUTTON: String = "20dp"
    
    // Card/image radius
    const val CARD: String = "12dp"
    const val DIALOG: String = "28dp"
}

/** Elevation/shadow tokens */
object Elevation {
    // Shadow levels (following Material Design 3 elevation)
    const val NONE: String = "0dp"
    const val XS: String = "1dp"
    const val SM: String = "3dp"
    const val MD: String = "6dp"
    const val LG: String = "8dp"
    const val XL: String = "12dp"
    const val XXL: String = "16dp"
    const val XXXL: String = "24dp"
    
    // Shadow colors
    const val SHADOW_LIGHT: String = "rgba(0, 0, 0, 0.06)"
    const val SHADOW_MEDIUM: String = "rgba(0, 0, 0, 0.12)"
    const val SHADOW_DARK: String = "rgba(0, 0, 0, 0.24)"
    const val SHADOW_INTENSIVE: String = "rgba(0, 0, 0, 0.48)"
}

/** Icon size tokens */
object IconSize {
    const val XXXS: String = "12dp"
    const val XXS: String = "16dp"
    const val XS: String = "20dp"
    const val SM: String = "24dp"
    const val MD: String = "32dp"
    const val LG: String = "36dp"
    const val XL: string = "48dp"
    const val XXL: String = "64dp"
}