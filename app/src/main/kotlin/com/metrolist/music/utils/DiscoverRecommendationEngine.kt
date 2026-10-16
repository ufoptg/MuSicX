/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withIndex
import timber.log.Timber
import java.time.LocalDateTime
import java.util.Collections

object DiscoverRecommendationEngine {
    private const val TAG = "DiscoverRecEngine"
    private const val RecentSeedCount = 5
    private const val MostPlayedSeedCount = 5
    private const val LikedSeedCount = 15
    private const val RawCandidateTargetMultiplier = 2
    /** Throttle guard between sequential related() calls when retrying after an all-empty pass. */
    private const val SEQUENTIAL_THROTTLE_MS = 350L

    suspend fun generate(
        context: Context,
        database: MusicDatabase,
        hideExplicit: Boolean,
        hideVideoSongs: Boolean,
        count: Int,
        excludedIds: Set<String> = emptySet(),
    ): List<SongItem> = withContext(Dispatchers.IO) {
        if (count <= 0) return@withContext emptyList()

        val historyIds: Set<String> =
            database.events().first().map { it.song.id }.toSet() + excludedIds

        suspend fun fetchCandidates(
            recentCount: Int,
            mostPlayedCount: Int,
            likedCount: Int,
            explicitEnabled: Boolean,
            videoSongsDisabled: Boolean,
            excluded: Set<String>,
        ): List<SongItem> {
            val events = database.events().first()
            val recentSeeds = events
                .distinctBy { it.song.id }
                .shuffled()
                .take(recentCount)
                .map { it.song }
            val mostPlayedSeeds = database.mostPlayedSongs(
                fromTimeStamp = LocalDateTime.now().minusYears(100),
                limit = 30,
                offset = 0,
                toTimeStamp = LocalDateTime.now(),
            ).first().shuffled().take(mostPlayedCount)
            val likedSeeds = database.likedSongsByCreateDateAsc()
                .first()
                .shuffled()
                .take(likedCount)

            val seeds = (recentSeeds + mostPlayedSeeds + likedSeeds)
                .distinctBy { it.id }
                .filter { it.id.isNotBlank() }

            val seedIds = seeds.map { it.id }.toSet()
            val forbidden = buildSet {
                addAll(historyIds)
                addAll(excluded)
                addAll(seedIds)
            }

            val targetCount = count * RawCandidateTargetMultiplier
            val candidates = Collections.synchronizedList(mutableListOf<SongItem>())
            val seenCandidateIds = Collections.synchronizedSet(mutableSetOf<String>())

            suspend fun relatedSongsFor(videoId: String): List<SongItem> {
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
                    val related = page?.songs.orEmpty()
                        .filterExplicit(explicitEnabled)
                        .filterVideoSongs(videoSongsDisabled)
                        .filter { it.id.isNotBlank() && it.id != videoId }
                    if (related.isNotEmpty()) return related
                }

                // Fallback 1: the watch-next up-next queue (already fetched above).
                val watchNext = nextResult.items
                    .filterExplicit(explicitEnabled)
                    .filterVideoSongs(videoSongsDisabled)
                    .filter { it.id.isNotBlank() && it.id != videoId }
                if (watchNext.isNotEmpty()) {
                    Timber.d("$TAG: seed=$videoId related empty → watch-next fallback yielded ${watchNext.size}")
                    return watchNext
                }

                // Fallback 2: an explicit RDAMVM radio mix.
                val radio = YouTube.next(
                    WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")
                ).getOrNull()?.items.orEmpty()
                    .filterExplicit(explicitEnabled)
                    .filterVideoSongs(videoSongsDisabled)
                    .filter { it.id.isNotBlank() && it.id != videoId }
                Timber.d("$TAG: seed=$videoId related+watch-next empty → radio fallback yielded ${radio.size}")
                return radio
            }

            // Parallel pass inside a supervisor scope so one failing seed never cancels the others.
            supervisorScope {
                seeds.withIndex().forEach { (_, seed) ->
                    launch(Dispatchers.IO) {
                        try {
                            val recs = relatedSongsFor(seed.id)
                            for (song in recs) {
                                synchronized(candidates) {
                                    if (candidates.size >= targetCount) return@synchronized
                                    if (song.id in forbidden) return@synchronized
                                    if (seenCandidateIds.add(song.id)) {
                                        candidates.add(song)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG: seed=${seed.id} failed")
                        }
                    }
                }
            }

            // Sequential retry (throttled) if the parallel pass produced nothing for every seed.
            // Parallel bursts are the most likely to trip 403/throttle without a PoToken.
            if (candidates.isEmpty()) {
                Timber.w("$TAG: all ${seeds.size} seeds empty on parallel pass, retrying sequentially")
                seeds.withIndex().forEach { (i, seed) ->
                    if (i > 0) delay(SEQUENTIAL_THROTTLE_MS)
                    try {
                        val recs = relatedSongsFor(seed.id)
                        for (song in recs) {
                            synchronized(candidates) {
                                if (candidates.size >= targetCount) return@synchronized
                                if (song.id in forbidden) return@synchronized
                                if (seenCandidateIds.add(song.id)) {
                                    candidates.add(song)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: sequential seed=${seed.id} failed")
                    }
                }
            }

            return candidates
        }

        fun appendUnique(target: MutableList<SongItem>, seen: MutableSet<String>, items: List<SongItem>) {
            for (item in items) {
                if (item.id.isBlank()) continue
                if (seen.add(item.id)) target.add(item)
            }
        }

        // First pass: normal filtering and full history/exclusion handling.
        val collected = mutableListOf<SongItem>()
        val collectedIds = mutableSetOf<String>()
        appendUnique(
            collected,
            collectedIds,
            fetchCandidates(
                recentCount = RecentSeedCount,
                mostPlayedCount = MostPlayedSeedCount,
                likedCount = LikedSeedCount,
                explicitEnabled = hideExplicit,
                videoSongsDisabled = hideVideoSongs,
                excluded = excludedIds,
            ),
        )

        if (collected.size >= count) {
            return@withContext collected.shuffled().take(count)
        }

        // Second pass: widened seeds, original explicit/video filtering; played history still excluded.
        appendUnique(
            collected,
            collectedIds,
            fetchCandidates(
                recentCount = 10,
                mostPlayedCount = 10,
                likedCount = 10,
                explicitEnabled = hideExplicit,
                videoSongsDisabled = hideVideoSongs,
                excluded = excludedIds,
            ),
        )

        if (collected.size >= count) {
            return@withContext collected.shuffled().take(count)
        }

        // Third pass: widened seeds, explicit/video filters disabled; played history still excluded.
        appendUnique(
            collected,
            collectedIds,
            fetchCandidates(
                recentCount = 10,
                mostPlayedCount = 10,
                likedCount = 10,
                explicitEnabled = false,
                videoSongsDisabled = false,
                excluded = excludedIds,
            ),
        )

        return@withContext collected.shuffled().take(count)
    }
}
