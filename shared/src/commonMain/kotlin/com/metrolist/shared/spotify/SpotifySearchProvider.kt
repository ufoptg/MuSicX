package com.metrolist.shared.spotify

import com.metrolist.shared.model.YTItem

/**
 * Platform seam for Spotify search.
 *
 * The Spotify client lives in JVM shared code (it uses Ktor HTTP clients and
 * the spotlib GQL client), but search must be reachable from commonMain so
 * SearchViewModel can merge Spotify results with YouTube Music results.
 *
 * Callers must guard against null when no Spotify session is available.
 */
interface SpotifySearchProvider {
    /**
     * Search Spotify for tracks, albums, artists, and playlists.
     *
     * @param term Search term
     * @param limit Maximum number of results to return
     * @return Spotify search results mapped to shared YTItem types, or null if not logged in
     */
    suspend fun search(
        term: String,
        limit: Int = 20,
    ): SpotifySearchResult?

    /** Whether a Spotify session is currently available. */
    fun hasSession(): Boolean
}

/** No-op provider used when Spotify is not configured. */
class NullSpotifySearchProvider : SpotifySearchProvider {
    override suspend fun search(
        term: String,
        limit: Int,
    ): SpotifySearchResult? = null

    override fun hasSession(): Boolean = false
}
