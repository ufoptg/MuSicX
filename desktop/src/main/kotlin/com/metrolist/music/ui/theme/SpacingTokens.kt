package com.metrolist.music.ui.theme

/**
 * MuSicX Project Design System - Spacing Tokens
 * Following Material Design 3 spacing scale
 */

/** Spacing tokens in density-independent pixels (dp) */
object Spacing {
    // Base unit
    const val UNIT: Dp = 4.dp  // 4dp is Material Design's baseline
    
    // Minimal spacing
    const val NONE: Dp = 0.dp
    const val XXXS: Dp = 2.dp
    const val XXS: Dp = 4.dp
    
    // Component spacing
    const val XS: Dp = 8.dp
    const val SM: Dp = 12.dp
    const val MD: Dp = 16.dp
    const val LG: Dp = 24.dp
    const val XL: Dp = 32.dp
    const val XXL: Dp = 48.dp
    const val XXXL: Dp = 64.dp
    
    // Container padding
    const val CONTAINER_PADDING_SCREEN: Dp = 16.dp
    const val CONTAINER_PADDING_SURFACE: Dp = 24.dp
    
    // Section spacing
    const val SECTION_GAP: Dp = 32.dp
    const val SECTION_PADDING: Dp = 24.dp
    
    // List spacing
    const val LIST_ITEM_GAP: Dp = 8.dp
    const val LIST_ITEM_PADDING: Dp = 16.dp
    
    // Dialog/Modal spacing
    const val DIALOG_PADDING: Dp = 24.dp
    const val DIALOG_CONTENT_GAP: Dp = 16.dp
    
    // Form spacing
    const val FORM_FIELD_GAP: Dp = 16.dp
    const val FORM_FIELD_PADDING: Dp = 12.dp
    const val FORM_HELPER_GAP: Dp = 4.dp
    
    // Button spacing
    const val BUTTON_GAP: Dp = 8.dp
    const val BUTTON_ICON_GAP: Dp = 8.dp
    
    // Card spacing
    const val CARD_GAP: Dp = 16.dp
    const val CARD_PADDING: Dp = 20.dp
    
    // Navigation spacing
    const val NAVIGATION_GAP: Dp = 4.dp
    const val NAVIGATION_PADDING: Dp = 8.dp
}

/** Responsive spacing multipliers for different screen sizes */
object ResponsiveSpacing {
    // Small screens (mobile)
    const val SMALL_SCREEN_MULTIPLIER: Double = 0.8
    
    // Medium screens (tablet)
    const val MEDIUM_SCREEN_MULTIPLIER: Double = 1.0
    
    // Large screens (desktop)
    const val LARGE_SCREEN_MULTIPLIER: Double = 1.2
    
    // Extra large screens
    const val EXTRA_LARGE_SCREEN_MULTIPLIER: Double = 1.4
}