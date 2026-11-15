package dev.musicx.shared.spotify

import dev.musicx.shared.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.TrackItem
import kotlin.math.abs

/**
 * Lightweight recommendation engine that builds personalized Spotify→YouTube queues.
 *
 * Unlike the legacy implementation, this does not depend on Spotify's deprecated
 * recommendations or related-artists endpoints. It builds a user taste profile from
 * the user's liked songs (via [SpotifyProfileCache]) and, for a seed track, scores
 * candidate tracks from that pool by title/artist similarity, artist affinity, and
 * duration closeness. Candidates are diversified so no single artist dominates.
 */
object SpotifyRecommendationEngine {

    private const val PROFILE_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
    private const val MAX_TRACKS_PER_ARTIST = 3

    // Composite scoring weights (sum to 1.0)
    private const val W_SIMILARITY = 0.40f
    private const val W_AFFINITY = 0.30f
    private const val W_DURATION = 0.15f
    private const val W_RECENCY = 0.15f

    @Volatile
    private var artistAffinityMap: Map<String, Float> = emptyMap()

    @Volatile
    private var topTrackPool: List<TrackItem> = emptyList()

    @Volatile
    private var lastProfileRefresh: Long = 0L

    private val refreshMutex = Mutex()

    /**
     * Builds or refreshes the user taste profile from Spotify liked songs.
     *
     * Cached for [PROFILE_TTL_MS]; returns true when a non-empty profile is available.
     */
    suspend fun ensureProfileLoaded(client: SpotifyClient): Boolean =
        withContext(Dispatchers.IO) {
            if (System.currentTimeMillis() - lastProfileRefresh < PROFILE_TTL_MS &&
                topTrackPool.isNotEmpty()
            ) {
                return@withContext true
            }
            refreshMutex.withLock {
                if (System.currentTimeMillis() - lastProfileRefresh < PROFILE_TTL_MS &&
                    topTrackPool.isNotEmpty()
                ) {
                    return@withLock true
                }
                buildProfile(client)
            }
        }

    /**
     * Generate a personalized list of recommended tracks based on a seed track.
     *
     * Returns up to [limit] tracks from the user's liked-songs pool, excluding the
     * seed itself, ranked by composite score and diversified per artist.
     */
    suspend fun getRecommendations(
        seedTrack: TrackItem,
        limit: Int = 50,
        client: SpotifyClient,
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        if (!ensureProfileLoaded(client)) {
            Logger.w("SpotifyRecEngine", "Profile not available; returning empty recommendations")
            return@withContext emptyList()
        }

        val precomputed = SpotifyMapper.precompute(
            title = seedTrack.name,
            artist = seedTrack.artists.firstOrNull().orEmpty(),
            durationMs = (seedTrack.durationMs ?: 0).toInt(),
        )
        val seedDurationSec = (seedTrack.durationMs ?: 0L) / 1000

        val candidates = topTrackPool
            .filter { it.uri != seedTrack.uri }
            .mapNotNull { track ->
                val score = scoreCandidate(track, precomputed, seedDurationSec)
                if (score > 0.0f) ScoredCandidate(track, score) else null
            }

        Logger.d(
            "SpotifyRecEngine",
            "Generated ${candidates.size} candidates for '${seedTrack.name}'"
        )

        val diversified = diversify(candidates.sortedByDescending { it.score }, limit)
        diversified.map { it.track }
    }

    /**
     * Invalidates the cached user profile, forcing a rebuild on next use.
     */
    fun invalidateProfile() {
        lastProfileRefresh = 0L
        artistAffinityMap = emptyMap()
        topTrackPool = emptyList()
        Logger.d("SpotifyRecEngine", "Profile cache invalidated")
    }

    private suspend fun buildProfile(client: SpotifyClient): Boolean {
        return try {
            val liked = SpotifyProfileCache.getTopTracks(client, limit = 100)

            val affinityBuilder = mutableMapOf<String, Int>()
            for (track in liked) {
                for (artist in track.artists) {
                    affinityBuilder[artist] = (affinityBuilder[artist] ?: 0) + 1
                }
            }

            val maxCount = affinityBuilder.values.maxOrNull() ?: 1
            val normalized = affinityBuilder.mapValues { it.value.toFloat() / maxCount.toFloat() }

            artistAffinityMap = normalized
            topTrackPool = liked.distinctBy { it.uri }
            lastProfileRefresh = System.currentTimeMillis()

            Logger.d(
                "SpotifyRecEngine",
                "Profile built — ${liked.size} tracks, ${normalized.size} artists"
            )
            true
        } catch (e: Exception) {
            Logger.e("SpotifyRecEngine", "Failed to build user profile", e)
            if (topTrackPool.isNotEmpty()) true else false
        }
    }

    private fun scoreCandidate(
        track: TrackItem,
        seedPrecomputed: SpotifyMapper.PrecomputedTrack,
        seedDurationSec: Long,
    ): Float {
        val titleArtistScore = SpotifyMapper.matchScorePrecomputed(
            precomputed = seedPrecomputed,
            candidateTitle = track.name,
            candidateArtist = track.artists.firstOrNull().orEmpty(),
            candidateDurationSec = ((track.durationMs ?: 0L) / 1000).toInt(),
        ).toFloat()

        // Artist affinity: best affinity among the track's artists
        val affinity = track.artists
            .maxOfOrNull { artistAffinityMap[it] ?: 0f } ?: 0f

        // Duration similarity
        val trackDurationSec = (track.durationMs ?: 0L) / 1000
        val durationScore = when {
            trackDurationSec == 0L || seedDurationSec == 0L -> 0.5f
            else -> {
                val diff = abs(seedDurationSec - trackDurationSec)
                when {
                    diff <= 2 -> 1.0f
                    diff <= 5 -> 0.8f
                    diff <= 10 -> 0.5f
                    diff <= 30 -> 0.2f
                    else -> 0.0f
                }
            }
        }

        // Recency boost: track is itself in the user's top pool (it is, by construction)
        val recency = 1.0f

        return (W_SIMILARITY * titleArtistScore) +
            (W_AFFINITY * affinity) +
            (W_DURATION * durationScore) +
            (W_RECENCY * recency)
    }

    private fun diversify(ranked: List<ScoredCandidate>, limit: Int): List<ScoredCandidate> {
        val result = mutableListOf<ScoredCandidate>()
        val artistCount = mutableMapOf<String, Int>()

        for (candidate in ranked) {
            if (result.size >= limit) break
            val mainArtist = candidate.track.artists.firstOrNull().orEmpty()
            if ((artistCount[mainArtist] ?: 0) >= MAX_TRACKS_PER_ARTIST) continue
            result.add(candidate)
            artistCount[mainArtist] = (artistCount[mainArtist] ?: 0) + 1
        }
        return result
    }

    private data class ScoredCandidate(
        val track: TrackItem,
        val score: Float,
    )
}
