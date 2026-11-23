/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.playback.queues

import com.metrolist.shared.playback.MusicQueue
import com.metrolist.shared.spotifymatch.SpotifyYouTubeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.SpotifyPriorityGate
import meow.nyx.spotlib.TrackItem

/**
 * Queue implementation that loads tracks from a Spotify playlist.
 *
 * Optimized for fast playback start: only the selected track is resolved
 * during [loadInto], while the remaining tracks are resolved progressively
 * in [appendNextPage] batches as the player approaches the end of the
 * currently loaded queue.
 */
class SpotifyPlaylistQueue(
    private val client: SpotifyClient,
    private val playlistUri: String,
    private val startIndex: Int = 0,
    private val mapper: SpotifyYouTubeMapper,
) {
    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
        private const val RESOLVE_BATCH_SIZE = 20
        private const val FAST_START_BEFORE = 0
        private const val FAST_START_AFTER = 2
    }

    // All Spotify tracks fetched so far (may span multiple API pages)
    private val allTracks = mutableListOf<TrackItem>()

    // Index into [allTracks] for the next batch to resolve to YouTube
    private var resolveOffset = 0

    // Spotify API pagination state
    private var apiFetchOffset = 0
    private var apiHasMore = true

    /** Serializes concurrent [appendNextPage] invocations. */
    private val pageMutex = Mutex()

    /**
     * Loads the playlist tracks and sets the queue with a fast-start window.
     *
     * @param queue the [MusicQueue] to populate
     * @param startIndex the index of the target track within the full playlist
     */
    suspend fun loadInto(
        queue: MusicQueue,
        startIndex: Int = 0,
    ) {
        withContext(Dispatchers.IO) {
            val fetched = fetchPlaylistPage(limit = SPOTIFY_PAGE_SIZE)
            allTracks.addAll(fetched)
            apiFetchOffset = fetched.size
            apiHasMore = fetched.size == SPOTIFY_PAGE_SIZE

            while (startIndex >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }

            val targetIndex = startIndex.coerceIn(0, (allTracks.size - 1).coerceAtLeast(0))
            val windowStart = (targetIndex - FAST_START_BEFORE).coerceAtLeast(0)
            val windowEnd = (targetIndex + FAST_START_AFTER + 1).coerceAtMost(allTracks.size)
            val windowTracks = allTracks.subList(windowStart, windowEnd)

            val resolvedItems =
                coroutineScope {
                    windowTracks
                        .map { track -> async { mapper.mapToYouTube(track) } }
                        .awaitAll()
                        .filterNotNull()
                }

            if (resolvedItems.isEmpty()) {
                // Nothing resolved — leave the queue empty and stop early.
            } else {
                resolveOffset = windowEnd
                val mediaItemIndex =
                    (targetIndex - windowStart)
                        .coerceIn(0, (resolvedItems.size - 1).coerceAtLeast(0))

                queue.setQueue(resolvedItems, mediaItemIndex)
            }
        }
    }

    /**
     * Fetches the next batch of tracks from the Spotify API and appends them to the queue.
     *
     * @param queue the [MusicQueue] to append the batch to
     */
    suspend fun appendNextPage(queue: MusicQueue) {
        withContext(Dispatchers.IO) {
            pageMutex.withLock {
                if (resolveOffset >= allTracks.size && apiHasMore) {
                    fetchNextApiPage()
                }

                if (resolveOffset >= allTracks.size) {
                    queue.appendPage(emptyList())
                } else {
                    val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
                    val batch = allTracks.subList(resolveOffset, end)
                    resolveOffset = end

                    coroutineScope {
                        batch
                            .map { track -> async { mapper.mapToYouTube(track) } }
                            .awaitAll()
                            .filterNotNull()
                    }.also { resolved ->
                        queue.appendPage(resolved)
                    }
                }
            }
        }
    }

    private suspend fun fetchPlaylistPage(limit: Int): List<TrackItem> =
        SpotifyPriorityGate.withPlaybackPriority {
            val all = client.playlistTracks(playlistUri, maxTracks = apiFetchOffset + limit)
            all.subList(apiFetchOffset, all.size)
        }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        try {
            val newTracks =
                SpotifyPriorityGate.withPlaybackPriority {
                    val all = client.playlistTracks(playlistUri, maxTracks = apiFetchOffset + SPOTIFY_PAGE_SIZE)
                    all.subList(apiFetchOffset, all.size)
                }
            allTracks.addAll(newTracks)
            apiFetchOffset += newTracks.size
            apiHasMore = newTracks.size == SPOTIFY_PAGE_SIZE
        } catch (e: Exception) {
            apiHasMore = false
        }
    }
}
