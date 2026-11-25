/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.InnerTubeXPlayer

/**
 * A watchable video counterpart for the current media, as required for inline video playback.
 *
 * @param videoId YouTube video id to play (the track itself for video songs, the counterpart for album tracks).
 * @param mediaUri Optional media uri / media item data needed by the player, when a direct uri is known.
 * @param streamHeaders Optional playback stream headers (e.g. authorization/cookies) required by the player.
 * @param format Optional extracted playback format data for the resolved stream.
 */
data class VideoPlaybackTarget(
    val videoId: String,
    val mediaUri: String? = null,
    val streamHeaders: Map<String, String> = emptyMap(),
    val format: InnerTubeXPlayer.PlaybackData? = null,
)

fun interface VideoLookup {
    fun lookup(metadata: MediaMetadata): VideoLookupResult?
}

data class VideoLookupResult(
    val videoId: String?,
    val mediaUri: String? = null,
)

/**
 * Resolves a watchable video target for the current media before committing to video mode.
 *
 * The lookup is injectable so callers (and tests) can supply their own counterpart resolution.
 */
open class VideoPlaybackResolver(
    val lookup: VideoLookup = VideoPlaybackResolver.DefaultLookup,
) {
    open fun resolve(mediaMetadata: MediaMetadata?): VideoPlaybackTarget? {
        if (mediaMetadata == null) return null
        val result = lookup.lookup(mediaMetadata) ?: return null
        val videoId = result.videoId ?: return null
        return VideoPlaybackTarget(
            videoId = videoId,
            mediaUri = result.mediaUri,
            streamHeaders = emptyMap(),
            format = null,
        )
    }

    companion object {
        private val DefaultLookup: VideoLookup = VideoLookup { media ->
            val videoId = if (media.isVideoSong) media.setVideoId ?: media.id else media.setVideoId
            if (videoId.isNullOrBlank()) {
                null
            } else {
                VideoLookupResult(videoId = videoId, mediaUri = "https://www.youtube.com/watch?v=$videoId")
            }
        }
    }
}
