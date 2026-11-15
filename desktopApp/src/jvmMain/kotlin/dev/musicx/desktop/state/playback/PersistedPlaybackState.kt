package dev.musicx.desktop.state.playback

import dev.musicx.shared.model.RepeatMode
import dev.musicx.shared.playback.MusicQueueSnapshot
import dev.musicx.shared.playback.StoredQueueSnapshotSerializer
import kotlinx.serialization.Serializable

@Serializable
data class PersistedPlaybackState(
    @Serializable(with = StoredQueueSnapshotSerializer::class)
    val queue: MusicQueueSnapshot,
    val positionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val revision: String? = null,
)
