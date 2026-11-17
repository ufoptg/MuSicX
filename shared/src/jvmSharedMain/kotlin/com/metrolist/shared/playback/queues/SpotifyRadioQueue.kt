/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.playback.queues

import com.metrolist.shared.playback.MusicQueue
import com.metrolist.shared.spotifymatch.SpotifyYouTubeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.SpotifyRecommendationEngine
import meow.nyx.spotlib.SpotifyPriorityGate
import meow.nyx.spotlib.TrackItem

/**
 * Queue implementation that builds a personalized radio-like queue using the
 * [SpotifyRecommendationEngine].
 *
 * The engine builds a user taste profile from Spotify top tracks/artists,
 * then generates recommendations by combining seed-artist relevance, genre
 * similarity, user affinity, and popularity matching — with diversification
 * to avoid monotony.
 *
 * If the engine fails (e.g. network issues, not yet implemented), falls back
 * to a basic queue built from the seed artist's top tracks.
 *
 * Optimized for fast playback start:
 * - [loadInto] resolves ONLY the initial track and returns immediately.
 *   Playback begins as soon as the first track is available.
 * - The recommendation engine is deferred to the first call to
 *   [appendNextPage], which fires on a background coroutine after playback
 *   has already started — so the user never feels the cost.
 * - Subsequent tracks are resolved progressively in [appendNextPage] batches.
 */
class SpotifyRadioQueue(
    private val client: SpotifyClient,
    private val seedTrack: TrackItem,
    private val startIndex: Int = 0,
    private val mapper: SpotifyYouTubeMapper,
) {

    companion object {
        private const val RESOLVE_BATCH_SIZE = 20
        private const val RECOMMENDATION_TIMEOUT_MS = 4000L
        private const val FALLBACK_ARTIST_LIMIT = 2
        private const val FALLBACK_TOP_TRACKS_LIMIT = 50
    }

    // All Spotify tracks available in this radio queue (seed + recommendations + fallback)
    private val allTracks = mutableListOf<TrackItem>()

    // Index into [allTracks] for the next batch to resolve to YouTube
    private var resolveOffset = 0

    /** Whether the recommendation engine has already been consulted. */
    private var engineRan = false

    /** Serializes concurrent [appendNextPage] invocations. */
    private val pageMutex = Mutex()

    /**
     * Loads the seed track, resolves it, and sets the queue for instant playback.
     *
     * @param queue the [MusicQueue] to populate
     * @param startIndex the index of the target track (usually 0 for radio)
     */
    suspend fun loadInto(queue: MusicQueue, startIndex: Int = 0) {
        withContext(Dispatchers.IO) {
            // Fast start: resolve ONLY the seed track and return.
            // The recommendation engine — which can take 2–4s — is deferred
            // to the first [appendNextPage] call so playback can begin
            // the instant the first track is resolved.
            allTracks.add(seedTrack)

            val resolvedSeed = mapper.mapToYouTube(seedTrack)

            if (resolvedSeed == null) {
                // Seed track couldn't be resolved — leave the queue empty.
            } else {
                val mediaItemIndex = startIndex.coerceIn(0, 0)
                queue.setQueue(listOf(resolvedSeed), mediaItemIndex)
                resolveOffset = 1 // seed already resolved
            }
        }
    }

    /**
     * Fetches the next batch of recommended tracks and appends them to the queue.
     *
     * On first invocation: consults the recommendation engine to populate the
     * rest of the queue. This ~2–4s cost used to be paid synchronously in
     * [loadInto] but now runs on the background coroutine that fires after
     * playback of the initial track has already started.
     *
     * @param queue the [MusicQueue] to append the batch to
     */
    suspend fun appendNextPage(queue: MusicQueue) {
        withContext(Dispatchers.IO) {
            pageMutex.withLock {
                // First invocation: run the recommendation engine
                if (!engineRan) {
                    runRecommendationEngine()
                    engineRan = true
                }

                if (resolveOffset >= allTracks.size) {
                    queue.appendPage(emptyList())
                } else {
                    val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
                    val batch = allTracks.subList(resolveOffset, end)
                    resolveOffset = end

                    coroutineScope {
                        batch.map { track -> async { mapper.mapToYouTube(track) } }
                            .awaitAll()
                            .filterNotNull()
                    }.also { resolved ->
                        queue.appendPage(resolved)
                    }
                }
            }
        }
    }

    private suspend fun runRecommendationEngine() {
        try {
            val recommendations = withContext(Dispatchers.IO) {
                try {
                    withTimeout(RECOMMENDATION_TIMEOUT_MS) {
                        SpotifyRecommendationEngine.getRecommendations(seedTrack)
                    }
                } catch (e: TimeoutCancellationException) {
                    buildFallbackQueue()
                    null
                }
            }

            if (recommendations != null && recommendations.isNotEmpty()) {
                allTracks.addAll(recommendations)
            } else if (recommendations == null) {
                buildFallbackQueue()
            }
        } catch (e: Exception) {
            buildFallbackQueue()
        }
    }

    /**
     * Basic fallback queue: seed artist's top tracks + same album, shuffled.
     * Used when the recommendation engine is unavailable or returns empty.
     */
    private suspend fun buildFallbackQueue() {
        val seenIds = mutableSetOf(seedTrack.uri)

        // Use up to 2 seed artists for top tracks
        for (artistId in seedTrack.artists.take(FALLBACK_ARTIST_LIMIT)) {
            val topTracks = SpotifyPriorityGate.withPlaybackPriority {
                client.artistTopTracks(artistId, limit = FALLBACK_TOP_TRACKS_LIMIT)
            }
            if (topTracks.isNotEmpty()) {
                val newTracks = topTracks.filter { it.uri.isNotEmpty() && it.uri !in seenIds }
                allTracks.addAll(newTracks)
                seenIds.addAll(newTracks.map { it.uri })
            }
        }

        // Add tracks from the same album if available
        seedTrack.albumUri?.let { albumUri ->
            val album = SpotifyPriorityGate.withPlaybackPriority {
                client.album(albumUri)
            }
            val albumTracks = album.filter { it.uri.isNotEmpty() && it.uri !in seenIds }
            if (albumTracks.isNotEmpty()) {
                allTracks.addAll(albumTracks)
                seenIds.addAll(albumTracks.map { it.uri })
            }
        }

        allTracks.shuffle()
    }
}
