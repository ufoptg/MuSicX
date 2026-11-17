package com.metrolist.shared.spotify

import com.metrolist.shared.model.YTItem
import kotlinx.serialization.Serializable

/**
 * Result of a Spotify search query, containing tracks, albums, artists, and playlists
 * mapped to shared YTItem types for rendering in the search UI.
 */
@Serializable
data class SpotifySearchResult(
    val tracks: List<YTItem> = emptyList(),
    val albums: List<YTItem> = emptyList(),
    val artists: List<YTItem> = emptyList(),
    val playlists: List<YTItem> = emptyList(),
)