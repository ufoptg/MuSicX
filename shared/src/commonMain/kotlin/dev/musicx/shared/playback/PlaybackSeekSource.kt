package dev.musicx.shared.playback

import dev.musicx.shared.youtube.playback.PlaybackEventKind
import dev.musicx.shared.youtube.playback.PlaybackEventLog

data class PlaybackSeekSource(
    val name: String,
    val details: Map<String, String> = emptyMap(),
) {
    val logDetails: Map<String, String> get() = details + ("seekSource" to name)

    fun record(
        fromPositionMs: Long,
        toPositionMs: Long,
        videoId: String?,
    ) {
        PlaybackEventLog.info(
            PlaybackEventKind.PLAYER,
            "Playback seek requested.",
            videoId = videoId,
            details = logDetails + mapOf("fromPositionMs" to fromPositionMs.toString(), "toPositionMs" to toPositionMs.toString()),
        )
    }

    companion object {
        val Other = PlaybackSeekSource("other")
    }
}
