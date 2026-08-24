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

        val recsPerSeed = coroutineScope {
            seeds.map { seed ->
                async {
                    relatedSongsFor(seed.id, hideVideoSongs)
                }
            }.awaitAll()
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

    private suspend fun relatedSongsFor(
        videoId: String,
        hideVideoSongs: Boolean,
    ): List<SongItem> {
        val relatedEndpoint = YouTube.next(WatchEndpoint(videoId = videoId))
            .getOrNull()
            ?.relatedEndpoint
            ?: return emptyList()

        val page = YouTube.related(relatedEndpoint).getOrNull() ?: return emptyList()
        return page.songs.filter { song ->
            if (song.id.isEmpty() || song.id == videoId) return@filter false
            if (hideVideoSongs && song.isVideoSong) return@filter false
            true
        }
    }
}
