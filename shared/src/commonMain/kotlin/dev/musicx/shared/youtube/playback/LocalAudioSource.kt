package dev.musicx.shared.youtube.playback

import dev.musicx.shared.model.SongItem

fun interface LocalAudioSource {
    suspend fun resolve(
        videoId: String,
        song: SongItem?,
    ): PlaybackResource?
}
