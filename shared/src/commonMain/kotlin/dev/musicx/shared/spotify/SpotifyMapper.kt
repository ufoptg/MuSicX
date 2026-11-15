package dev.musicx.shared.spotify

import kotlin.math.abs

/**
 * Utility object for creating search queries and matching scores
 * from Spotify track data to YouTube Music candidates.
 *
 * The fuzzy matching algorithm uses bigram similarity on normalized
 * title+artist strings plus a duration window. Scores range from 0.0
 * to 1.0.
 */
object SpotifyMapper {

    // Pre-compiled regex patterns for title normalization (avoids re-creation on each call)
    private val FEAT_PATTERN = Regex("\\(feat\\..*?\\)", RegexOption.IGNORE_CASE)
    private val FT_PATTERN = Regex("\\(ft\\..*?\\)", RegexOption.IGNORE_CASE)
    private val BRACKET_PATTERN = Regex("\\[.*?\\]")
    private val REMASTER_PATTERN = Regex("\\(.*?remaster.*?\\)", RegexOption.IGNORE_CASE)
    private val REMIX_PATTERN = Regex("\\(.*?remix.*?\\)", RegexOption.IGNORE_CASE)
    private val NON_ALNUM_PATTERN = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_PATTERN = Regex("\\s+")

    private val NORM_CACHE_MAX_SIZE = 256
    private const val EARLY_EXIT_THRESHOLD = 0.95

    /**
     * Normalizes a string for comparison: lowercases, strips feature/remaster/remix tags,
     * removes non-alphanumeric characters (except spaces), and collapses whitespace.
     */
    fun normalizeTitle(title: String): String {
        return title
            .lowercase()
            .replace(FEAT_PATTERN, "")
            .replace(FT_PATTERN, "")
            .replace(BRACKET_PATTERN, "")
            .replace(REMASTER_PATTERN, "")
            .replace(REMIX_PATTERN, "")
            .replace(NON_ALNUM_PATTERN, "")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()
    }

    /**
     * Pre-computes normalized title/artist and their bigrams for a Spotify track.
     * Call once before scoring against many candidates to avoid redundant work.
     */
    data class PrecomputedTrack(
        val normalizedTitle: String,
        val titleBigrams: Set<String>,
        val normalizedArtist: String,
        val artistBigrams: Set<String>,
        val durationMs: Int,
    )

    /** Bounded LRU cache of normalized strings. */
    private val normCache = object : LinkedHashMap<String, String>(
        NORM_CACHE_MAX_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > NORM_CACHE_MAX_SIZE
    }

    /** Bounded LRU cache of bigram sets. */
    private val bigramCache = object : LinkedHashMap<String, Set<String>>(
        NORM_CACHE_MAX_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>?>): Boolean =
            size > NORM_CACHE_MAX_SIZE
    }

    /** Pre-compute once per Spotify track. */
    fun precompute(
        title: String,
        artist: String,
        durationMs: Int,
    ): PrecomputedTrack {
        val normTitle = cachedNormalize(title)
        val normArtist = cachedNormalize(artist)
        return PrecomputedTrack(
            normalizedTitle = normTitle,
            titleBigrams = cachedBigrams(normTitle),
            normalizedArtist = normArtist,
            artistBigrams = cachedBigrams(normArtist),
            durationMs = durationMs,
        )
    }

    /** Scores a candidate against pre-computed Spotify track data. */
    fun matchScorePrecomputed(
        precomputed: PrecomputedTrack,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Int?,
    ): Double {
        val normCandidateTitle = cachedNormalize(candidateTitle)
        val normCandidateArtist = cachedNormalize(candidateArtist)

        val titleScore = bigramSimilarity(
            precomputed.normalizedTitle, precomputed.titleBigrams,
            normCandidateTitle, cachedBigrams(normCandidateTitle),
        )
        val artistScore = bigramSimilarity(
            precomputed.normalizedArtist, precomputed.artistBigrams,
            normCandidateArtist, cachedBigrams(normCandidateArtist),
        )

        val durationScore = durationScore(precomputed.durationMs, candidateDurationSec)
        return titleScore * 0.45 + artistScore * 0.35 + durationScore * 0.20
    }

    /** Returns the early-exit threshold above which remaining candidates can be skipped. */
    fun earlyExitThreshold(): Double = EARLY_EXIT_THRESHOLD

    /** Duration similarity: 1.0 if within 2s, decaying to 0.0 beyond 30s. */
    private fun durationScore(spotifyDurationMs: Int, candidateDurationSec: Int?): Double {
        if (candidateDurationSec == null || spotifyDurationMs <= 0) return 0.5
        val diff = kotlin.math.abs(spotifyDurationMs / 1000 - candidateDurationSec)
        return when {
            diff <= 2 -> 1.0
            diff <= 5 -> 0.8
            diff <= 10 -> 0.5
            diff <= 30 -> 0.2
            else -> 0.0
        }
    }

    /** Cached full normalization. */
    private fun cachedNormalize(title: String): String {
        normCache[title]?.let { return it }
        val normalized = normalizeTitle(title)
        normCache[title] = normalized
        return normalized
    }

    /** Cached bigrams for a normalized string. */
    private fun cachedBigrams(normalized: String): Set<String> {
        bigramCache[normalized]?.let { return it }
        val bigrams = if (normalized.length < 2) emptySet() else normalized.windowed(2).toSet()
        bigramCache[normalized] = bigrams
        return bigrams
    }

    /** Dice coefficient / bigram overlap similarity. */
    private fun bigramSimilarity(
        a: String, bigramsA: Set<String>,
        b: String, bigramsB: Set<String>,
    ): Double {
        if (a == b) return 1.0
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0
        val intersection = bigramsA.count { it in bigramsB }
        return (2.0 * intersection) / (bigramsA.size + bigramsB.size)
    }
}