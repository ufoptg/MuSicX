/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.ConnectivityManager
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException
import timber.log.Timber

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
) {
    companion object {
        /**
         * Cache-key prefix used to tell the resolving data source that this MediaItem must be
         * resolved as a video stream instead of the normal audio stream.
         */
        const val CACHE_KEY_PREFIX = "video:"
    }
}

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
    val lookup: VideoLookup = DefaultLookup,
    private val counterpartLookup: suspend (MediaMetadata) -> String? = { metadata ->
        findCounterpartVideoId(
            metadata = metadata,
            items =
                YouTube
                    .search(
                        query = buildCounterpartQuery(metadata),
                        filter = YouTube.SearchFilter.FILTER_VIDEO,
                    ).getOrNull()
                    ?.items
                    .orEmpty(),
        )
    },
) {
    /**
     * Asynchronously resolves a playable video stream URI for the given media metadata.
     *
     * Uses InnerTubeXPlayer.playerResponseForPlayback() to extract the stream and returns a
     * VideoPlaybackTarget populated with the discovered mediaUri (a real playable stream URL).
     * Returns null when no videoId or no playable video stream can be found.
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
        val videoId =
            if (mediaMetadata.isVideoSong) {
                mediaMetadata.id.takeIf { it.isNotBlank() }
            } else {
                counterpartLookup(mediaMetadata)
            }
        if (videoId.isNullOrBlank()) {
            Timber.tag(TAG).d("No video id for track %s (isVideo=%s)", mediaMetadata.id, mediaMetadata.isVideoSong)
            return null
        }

        return try {
            val result =
                InnerTubeXPlayer.playerResponseForPlayback(
                    videoId = videoId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    contentHints =
                        ContentHints(
                            isExplicit = mediaMetadata.explicit,
                            isUploaded = false,
                            wantVideo = true,
                        ),
                )

            result.fold(
                { playbackData ->
                    if (!playbackData.hasVideo || playbackData.streamUrl.isBlank()) {
                        Timber
                            .tag(TAG)
                            .w(
                                "Resolved stream for %s has no video track (mime=%s, %dx%d)",
                                videoId,
                                playbackData.format.mimeType,
                                playbackData.format.width ?: 0,
                                playbackData.format.height ?: 0,
                            )
                        null
                    } else {
                        VideoPlaybackTarget(
                            videoId = videoId,
                            mediaUri = playbackData.streamUrl,
                            streamHeaders = playbackData.streamHeaders,
                        )
                    }
                },
                { error ->
                    Timber.tag(TAG).w(error, "Video stream resolution failed for %s", videoId)
                    null
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            Timber.tag(TAG).w(error, "Video stream resolution failed for %s", videoId)
            null
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Video stream resolution failed for %s", videoId)
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
        private const val TAG = "VideoPlaybackResolver"

        private val DefaultLookup =
            VideoLookup { media ->
                // setVideoId is the playlist-entry token, never a YouTube video id. Video songs
                // always play their own id; audio tracks need an async counterpart lookup.
                val videoId = media.id.takeIf { media.isVideoSong && it.isNotBlank() }
                videoId?.let { VideoLookupResult(videoId = it, mediaUri = null) }
            }
    }
}

internal fun buildCounterpartQuery(metadata: MediaMetadata): String {
    val artist = metadata.artists.firstOrNull()?.name.orEmpty().trim()
    return buildString {
        append(metadata.title.trim())
        if (artist.isNotBlank()) append(' ').append(artist)
        append(" official video")
    }.trim()
}

/**
 * Picks the most likely official music video for an audio track from YouTube search results.
 * Kept pure so it can be unit tested without network access.
 */
internal fun findCounterpartVideoId(
    metadata: MediaMetadata,
    items: List<YTItem>,
): String? {
    if (metadata.isVideoSong) return null
    val targetTitle = normalizeVideoTitle(metadata.title)
    if (targetTitle.isBlank()) return null
    val targetArtist = normalizeVideoTitle(metadata.artists.firstOrNull()?.name.orEmpty())

    return items
        .asSequence()
        .filterIsInstance<SongItem>()
        .filter { it.id != metadata.id && it.id.isNotBlank() }
        .filter { it.isVideoSong }
        .mapNotNull { candidate ->
            val candidateTitle = normalizeVideoTitle(candidate.title)
            val titleMatches =
                candidateTitle == targetTitle ||
                    candidateTitle.contains(targetTitle) ||
                    targetTitle.contains(candidateTitle)
            if (!titleMatches) return@mapNotNull null

            val artistMatches =
                targetArtist.isBlank() ||
                    candidate.artists.any { artist ->
                        val candidateArtist = normalizeVideoTitle(artist.name)
                        candidateArtist.isNotBlank() &&
                            (candidateArtist.contains(targetArtist) || targetArtist.contains(candidateArtist))
                    }
            val mediaDuration = metadata.duration
            val candidateDuration = candidate.duration
            val durationMatches =
                mediaDuration <= 0 ||
                    candidateDuration == null ||
                    kotlin.math.abs(candidateDuration - mediaDuration) <= MAX_DURATION_DELTA_SECONDS
            if (!artistMatches && !durationMatches) return@mapNotNull null

            val score =
                when {
                    candidate.musicVideoType?.contains("OMV", ignoreCase = true) == true -> 3
                    candidate.musicVideoType?.contains("UGC", ignoreCase = true) == true -> 2
                    else -> 1
                } * 100 +
                    if (candidateTitle == targetTitle) 50 else 0 +
                    if (artistMatches) 25 else 0 -
                    if (mediaDuration > 0 && candidateDuration != null) {
                        kotlin.math.abs(candidateDuration - mediaDuration)
                    } else {
                        0
                    }
            candidate to score
        }.maxByOrNull { it.second }
        ?.first
        ?.id
}

private const val MAX_DURATION_DELTA_SECONDS = 45

private fun normalizeVideoTitle(value: String): String =
    value
        .lowercase()
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(
            Regex("\\b(official|music|video|lyric|lyrics|audio|visualizer|mv|m v|hd|hq|4k|8k)\\b"),
            " ",
        ).replace(Regex("\\s+"), " ")
        .trim()
