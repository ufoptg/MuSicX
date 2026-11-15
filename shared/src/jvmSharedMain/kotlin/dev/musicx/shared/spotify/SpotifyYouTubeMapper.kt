package dev.musicx.shared.spotify

import dev.musicx.shared.model.AlbumTiny
import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import meow.nyx.spotlib.TrackItem

/**
 * Handles the mapping of Spotify tracks to YouTube Music equivalents.
 *
 * Uses fuzzy matching on title, artist, and duration to find the best result,
 * then caches successful matches in an in-memory LRU cache to avoid repeated
 * YouTube search API calls for the same track within a session.
 */
class SpotifyYouTubeMapper(
    private val searchSongs: suspend (String) -> List<SongItem>,
) {

    private data class CachedMatch(
        val youtubeId: String,
        val title: String,
        val artist: String,
        val isManualOverride: Boolean = false,
    )

    private val memoryCache = object : LinkedHashMap<String, CachedMatch>(
        MEM_CACHE_MAX_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMatch>?): Boolean =
            size > MEM_CACHE_MAX_SIZE
    }

    /** Maps a Spotify track to a YouTube Music SongItem by searching YouTube Music. */
    suspend fun mapToYouTube(track: TrackItem): SongItem? = withContext(Dispatchers.IO) {
        memoryCache[track.uri]?.let { mem ->
            Logger.d("SpotifyYouTubeMapper", "Spotify match memory hit: ${track.uri} -> ${mem.youtubeId}")
            return@withContext buildSongItem(
                youtubeId = mem.youtubeId,
                track = track,
                ytTitle = mem.title,
                ytArtist = mem.artist,
            )
        }

        val query = buildSearchQuery(track)
        Logger.d("SpotifyYouTubeMapper", "Searching YouTube for Spotify track: $query")

        val candidates = searchSongs(query)
        val bestMatch = findBestMatch(track, candidates)

        if (bestMatch != null) {
            memoryCache[track.uri] = CachedMatch(
                youtubeId = bestMatch.id,
                title = bestMatch.title,
                artist = bestMatch.artists.firstOrNull()?.name.orEmpty(),
                isManualOverride = false,
            )
            return@withContext buildSongItem(
                youtubeId = bestMatch.id,
                track = track,
                ytTitle = bestMatch.title,
                ytArtist = bestMatch.artists.firstOrNull()?.name.orEmpty(),
                ytThumbnailUrl = bestMatch.thumbnail,
            )
        }

        Logger.w("SpotifyYouTubeMapper", "No YouTube match found for Spotify track: ${track.name}")
        null
    }

    /** Resolves a Spotify track to a SongItem suitable for the player queue. */
    suspend fun resolveToYouTubeItem(track: TrackItem): SongItem? {
        val song = mapToYouTube(track)
        if (song == null) {
            Logger.w("SpotifyYouTubeMapper", "FAILED to resolve ${track.name} - no YouTube match")
            return null
        }
        Logger.d("SpotifyYouTubeMapper", "resolved '${track.name}' -> YouTube ID: ${song.id}")
        return song
    }

    private fun findBestMatch(
        spotifyTrack: TrackItem,
        candidates: List<SongItem>,
    ): MatchCandidate? {
        val spotifyArtist = spotifyTrack.artists.firstOrNull().orEmpty()
        val precomputed = SpotifyMapper.precompute(
            title = spotifyTrack.name,
            artist = spotifyArtist,
            durationMs = (spotifyTrack.durationMs ?: 0).toInt(),
        )

        var bestCandidate: MatchCandidate? = null
        val earlyExitThreshold = SpotifyMapper.earlyExitThreshold()

        for (song in candidates) {
            val score = SpotifyMapper.matchScorePrecomputed(
                precomputed = precomputed,
                candidateTitle = song.title,
                candidateArtist = song.artists.firstOrNull()?.name.orEmpty(),
                candidateDurationSec = (song.duration ?: 0).toInt(),
            )
            if (bestCandidate == null || score > bestCandidate.score) {
                bestCandidate = MatchCandidate(
                    id = song.id,
                    title = song.title,
                    artistName = song.artists.firstOrNull()?.name.orEmpty(),
                    artists = song.artists.map { ArtistTiny(it.id, it.name) },
                    duration = (song.duration ?: 0).toInt(),
                    thumbnailUrl = song.thumbnail,
                    albumId = song.album?.id,
                    albumTitle = song.album?.name,
                    explicit = song.isExplicit,
                    score = score,
                )
                if (score >= earlyExitThreshold) break
            }
        }

        return bestCandidate?.takeIf { it.score >= MIN_MATCH_THRESHOLD }
    }

    private fun buildSongItem(
        youtubeId: String,
        track: TrackItem,
        ytTitle: String,
        ytArtist: String,
        ytThumbnailUrl: String? = null,
    ): SongItem {
        val thumbnail = ytThumbnailUrl ?: "https://i.ytimg.com/vi/$youtubeId/maxresdefault.jpg"

        return SongItem(
            id = youtubeId,
            title = ytTitle.ifEmpty { track.name },
            thumbnail = thumbnail,
            artists = if (ytArtist.isNotEmpty()) {
                listOf(ArtistTiny(null, ytArtist))
            } else {
                track.artists.map { ArtistTiny(null, it) }
            },
            album = track.album?.let { AlbumTiny(track.albumUri, it) },
            duration = track.durationMs,
            isExplicit = track.explicit ?: false,
        )
    }

    data class MatchCandidate(
        val id: String,
        val title: String,
        val artistName: String,
        val artists: List<ArtistTiny>,
        val duration: Int,
        val thumbnailUrl: String?,
        val albumId: String?,
        val albumTitle: String?,
        val explicit: Boolean,
        val score: Double,
    )

    companion object {
        private const val MIN_MATCH_THRESHOLD = 0.35
        private const val MEM_CACHE_MAX_SIZE = 512

        fun buildSearchQuery(track: TrackItem): String {
            val artist = track.artists.firstOrNull().orEmpty()
            val title = track.name
            return if (artist.isEmpty()) title else "$artist $title"
        }
    }
}