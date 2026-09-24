/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * YouTube Music playlist Enhance recommender.
 *
 * Mirrors [SpotifyRecommendationEngine.getRecommendationsForPlaylist]: pick evenly
 * spaced seeds across the playlist, fetch related songs per seed via
 * `YouTube.next` → `relatedEndpoint` → `YouTube.related`, then round-robin merge
 * and dedupe against tracks already in the playlist.
 */
object YouTubeRecommendationEngine {

    private const val TAG = "YouTubeRecEngine"

    /** Throttle guard between sequential related() calls when retrying after an all-empty pass. */
    private const val SEQUENTIAL_THROTTLE_MS = 350L

    /**
     * @param playlistSongs full (or currently loaded) playlist track list
     * @param limit target recommendation count after dedupe
     * @param seedCount how many diverse seeds to pull from the playlist
     * @param hideVideoSongs when true, drop video-type related songs
     */
    suspend fun getRecommendationsForPlaylist(
        playlistSongs: List<SongItem>,
        limit: Int = 20,
        seedCount: Int = 4,
        hideVideoSongs: Boolean = false,
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val valid = playlistSongs.filter { it.id.isNotEmpty() }
        if (valid.isEmpty()) return@withContext emptyList()

        val seeds = if (valid.size <= seedCount) {
            valid
        } else {
            val step = valid.size / seedCount
            (0 until seedCount).map { valid[it * step] }
        }

        val alreadyIn = valid.map { it.id }.toHashSet()

        var recsPerSeed = coroutineScope {
            seeds.map { seed ->
                async {
                    relatedSongsFor(seed.id, hideVideoSongs)
                }
            }.awaitAll()
        }

        // Sequential retry (throttled) if the parallel pass produced nothing for every seed —
        // parallel bursts are the most likely to trip 403/throttle without a PoToken.
        if (recsPerSeed.all { it.isEmpty() }) {
            Timber.w("$TAG: all ${seeds.size} seeds empty on parallel pass, retrying sequentially")
            recsPerSeed = seeds.mapIndexed { i, seed ->
                if (i > 0) delay(SEQUENTIAL_THROTTLE_MS)
                relatedSongsFor(seed.id, hideVideoSongs)
            }
        }

        val merged = mutableListOf<SongItem>()
        val seen = HashSet<String>(alreadyIn)
        val iterators = recsPerSeed.map { it.iterator() }.toMutableList()
        while (merged.size < limit && iterators.any { it.hasNext() }) {
            for (it in iterators) {
                if (!it.hasNext()) continue
                val next = it.next()
                if (next.id.isNotEmpty() && seen.add(next.id)) {
                    merged.add(next)
                    if (merged.size >= limit) break
                }
            }
        }

        Timber.d("$TAG: Enhance produced ${merged.size} recs from ${seeds.size} seeds")
        merged
    }

    /**
     * Related songs for a single seed. Tries `next → relatedEndpoint → related` first, then falls
     * back to the watch-next up-next queue, then to an RDAMVM radio mix — so a seed whose
     * `related()` shelf is empty still contributes recommendations instead of silently dropping out.
     * Emits per-seed diagnostics for logcat verification (blind CI builds).
     */
    private suspend fun relatedSongsFor(
        videoId: String,
        hideVideoSongs: Boolean,
    ): List<SongItem> {
        val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
        if (nextResult == null) {
            Timber.w("$TAG: seed=$videoId next() returned null")
            return emptyList()
        }

        val relatedEndpoint = nextResult.relatedEndpoint
        Timber.d(
            "$TAG: seed=$videoId relatedEndpoint=${relatedEndpoint != null} " +
                "watchNextItems=${nextResult.items.size}",
        )

        if (relatedEndpoint != null) {
            val page = YouTube.related(relatedEndpoint).getOrNull()
            Timber.d("$TAG: seed=$videoId related.songs=${page?.songs?.size ?: -1}")
            val related = page?.songs.orEmpty().filter { keep(it, videoId, hideVideoSongs) }
            if (related.isNotEmpty()) return related
        }

        // Fallback 1: the watch-next up-next queue (already fetched above).
        val watchNext = nextResult.items.filter { keep(it, videoId, hideVideoSongs) }
        if (watchNext.isNotEmpty()) {
            Timber.d("$TAG: seed=$videoId related empty → watch-next fallback yielded ${watchNext.size}")
            return watchNext
        }

        // Fallback 2: an explicit RDAMVM radio mix.
        val radio = YouTube.next(
            WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId"),
        ).getOrNull()?.items.orEmpty().filter { keep(it, videoId, hideVideoSongs) }
        Timber.d("$TAG: seed=$videoId related+watch-next empty → radio fallback yielded ${radio.size}")
        return radio
    }

    private fun keep(song: SongItem, seedId: String, hideVideoSongs: Boolean): Boolean {
        if (song.id.isEmpty() || song.id == seedId) return false
        if (hideVideoSongs && song.isVideoSong) return false
        return true
    }
}
