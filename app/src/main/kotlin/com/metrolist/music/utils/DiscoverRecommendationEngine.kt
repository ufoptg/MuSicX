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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.Collections

object DiscoverRecommendationEngine {
    private const val RecentSeedCount = 5
    private const val MostPlayedSeedCount = 5
    private const val LikedSeedCount = 15
    private const val RawCandidateTargetMultiplier = 2

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

        fun appendUnique(target: MutableList<SongItem>, seen: MutableSet<String>, items: List<SongItem>) {
            for (item in items) {
                if (item.id.isBlank()) continue
                if (seen.add(item.id)) target.add(item)
            }
        }

        suspend fun fetchCandidates(
            recentCount: Int,
            mostPlayedCount: Int,
            likedCount: Int,
            explicitEnabled: Boolean,
            videoSongsDisabled: Boolean,
            relaxHistory: Boolean,
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
                if (!relaxHistory) addAll(historyIds)
                addAll(excluded)
                addAll(seedIds)
            }

            val targetCount = count * RawCandidateTargetMultiplier
            val candidates = Collections.synchronizedList(mutableListOf<SongItem>())
            val seenCandidateIds = Collections.synchronizedSet(mutableSetOf<String>())

            coroutineScope {
                seeds.map { seed ->
                    launch(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id))
                            .getOrNull()
                            ?.relatedEndpoint
                        val page = endpoint?.let { YouTube.related(it).getOrNull() }
                        val songs = page?.songs.orEmpty()
                            .filterExplicit(explicitEnabled)
                            .filterVideoSongs(videoSongsDisabled)

                        for (song in songs) {
                            synchronized(candidates) {
                                if (candidates.size >= targetCount) return@synchronized
                                if (song.id.isBlank()) return@synchronized
                                if (song.id in forbidden) return@synchronized
                                if (seenCandidateIds.add(song.id)) {
                                    candidates.add(song)
                                }
                            }
                        }
                    }
                }.forEach { it.join() }
            }

            return candidates
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
                relaxHistory = false,
                excluded = excludedIds,
            ),
        )

        if (collected.size >= count) {
            return@withContext collected.shuffled().take(count)
        }

        // Second pass: widened seeds, relaxed history, original explicit/video filtering.
        appendUnique(
            collected,
            collectedIds,
            fetchCandidates(
                recentCount = 10,
                mostPlayedCount = 10,
                likedCount = 10,
                explicitEnabled = hideExplicit,
                videoSongsDisabled = hideVideoSongs,
                relaxHistory = true,
                excluded = excludedIds,
            ),
        )

        if (collected.size >= count) {
            return@withContext collected.shuffled().take(count)
        }

        // Third pass: widened seeds, relaxed history, explicit/video filters disabled.
        appendUnique(
            collected,
            collectedIds,
            fetchCandidates(
                recentCount = 10,
                mostPlayedCount = 10,
                likedCount = 10,
                explicitEnabled = false,
                videoSongsDisabled = false,
                relaxHistory = true,
                excluded = excludedIds,
            ),
        )

        return@withContext collected.shuffled().take(count)
    }
}
