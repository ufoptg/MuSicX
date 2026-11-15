package dev.musicx.shared.spotify

import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.TrackItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight profile cache for Spotify artists/tracks used by the recommendation engine.
 *
 * Unlike the legacy implementation this does not depend on Android Context or a
 * Room-backed MusicDatabase. It maintains an in-memory cache only.
 */
object SpotifyProfileCache {

    const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

    @Volatile
    private var cachedTracks: List<TrackItem> = emptyList()

    @Volatile
    private var cachedArtists: List<String> = emptyList()

    @Volatile
    private var lastRefreshMs: Long = 0L

    private val refreshMutex = Mutex()

    suspend fun getTopTracks(client: SpotifyClient, limit: Int = 50): List<TrackItem> {
        ensureLoaded(client)
        return cachedTracks.take(limit)
    }

    suspend fun getTopArtists(client: SpotifyClient, limit: Int = 50): List<String> {
        ensureLoaded(client)
        return cachedArtists.take(limit)
    }

    suspend fun forceRefresh(client: SpotifyClient): Boolean {
        lastRefreshMs = 0L
        return ensureLoaded(client)
    }

    fun invalidate() {
        lastRefreshMs = 0L
        cachedTracks = emptyList()
        cachedArtists = emptyList()
    }

    suspend fun ensureLoaded(client: SpotifyClient): Boolean {
        if (System.currentTimeMillis() - lastRefreshMs < CACHE_TTL_MS && cachedTracks.isNotEmpty()) {
            return true
        }
        return refreshMutex.withLock {
            if (System.currentTimeMillis() - lastRefreshMs < CACHE_TTL_MS && cachedTracks.isNotEmpty()) {
                return@withLock true
            }
            refreshFromSpotify(client)
        }
    }

    private suspend fun refreshFromSpotify(client: SpotifyClient): Boolean {
        return try {
            val liked = client.likedSongs(limit = 50)
            val tracks = liked.items.map { it.track }

            val artistFrequency = mutableMapOf<String, Int>()
            for (track in tracks) {
                for (artist in track.artists) {
                    artistFrequency[artist] = artistFrequency.getOrPut(artist) { 0 } + 1
                }
            }
            val artists = artistFrequency.entries
                .sortedByDescending { it.value }
                .map { it.key }

            cachedTracks = tracks
            cachedArtists = artists
            lastRefreshMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            // Fallback: keep stale cache rather than returning empty data
            if (cachedTracks.isNotEmpty()) {
                true
            } else {
                false
            }
        }
    }
}