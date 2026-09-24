/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    /**
     * Serializes [getInitialStatus] / [nextPage] so concurrent callers from
     * [com.metrolist.music.playback.MusicService.playQueue] background growth and
     * [com.metrolist.music.playback.MusicService.onMediaItemTransition] cannot race
     * on [continuation] / [endpoint].
     */
    private val pageMutex = Mutex()

    private class EmptyRadioQueueException : IllegalStateException()

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            pageMutex.withLock {
                var lastException: Throwable? = null

                if (endpoint.videoId != null && endpoint.playlistId == null) {
                    endpoint = WatchEndpoint(
                        videoId = endpoint.videoId,
                        playlistId = "RDAMVM${endpoint.videoId}"
                    )
                }

                val isRadioRequest =
                    endpoint.playlistId?.startsWith("RDAMVM") == true ||
                    (endpoint.videoId != null && endpoint.playlistId == null)

                for (attempt in 0..maxRetries) {
                    try {
                        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()

                        var items = nextResult.items
                        val relEndpoint = nextResult.relatedEndpoint

                        if (isRadioRequest && continuation == null) {
                            if (items.size <= 1 && endpoint.playlistId?.startsWith("RDAMVM") == true) {
                                throw EmptyRadioQueueException()
                            }
                            // Broaden the radio scope: interleave related songs *through* the mix
                            // (round-robin) instead of tacking them on the end, so variety shows up
                            // early rather than after 20+ same-artist tracks. Any error here falls
                            // back to the plain narrow mix so radio never regresses.
                            if (relEndpoint != null) {
                                try {
                                    val relatedPage = YouTube.related(relEndpoint).getOrNull()
                                    if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                                        val existingIds = items.map { it.id }.toHashSet()
                                        val relatedSongs = relatedPage.songs.filter {
                                            it.id != endpoint.videoId && existingIds.add(it.id)
                                        }
                                        if (relatedSongs.isNotEmpty()) {
                                            val currentIdx =
                                                (nextResult.currentIndex ?: 0)
                                                    .coerceIn(0, maxOf(0, items.size - 1))
                                            items = interleaveAfter(items, relatedSongs, currentIdx)
                                            Timber.d(
                                                "YouTubeQueue: radio scope widened — mix=${items.size} " +
                                                    "(+${relatedSongs.size} related interleaved after idx=$currentIdx)",
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "YouTubeQueue: radio scope expansion failed, using narrow mix")
                                }
                            }
                        }

                        endpoint = nextResult.endpoint
                        continuation = nextResult.continuation
                        retryCount = 0
                        return@withLock Queue.Status(
                            title = nextResult.title,
                            items = items.map { it.toMediaItem() },
                            mediaItemIndex = nextResult.currentIndex ?: 0,
                        )
                    } catch (e: Exception) {
                        lastException = e
                        if (
                            e is EmptyRadioQueueException &&
                            endpoint.playlistId?.startsWith("RDAMVM") == true &&
                            endpoint.videoId != null
                        ) {
                            endpoint = WatchEndpoint(videoId = endpoint.videoId)
                            // It will loop again and try with just videoId
                        }
                    }
                }
                throw lastException ?: Exception("Failed to get initial status")
            }
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            pageMutex.withLock {
                var lastException: Throwable? = null

                for (attempt in 0..maxRetries) {
                    try {
                        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                        endpoint = nextResult.endpoint
                        continuation = nextResult.continuation
                        retryCount = 0
                        return@withLock nextResult.items.map { it.toMediaItem() }
                    } catch (e: Exception) {
                        lastException = e
                        retryCount++
                        if (retryCount >= maxRetries) {
                            continuation = null // Stop trying to load more
                        }
                    }
                }
                throw lastException ?: Exception("Failed to get next page")
            }
        }
    }

    companion object {
        /**
         * Keeps the first [keepFirst]+1 primary items (through the currently-playing index) in place,
         * then round-robin interleaves [secondary] through the remaining [primary] tail so added
         * variety surfaces early without shifting the current-index track.
         */
        private fun interleaveAfter(
            primary: List<SongItem>,
            secondary: List<SongItem>,
            keepFirst: Int,
        ): List<SongItem> {
            if (secondary.isEmpty()) return primary
            val splitAt = (keepFirst + 1).coerceIn(0, primary.size)
            val head = primary.subList(0, splitAt)
            val tail = primary.subList(splitAt, primary.size)

            val merged = ArrayList<SongItem>(primary.size + secondary.size)
            merged.addAll(head)
            val ti = tail.iterator()
            val si = secondary.iterator()
            while (ti.hasNext() || si.hasNext()) {
                if (si.hasNext()) merged.add(si.next())
                if (ti.hasNext()) merged.add(ti.next())
            }
            return merged
        }

        /**
         * Creates a radio queue based on a song.
         * Explicitly requests the RDAMVM playlist to trigger automotive/radio mixing.
         */
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(
                    videoId = song.id,
                    playlistId = "RDAMVM${song.id}"
                ),
                song
            )
        }
    }
}
