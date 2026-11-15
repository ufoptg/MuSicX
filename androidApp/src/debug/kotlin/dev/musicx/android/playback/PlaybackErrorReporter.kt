package dev.musicx.android.playback

import dev.musicx.shared.model.SongItem
import dev.musicx.shared.playback.PlaybackErrorContext
import dev.musicx.shared.youtube.playback.PlaybackResource

internal object PlaybackErrorReporter {
    fun setActivePlayback(
        song: SongItem,
        resource: PlaybackResource?,
    ) = Unit

    fun clearActivePlayback() = Unit

    fun report(
        error: Throwable,
        context: PlaybackErrorContext,
    ) = Unit
}
