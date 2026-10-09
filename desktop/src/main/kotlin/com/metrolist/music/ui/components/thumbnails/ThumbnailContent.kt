/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Desktop Thumbnail Content - adapted for desktop experience
 */

package com.metrolist.music.ui.components.thumbnails

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun ThumbnailContent(
    thumbnailUrl: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "Thumbnail"
) {
    Box(modifier = modifier) {
        // For desktop, we'll use a simple approach without Coil3
        // Just show the default launcher icon for now
        Image(
            painter = painterResource("ic_launcher.png"),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Note: Removed background dimming for disabled thumbnails to avoid import issues
    }
}