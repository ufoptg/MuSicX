/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.ConnectivityManager
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException

/**
 * A watchable video counterpart for the current media, as required for inline video playback.
 *
 * @param videoId YouTube video id to play (the track itself for video songs, the counterpart for album tracks).
 * @param mediaUri Optional playable media uri / MediaItem data needed by the player; null until a real
 * playable stream uri is resolved (watch-page urls are references, not ExoPlayer media data).
 * @param streamHeaders Optional playback stream headers (e.g. authorization/cookies) required by the player.
 */
data class VideoPlaybackTarget(
    val videoId: String,
    val mediaUri: String? = null,
    val streamHeaders: Map<String, String> = emptyMap(),
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
 * Resolution is asynchronous over the injected lookup; Task 3 consumes the returned target to switch playback.
 */
open class VideoPlaybackResolver(
    val lookup: VideoLookup = VideoPlaybackResolver.DefaultLookup,
) {
    /**
     * Asynchronously resolves a playable video stream URI for the given media metadata.
     *
     * Uses InnerTubeXPlayer.playerResponseForPlayback() to extract the stream and returns a
     * VideoPlaybackTarget populated with the discovered mediaUri (a real playable stream URL).
     * Returns null when no videoId or no playable stream can be found.
     *
     * @param mediaMetadata The media metadata for the current track
     * @param audioQuality The desired audio quality for extraction
     * @param connectivityManager The device's connectivity manager
     */
    suspend fun resolveVideoStream(
        mediaMetadata: MediaMetadata?,
        audioQuality: AudioQuality = AudioQuality.AUTO,
        connectivityManager: ConnectivityManager,
    ): VideoPlaybackTarget? {
        if (mediaMetadata == null) return null
        val videoId = if (mediaMetadata.isVideoSong) mediaMetadata.setVideoId ?: mediaMetadata.id else mediaMetadata.setVideoId
        if (videoId.isNullOrBlank()) return null

        return try {
            val result = InnerTubeXPlayer.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                contentHints = ContentHints(
                    isExplicit = mediaMetadata.explicit,
                    isUploaded = false,
                    wantVideo = true,
                ),
            )

            result.fold(
                { playbackData ->
                    VideoPlaybackTarget(
                        videoId = videoId,
                        mediaUri = playbackData.streamUrl,
                        streamHeaders = playbackData.streamHeaders,
                    )
                },
                { _ ->
                    null
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            null
        } catch (error: Exception) {
            null
        }
    }

    /**
     * Synchronous fallback resolver used by tests and existing callers.
     * Kept for backward compatibility; production path uses resolveVideoStream().
     */
    open fun resolve(mediaMetadata: MediaMetadata?): VideoPlaybackTarget? {
        if (mediaMetadata == null) return null
        val result = lookup.lookup(mediaMetadata) ?: return null
        val videoId = result.videoId ?: return null
        return VideoPlaybackTarget(
            videoId = videoId,
            mediaUri = result.mediaUri,
            streamHeaders = emptyMap(),
        )
    }

    companion object {
        private val DefaultLookup: VideoLookup = VideoLookup { media ->
            val videoId = if (media.isVideoSong) media.setVideoId ?: media.id else media.setVideoId
            if (videoId.isNullOrBlank()) {
                null
            } else {
                VideoLookupResult(videoId = videoId, mediaUri = null)
            }
        }
    }
}