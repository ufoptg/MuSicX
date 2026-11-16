package com.metrolist.shared.spotifymatch

import com.metrolist.shared.model.SearchSummaryPage
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.state.GlobalYouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import meow.nyx.spotlib.SpotifyPriorityGate
import meow.nyx.spotlib.TrackItem
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Spotify-to-YouTube Music fuzzy mapping layer.
 *
 * Ported from v13 [SpotifyMapper] and [SpotifyYouTubeMapper].
 * Uses bigram Dice coefficient for title/artist similarity, duration
 * weighting, and early-exit thresholds. Caches normalization and
 * bigram results in bounded LRU maps.
 *
 * v15 note: no Room DB persistence (no schema change). Match results are
 * cached in-memory only. Manual overrides live in memory as well.
 */
object SpotifyYouTubeMapper {

    // ---- Constants ----

    private const val MIN_MATCH_THRESHOLD = 0.35
    private const val EARLY_EXIT_THRESHOLD = 0.95
    private const val MEM_CACHE_MAX_SIZE = 512
    private const val NORM_CACHE_MAX_SIZE = 256
    private const val BIGRAM_CACHE_MAX_SIZE = 256
    private const val PRECOMPUTED_CACHE_MAX_SIZE = 256

    // Pre-compiled regex patterns for title normalization (avoids re-creation on each call)
    private val FEAT_PATTERN = Regex("\\(feat\\..*?\\)")
    private val FT_PATTERN = Regex("\\(ft\\..*?\\)")
    private val BRACKET_PATTERN = Regex("\\[.*?]")
    private val REMASTER_PATTERN = Regex("\\(.*?remaster.*?\\)", RegexOption.IGNORE_CASE)
    private val REMIX_PATTERN = Regex("\\(.*?remix.*?\\)", RegexOption.IGNORE_CASE)
    private val NON_ALNUM_PATTERN = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_PATTERN = Regex("\\s+")

    // ---- LRU Caches ----

    /** LRU cache for normalized strings. Avoids re-running 7 regex replacements per call. */
    private val normalizationCache = object : LinkedHashMap<String, String>(NORM_CACHE_MAX_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>): Boolean = size > NORM_CACHE_MAX_SIZE
    }

    /** LRU cache for bigram sets. */
    private val bigramCache = object : LinkedHashMap<String, Set<String>>(BIGRAM_CACHE_MAX_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Set<String>>): Boolean = size > BIGRAM_CACHE_MAX_SIZE
    }

    /** LRU cache for precomputed track metadata. */
    private val precomputedCache = object : LinkedHashMap<String, PrecomputedTrack>(PRECOMPUTED_CACHE_MAX_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, PrecomputedTrack>): Boolean = size > PRECOMPUTED_CACHE_MAX_SIZE
    }

    /** In-memory recent matches cache (spotifyUri -> SongItem). */
    private val matchCache = object : LinkedHashMap<String, SongItem>(MEM_CACHE_MAX_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, SongItem>): Boolean = size > MEM_CACHE_MAX_SIZE
    }

    /** In-memory manual overrides (youtubeId -> spotifyUri). No Room persistence in v15. */
    private val manualOverrides = ConcurrentHashMap<String, String>()

    // ---- Precomputed Track Model ----

    data class PrecomputedTrack(
        val uri: String,
        val titleNorm: String,
        val artistNorm: String,
        val durationMs: Long,
        val titleBigrams: Set<String>,
        val artistBigrams: Set<String>,
    )

    // ---- Public API ----

    /**
     * Map a Spotify [TrackItem] to a YouTube Music [SongItem] via fuzzy search.
     * Returns null if no match meets [MIN_MATCH_THRESHOLD].
     *
     * Resolution order:
     * 1. Manual override (youtubeId -> spotifyUri) then song metadata lookup
     * 2. Recent match cache (spotifyUri -> SongItem)
     * 3. YouTube search + fuzzy match (title 45% / artist 35% / duration 20%)
     */
    suspend fun mapToYouTube(track: TrackItem): SongItem? = withContext(Dispatchers.IO) {
        val spotifyUri = track.uri

        // 1. Check manual override first
        manualOverrides.entries.firstOrNull { it.value == spotifyUri }?.let { (youtubeId, _) ->
            GlobalYouTubeRepository.instance.getSongMetadata(youtubeId)?.let { song ->
                matchCache[spotifyUri] = song
                return@withContext song
            }
        }

        // 2. Check recent match cache
        matchCache[spotifyUri]?.let { return@withContext it }

        // 3. Build search query and fetch YouTube candidates
        val query = buildSearchQuery(track)
        val searchPage = GlobalYouTubeRepository.instance.getSearchSummary(query, recordSearchHistory = false)
        if (searchPage.summaries.isEmpty()) return@withContext null

        // 4. Precompute track metadata
        val precomputed = precompute(track)

        // 5. Find best match across all song-type summaries
        findBestMatch(precomputed, searchPage)?.let { song ->
            matchCache[spotifyUri] = song
            SpotifyMetadataRegistry.register(song.id, track)
            return@withContext song
        }

        null
    }

    /**
     * Resolve a YouTube video ID to its Spotify URI (for scrobbling/queueing).
     * Uses the playback priority gate so lookups don't stall behind background jobs.
     *
     * Fast path checks the registry and manual overrides. The slow path
     * (Spotify search) is deferred until SpotifyClient.search is ported
     * (P3.3+); callers fall back to YouTube-only queueing when null.
     */
    suspend fun resolveToSpotifyUri(
        youtubeId: String,
        title: String,
        artist: String,
        durationSec: Long = -1,
    ): String? = SpotifyPriorityGate.withPlaybackPriority {
        // 1. Check registry (registered during mapToYouTube)
        val cachedTrack = SpotifyMetadataRegistry.get(youtubeId)
        if (cachedTrack != null) return@withPlaybackPriority cachedTrack.uri

        // 2. Check manual override
        manualOverrides[youtubeId]?.let { return@withPlaybackPriority it }

        // 3. Slow path deferred — no Spotify search endpoint ported to v14 yet
        null
    }

    /**
     * Manually override a Spotify track's YouTube match.
     * In-memory only — no Room persistence in v15 (no schema change).
     */
    suspend fun overrideMatch(spotifyUri: String, youtubeId: String, title: String, artist: String) {
        manualOverrides[youtubeId] = spotifyUri
        SpotifyMetadataRegistry.register(youtubeId, TrackItem(
            uri = spotifyUri,
            name = title,
            artists = listOf(artist),
        ))
    }

    /**
     * Invalidate a manual override.
     */
    suspend fun invalidateOverride(youtubeId: String) {
        manualOverrides.remove(youtubeId)
    }

    /**
     * Clear all manual overrides.
     */
    suspend fun clearOverrides() {
        manualOverrides.clear()
    }

    // ---- Internal: Search Query Building ----

    private fun buildSearchQuery(track: TrackItem): String {
        val title = track.name
        val artists = track.artists.joinToString(", ")
        return "$title $artists".trim()
    }

    // ---- Internal: Precomputation ----

    private fun precompute(track: TrackItem): PrecomputedTrack {
        val key = track.uri
        precomputedCache[key]?.let { return it }

        val titleNorm = normalizeTitle(track.name)
        val artistNorm = normalizeTitle(track.artists.joinToString(" "))

        val titleBigrams = getBigrams(titleNorm)
        val artistBigrams = getBigrams(artistNorm)

        val precomputed = PrecomputedTrack(
            uri = track.uri,
            titleNorm = titleNorm,
            artistNorm = artistNorm,
            durationMs = track.durationMs ?: 0L,
            titleBigrams = titleBigrams,
            artistBigrams = artistBigrams,
        )

        precomputedCache[key] = precomputed
        return precomputed
    }

    // ---- Internal: Normalization ----

    private fun normalizeTitle(raw: String): String {
        normalizationCache[raw]?.let { return it }

        var normalized = raw.lowercase()
            .replace(FEAT_PATTERN, "")
            .replace(FT_PATTERN, "")
            .replace(BRACKET_PATTERN, "")
            .replace(REMASTER_PATTERN, "")
            .replace(REMIX_PATTERN, "")
            .replace(NON_ALNUM_PATTERN, " ")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()

        normalizationCache[raw] = normalized
        return normalized
    }

    // ---- Internal: Bigrams ----

    private fun getBigrams(text: String): Set<String> {
        bigramCache[text]?.let { return it }
        val bigrams = mutableSetOf<String>()
        for (i in 0 until text.length - 1) {
            bigrams.add(text.substring(i, i + 2))
        }
        val result = bigrams.toSet()
        bigramCache[text] = result
        return result
    }

    // ---- Internal: Scoring ----

    private fun matchScorePrecomputed(pre: PrecomputedTrack, candidate: SongItem): Double {
        val candidateTitleNorm = normalizeTitle(candidate.title)
        val candidateArtistNorm = normalizeTitle(candidate.artists.map { it.name }.joinToString(" "))

        val titleScore = bigramSimilarity(pre.titleBigrams, getBigrams(candidateTitleNorm))
        val artistScore = bigramSimilarity(pre.artistBigrams, getBigrams(candidateArtistNorm))

        val durationSec = pre.durationMs / 1000.0
        val candidateDurationSec = candidate.duration?.toDouble() ?: 0.0
        val durationScore = durationScore(durationSec, candidateDurationSec)

        // Weighted combination: title 45%, artist 35%, duration 20%
        val score = titleScore * 0.45 + artistScore * 0.35 + durationScore * 0.20
        return score.coerceAtMost(1.0)
    }

    private fun durationScore(expectedSec: Double, actualSec: Double): Double {
        if (expectedSec <= 0.0 || actualSec <= 0.0) return 0.5
        val diff = abs(expectedSec - actualSec)
        return 1.0 - (diff / expectedSec).coerceAtMost(1.0)
    }

    private fun bigramSimilarity(setA: Set<String>, setB: Set<String>): Double {
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val intersection = setA.intersect(setB).size
        val union = setA.size + setB.size - intersection
        return if (union == 0) 0.0 else (2.0 * intersection) / union.toDouble()
    }

    // ---- Internal: Best Match Search ----

    private fun findBestMatch(pre: PrecomputedTrack, page: SearchSummaryPage): SongItem? {
        var bestScore = 0.0
        var bestSong: SongItem? = null

        for (summary in page.summaries) {
            for (item in summary.items) {
                if (item !is SongItem) continue
                val score = matchScorePrecomputed(pre, item)
                if (score > bestScore) {
                    bestScore = score
                    bestSong = item
                    if (score >= EARLY_EXIT_THRESHOLD) return item
                }
            }
        }

        return if (bestScore >= MIN_MATCH_THRESHOLD) bestSong else null
    }
}