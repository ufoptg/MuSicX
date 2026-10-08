package com.metrolist.music.ui.theme

/**
 * MuSicX Project Design System - Typography Tokens
 * Following Material Design 3 typography specifications
 */

/** Font families */
object FontFamilies {
    // Primary font family (Roboto for Material Design 3)
    const val PRIMARY: String = "Roboto, -apple-system, BlinkMacSystemFont, 'Segoe UI', Oxygen, Ubuntu, sans-serif"
    
    // Monospace font family
    const val MONOSPACE: String = "JetBrains Mono, Menlo, Monaco, Consolas, 'Courier New', monospace"
    
    // System font fallbacks
    const val SYSTEM: String = "system-ui, -apple-system, sans-serif"
}

/** Font weights (following Material Design 3) */
object FontWeights {
    // Light font weights
    const val LIGHT: String = "300"      // Light
    const val NORMAL: String = "400"     // Regular
    const val MEDIUM: String = "500"     // Medium
    const val SEMI_BOLD: String = "600"  // Semi-bold
    const val BOLD: String = "700"       // Bold
    const val BOLD_HEAVY: String = "800" // Extra bold
}

/** Typography levels following Material Design 3 hierarchy */
object Typography {
    // Display styles (for hero sections, large headings)
    object Display {
        const val LARGE: String = "57sp/64sp ${FontWeights.BOLD} ${FontWeights.LIGHT}"
        const val MEDIUM: String = "45sp/52sp ${FontWeights.BOLD} ${FontWeights.LIGHT}"
        const val SMALL: String = "36sp/44sp ${FontWeights.BOLD} ${FontWeights.LIGHT}"
    }
    
    // Headline styles (page/section titles)
    object Headline {
        const val LARGE: String = "32sp/40sp ${FontWeights.BOLD} ${FontWeights.LIGHT}"
        const val MEDIUM: String = "28sp/36sp ${FontWeights.BOLD} ${FontWeights.LIGHT}
        const val SMALL: String = "24sp/32sp ${FontWeights.BOLD} ${FontWeights.LIGHT}"
    }
    
    // Title styles (component/section titles)
    object Title {
        const val LARGE: String = "22sp/28sp ${FontWeights.MEDIUM} ${FontWeights.NORMAL}"
        const val MEDIUM: String = "16sp/24sp ${FontWeights.MEDIUM} ${FontWeights.NORMAL}"
        const val SMALL: String = "14sp/20sp ${FontWeights.MEDIUM} ${FontWeights.NORMAL}"
    }
    
    // Body styles (main content text)
    object Body {
        const val LARGE: String = "16sp/24sp ${FontWeights.NORMAL} ${FontWeights.NORMAL}"
        const val MEDIUM: String = "14sp/20sp ${FontWeights.NORMAL} ${FontWeights.NORMAL}"
        const val SMALL: String = "12sp/16sp ${FontWeights.NORMAL} ${FontWeights.NORMAL}"
    }
    
    // Label styles (form labels, buttons, captions)
    object Label {
        const val LARGE: String = "14sp/20sp ${FontWeights.MEDIUM} ${FontWeights.NORMAL}"
        const val MEDIUM: String = "12sp/16sp ${FontWeights.MEDIUM} ${FontWeights.NORMAL}"
        const val SMALL: String = "11sp/16sp ${FontWeights.MEDIUM} ${FontWeights.NORMAL}"
    }
}

/** Line heights (leading) following Material Design 3 */
object LineHeights {
    // Line height ratios
    const val NONE: String = "100%"
    const val SHORT: String = "120%"
    const val DEFAULT: String = "140%"
    const val MIXTURED: String = "160%"
    
    // Component-specific line heights
    const val BUTTON: String = "16px"
    const val INPUT: String = "20px"
    const val CARD: String = "24px"
    const val LIST_ITEM: String = "20px"
}

/** Letter spacing (tracking) following Material Design 3 */
object LetterSpacing {
    // Letter spacing values
    const val MIN: String = "-0.25px"
    const val NORMAL: String = "0.5px"
    const val MAX: String = "1.5px"
    
    // Component-specific tracking
    const val BUTTON: String = "0.5px"
    const val INPUT: String = "0.4px"
    const val HEADING: String = "-0.5px"
}

/** Typography scales for different screen sizes */
object ResponsiveTypography {
    // Small screens (mobile)
    const val SMALL_SCREEN_SCALE: Double = 0.9
    
    // Medium screens (tablet)
    const val MEDIUM_SCREEN_SCALE: Double = 1.0
    
    // Large screens (desktop)
    const val LARGE_SCREEN_SCALE: Double = 1.1
    
    // Extra large screens
    const val EXTRA_LARGE_SCREEN_SCALE: Double = 1.2
}

/** Typographic scale ratios */
object TypographyScale {
    // Display to Headline ratio
    const val DISPLAY_TO_HEADLINE: Double = 1.5
    
    // Headline to Title ratio
    const val HEADLINE_TO_TITLE: Double = 1.3
    
    // Title to Body ratio
    const val TITLE_TO_BODY: Double = 1.2
    
    // Body to Label ratio
    const val BODY_TO_LABEL: Double = 1.1
}