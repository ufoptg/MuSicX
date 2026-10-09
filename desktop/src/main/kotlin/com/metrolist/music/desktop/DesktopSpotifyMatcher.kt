package com.metrolist.music.desktop

import com.metrolist.spotify.SpotifyMapper
import com.metrolist.spotify.models.SpotifyTrack

internal const val MIN_MATCH = 0.35

internal fun bestMatch(track: SpotifyTrack, candidates: List<SearchHit>): SearchHit? {
    if (candidates.isEmpty()) return null
    val pre =
        SpotifyMapper.precompute(
            track.name,
            track.artists.firstOrNull()?.name.orEmpty(),
            track.durationMs,
        )
    val best =
        candidates
            .map { it to SpotifyMapper.matchScorePrecomputed(pre, it.title, it.subtitle.orEmpty(), null) }
            .maxByOrNull { it.second }
            ?: return null
    return best.first.takeIf { best.second >= MIN_MATCH }
}

/**
 * Best-effort matching of a Spotify track to a YouTube result. Scores candidates with the shared
 * [SpotifyMapper] (title/artist bigrams, neutral duration term since YouTube search carries none).
 */
object DesktopSpotifyMatcher {
    private val cache =
        object : LinkedHashMap<String, String>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 200
        }

    suspend fun resolveToYouTube(client: DesktopInnerTube, track: SpotifyTrack): SearchHit? {
        if (track.id.isNotEmpty()) {
            synchronized(cache) { cache[track.id] }?.let { id ->
                return SearchHit(id, track.name, track.artists.firstOrNull()?.name)
            }
        }
        val query = SpotifyMapper.buildSearchQuery(track)
        val candidates = runCatching { client.searchSongs(query).take(8) }.getOrDefault(emptyList())
        val match = bestMatch(track, candidates) ?: return null
        if (track.id.isNotEmpty()) synchronized(cache) { cache[track.id] = match.videoId }
        return match
    }
}
