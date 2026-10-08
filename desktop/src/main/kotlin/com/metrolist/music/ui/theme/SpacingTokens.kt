package com.metrolist.music.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MuSicX Project Design System - Spacing Tokens
 * Following Material Design 3 spacing scale
 */

/** Spacing tokens in density-independent pixels (dp) */
object Spacing {
    // Base unit
    val UNIT: Dp = 4.dp  // 4dp is Material Design's baseline
    
    // Minimal spacing
    val NONE: Dp = 0.dp
    val XXXS: Dp = 2.dp
    val XXS: Dp = 4.dp
    
    // Component spacing
    val XS: Dp = 8.dp
    val SM: Dp = 12.dp
    val MD: Dp = 16.dp
    val LG: Dp = 24.dp
    val XL: Dp = 32.dp
    val XXL: Dp = 48.dp
    val XXXL: Dp = 64.dp
    
    // Container padding
    val CONTAINER_PADDING_SCREEN: Dp = 16.dp
    val CONTAINER_PADDING_SURFACE: Dp = 24.dp
    
    // Section spacing
    val SECTION_GAP: Dp = 32.dp
    val SECTION_PADDING: Dp = 24.dp
    
    // List spacing
    val LIST_ITEM_GAP: Dp = 8.dp
    val LIST_ITEM_PADDING: Dp = 16.dp
    
    // Dialog/Modal spacing
    val DIALOG_PADDING: Dp = 24.dp
    val DIALOG_CONTENT_GAP: Dp = 16.dp
    
    // Form spacing
    val FORM_FIELD_GAP: Dp = 16.dp
    val FORM_FIELD_PADDING: Dp = 12.dp
    val FORM_HELPER_GAP: Dp = 4.dp
    
    // Button spacing
    val BUTTON_GAP: Dp = 8.dp
    val BUTTON_ICON_GAP: Dp = 8.dp
    
    // Card spacing
    val CARD_GAP: Dp = 16.dp
    val CARD_PADDING: Dp = 20.dp
    
    // Navigation spacing
    val NAVIGATION_GAP: Dp = 4.dp
    val NAVIGATION_PADDING: Dp = 8.dp
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