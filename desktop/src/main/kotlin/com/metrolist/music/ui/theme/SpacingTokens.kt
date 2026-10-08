package com.metrolist.music.ui.theme

/**
 * MuSicX Project Design System - Spacing Tokens
 * Following Material Design 3 spacing scale
 */

/** Spacing tokens in density-independent pixels (dp) */
object Spacing {
    // Base unit
    const val UNIT: String = "4dp"  // 4dp is Material Design's baseline
    
    // Minimal spacing
    const val NONE: String = "0dp"
    const val XXXS: String = "2dp"
    const val XXS: String = "4dp"
    
    // Component spacing
    const val XS: String = "8dp"
    const val SM: String = "12dp"
    const val MD: String = "16dp"
    const val LG: String = "24dp"
    const val XL: String = "32dp"
    const val XXL: String = "48dp"
    const val XXXL: String = "64dp"
    
    // Container padding
    const val CONTAINER_PADDING_SCREEN: String = "16dp"
    const val CONTAINER_PADDING_SURFACE: String = "24dp"
    
    // Section spacing
    const val SECTION_GAP: String = "32dp"
    const val SECTION_PADDING: String = "24dp"
    
    // List spacing
    const val LIST_ITEM_GAP: String = "8dp"
    const val LIST_ITEM_PADDING: String = "16dp"
    
    // Dialog/Modal spacing
    const val DIALOG_PADDING: String = "24dp"
    const val DIALOG_CONTENT_GAP: String = "16dp"
    
    // Form spacing
    const val FORM_FIELD_GAP: String = "16dp"
    const val FORM_FIELD_PADDING: String = "12dp"
    const val FORM_HELPER_GAP: String = "4dp"
    
    // Button spacing
    const val BUTTON_GAP: String = "8dp"
    const val BUTTON_ICON_GAP: String = "8dp"
    
    // Card spacing
    const val CARD_GAP: String = "16dp"
    const val CARD_PADDING: String = "20dp"
    
    // Navigation spacing
    const val NAVIGATION_GAP: String = "4dp"
    const val NAVIGATION_PADDING: String = "8dp"
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