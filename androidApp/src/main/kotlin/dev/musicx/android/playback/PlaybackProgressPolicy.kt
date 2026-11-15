package dev.musicx.android.playback

import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.isLive

internal fun playbackDurationMs(
    backendDurationMs: Long,
    selectedSong: SongItem?,
    playbackPrepared: Boolean,
    isLive: Boolean = selectedSong?.isLive == true,
): Long =
    if (isLive) {
        0L
    } else {
        backendDurationMs.takeIf { playbackPrepared && it >= 0L }
            ?: selectedSong?.duration?.times(1_000L)?.coerceAtLeast(0L)
            ?: 0L
    }
