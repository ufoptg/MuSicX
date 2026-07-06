/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.SpotifyYouTubeMapper
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Queue implementation for Spotify Liked Songs (saved tracks).
 *
 * Optimized for fast playback start: only a small window around the selected
 * track is resolved during [getInitialStatus], while the remaining tracks are
 * resolved progressively in [nextPage] batches as the player approaches the
 * end of the currently loaded queue.
 *
 * @param preloadedTracks If non-empty, the queue uses this list as the full
 *   backing collection and skips the Spotify API pagination entirely. This
 *   is how the [com.metrolist.music.ui.screens.playlist.SpotifyLikedSongsScreen]
 *   avoids re-fetching the ~5000-track liked list that its ViewModel has
 *   already loaded, and how the Shuffle button ships a pre-shuffled ordering
 *   so shuffle randomizes across the entire liked-songs set.
 */
class SpotifyLikedSongsQueue(
    private val startIndex: Int = 0,
    private val mapper: SpotifyYouTubeMapper,
    private val preloadedTracks: List<SpotifyTrack> = emptyList(),
    override val preloadItem: MediaMetadata? = null,
) : Queue {

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
        private const val RESOLVE_BATCH_SIZE = 20
        /** Resolve only the target + a few neighbors for instant playback start.
         *  Kept small — awaitAll() blocks on the SLOWEST YouTube search of the
         *  window, and shuffled/cold-cache resolves can take 500 ms – 2 s each. */
        private const val FAST_START_BEFORE = 0
        private const val FAST_START_AFTER = 4
    }

    // All Spotify tracks fetched so far (may span multiple API pages)
    private val allTracks = mutableListOf<SpotifyTrack>()

    // Index into [allTracks] for the next batch to resolve to YouTube
    private var resolveOffset = 0

    // Spotify API pagination state
    private var apiFetchOffset = 0
    private var apiTotal = 0
    private var apiHasMore = true

    /** Serializes concurrent [nextPage] invocations. MusicService's initial-queue
     *  growth loop and its `onMediaItemTransition` handler can both call
     *  [nextPage] concurrently; without this mutex they race on [resolveOffset]
     *  and end up either double-resolving the same batch (duplicates in the
     *  visible queue) or skipping tracks entirely. */
    private val pageMutex = Mutex()

    override suspend fun getInitialStatus(): Queue.Status = withContext(Dispatchers.IO) {
        try {
            if (preloadedTracks.isNotEmpty()) {
                // Fast path: the caller (e.g. SpotifyLikedSongsScreen) already has the
                // full list of liked tracks in memory. Skip the ~105-request Spotify
                // pagination and use the preloaded list as the source of truth.
                allTracks.addAll(preloadedTracks)
                apiTotal = preloadedTracks.size
                apiFetchOffset = apiTotal
                apiHasMore = false
            } else {
                val result = Spotify.likedSongs(limit = SPOTIFY_PAGE_SIZE, offset = 0).getOrThrow()
                apiTotal = result.total
                val fetched = result.items.map { it.track }.filter { !it.isLocal }
                allTracks.addAll(fetched)
                apiFetchOffset = result.items.size
                apiHasMore = apiFetchOffset < apiTotal

                while (startIndex >= allTracks.size && apiHasMore) {
                    fetchNextApiPage()
                }
            }

            val targetIndex = startIndex.coerceIn(0, (allTracks.size - 1).coerceAtLeast(0))

            // Fast-start window — keep small. awaitAll() on this window is what
            // gates playback start, and every uncached YouTube search costs
            // 500 ms – 2 s. MusicService.playQueue then grows the visible queue
            // in the background *after* audio has started, so the queue depth
            // ends up around 60 within a few seconds without blocking startup.
            val windowStart = (targetIndex - FAST_START_BEFORE).coerceAtLeast(0)
            val windowEnd = (targetIndex + FAST_START_AFTER + 1).coerceAtMost(allTracks.size)
            val windowTracks = allTracks.subList(windowStart, windowEnd)

            val resolvedItems = coroutineScope {
                windowTracks.map { track -> async { mapper.resolveToMediaItem(track) } }
                    .awaitAll()
                    .filterNotNull()
            }

            if (resolvedItems.isEmpty()) {
                Timber.w("SpotifyLikedSongsQueue: Could not resolve any track in initial window")
                return@withContext Queue.Status(title = null, items = emptyList(), mediaItemIndex = 0)
            }

            resolveOffset = windowEnd

            val mediaItemIndex = (targetIndex - windowStart)
                .coerceIn(0, (resolvedItems.size - 1).coerceAtLeast(0))

            Timber.d("SpotifyLikedSongsQueue: Fast-start resolved ${resolvedItems.size} tracks " +
                "(window $windowStart..$windowEnd, target=$targetIndex, total=$apiTotal, " +
                "preloaded=${preloadedTracks.isNotEmpty()})")

            Queue.Status(
                title = null,
                items = resolvedItems,
                mediaItemIndex = mediaItemIndex,
            )
        } catch (e: Exception) {
            Timber.e(e, "SpotifyLikedSongsQueue: Failed initial fetch")
            Queue.Status(title = null, items = emptyList(), mediaItemIndex = 0)
        }
    }

    // Optional API used by MuSicX Spotify screens (not part of Queue interface).
    suspend fun getFullStatus(): Queue.Status? = withContext(Dispatchers.IO) {
        try {
            // Build full list from scratch so we always include tracks 0..N (not just from startIndex onwards)
            allTracks.clear()
            apiFetchOffset = 0
            apiHasMore = true
            while (apiFetchOffset == 0 || apiHasMore) {
                if (apiFetchOffset == 0) {
                    val result = Spotify.likedSongs(limit = SPOTIFY_PAGE_SIZE, offset = 0).getOrThrow()
                    apiTotal = result.total
                    val fetched = result.items.map { it.track }.filter { !it.isLocal }
                    allTracks.addAll(fetched)
                    apiFetchOffset = result.items.size
                    apiHasMore = apiFetchOffset < apiTotal
                } else {
                    fetchNextApiPage()
                }
            }
            if (allTracks.isEmpty()) return@withContext null
            val targetIndex = startIndex.coerceIn(0, allTracks.size - 1)
            val resolvedItems = mutableListOf<MediaItem>()
            var mediaItemIndex = 0
            for (i in allTracks.indices) {
                if (i == targetIndex) mediaItemIndex = resolvedItems.size
                mapper.resolveToMediaItem(allTracks[i])?.let { resolvedItems.add(it) }
            }
            if (resolvedItems.isEmpty()) return@withContext null
            mediaItemIndex = mediaItemIndex.coerceIn(0, resolvedItems.size - 1)
            resolveOffset = allTracks.size
            apiHasMore = false
            Timber.d("SpotifyLikedSongsQueue: getFullStatus resolved ${resolvedItems.size} tracks (startIndex=$targetIndex)")
            Queue.Status(title = null, items = resolvedItems, mediaItemIndex = mediaItemIndex)
        } catch (e: Exception) {
            Timber.e(e, "SpotifyLikedSongsQueue: getFullStatus failed")
            null
        }
    }

    // Optional API used by MuSicX Spotify screens (not part of Queue interface).
    suspend fun shuffleRemainingTracks() = withContext(Dispatchers.IO) {
        while (apiHasMore) {
            fetchNextApiPage()
        }
        if (resolveOffset < allTracks.size) {
            val remaining = allTracks.subList(resolveOffset, allTracks.size)
            val shuffled = remaining.shuffled()
            for (i in shuffled.indices) {
                remaining[i] = shuffled[i]
            }
            Timber.d("SpotifyLikedSongsQueue: Shuffled ${remaining.size} remaining tracks " +
                "(resolveOffset=$resolveOffset, total=${allTracks.size})")
        }
    }

    override fun hasNextPage(): Boolean =
        resolveOffset < allTracks.size || apiHasMore

    override suspend fun nextPage(): List<MediaItem> = withContext(Dispatchers.IO) {
        // Serialize concurrent callers (see [pageMutex] docstring). This is
        // essential now that MusicService.playQueue eagerly calls nextPage()
        // in a background growth loop while `onMediaItemTransition` may also
        // fire it — without the lock we'd race on resolveOffset and either
        // duplicate items or drop them.
        pageMutex.withLock {
            // If we've resolved all fetched tracks but the API has more, fetch another page
            if (resolveOffset >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }

            if (resolveOffset >= allTracks.size) {
                return@withLock emptyList()
            }

            // Resolve the next batch
            val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
            val batch = allTracks.subList(resolveOffset, end).toList()
            resolveOffset = end

            Timber.d("SpotifyLikedSongsQueue: Resolving batch of ${batch.size} tracks " +
                "(offset=$resolveOffset/${allTracks.size}, apiTotal=$apiTotal)")

            coroutineScope {
                batch.map { track -> async { mapper.resolveToMediaItem(track) } }
                    .awaitAll()
                    .filterNotNull()
            }
        }
    }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        try {
            val result = Spotify.likedSongs(
                limit = SPOTIFY_PAGE_SIZE,
                offset = apiFetchOffset,
            ).getOrThrow()
            val fetched = result.items.map { it.track }.filter { !it.isLocal }
            allTracks.addAll(fetched)
            apiFetchOffset += result.items.size
            apiHasMore = apiFetchOffset < apiTotal
            Timber.d("SpotifyLikedSongsQueue: Fetched API page, now have ${allTracks.size} tracks")
        } catch (e: Exception) {
            Timber.e(e, "SpotifyLikedSongsQueue: Failed to fetch next API page")
            apiHasMore = false
        }
    }
}
