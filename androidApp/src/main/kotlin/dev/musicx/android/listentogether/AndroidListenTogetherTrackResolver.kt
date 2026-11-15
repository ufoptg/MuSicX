package dev.musicx.android.listentogether

import dev.musicx.shared.listentogether.ListenTogetherTrackResolver
import dev.musicx.shared.listentogether.toSongItem
import dev.musicx.shared.model.SongItem

internal fun androidListenTogetherTrackResolver(
    metadata: suspend (String) -> SongItem?,
    fallbackArtist: suspend () -> String,
): ListenTogetherTrackResolver =
    ListenTogetherTrackResolver { track ->
        metadata(track.id) ?: track.toSongItem(
            fallbackArtist = fallbackArtist(),
            omitNonPositiveDuration = true,
        )
    }
