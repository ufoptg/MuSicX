/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.metrolist.music.constants.ThumbnailCornerRadius

/**
 * Inline video surface for now-playing playback.
 *
 * Hosts media3's [PlayerView] (from media3-ui) inside a Composable so video can render inline
 * in the artwork slot. When `exoPlayer` is null, nothing is rendered
 * (the caller should fall back to artwork).
 */
@Composable
fun MusicVideoSurface(
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = ThumbnailCornerRadius,
) {
    val context = LocalContext.current
    val exoPlayerRef = rememberUpdatedState(exoPlayer)

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                )
                clipToOutline = true
                exoPlayerRef.value?.let { player = it }
            }
        },
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius)),
        update = { view ->
            view.player = exoPlayerRef.value
        },
    )
}