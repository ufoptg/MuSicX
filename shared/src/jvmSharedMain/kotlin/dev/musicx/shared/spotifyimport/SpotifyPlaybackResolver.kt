package dev.musicx.shared.spotifyimport

import dev.musicx.shared.model.SongItem
import dev.musicx.shared.playback.SpotifyLikedSongsQueue
import dev.musicx.shared.playback.SpotifyPlaylistQueue
import dev.musicx.shared.spotify.SpotifyYouTubeMapper
import meow.nyx.spotlib.PlaylistMeta
import meow.nyx.spotlib.PlaylistSource
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between Spotify import (playlists/liked songs) and playback.
 *
 * Given an authenticated [SpotifyImportManager] and a [SpotifyYouTubeMapper],
 * resolves Spotify track metadata into YouTube Music [SongItem]s suitable for
 * the player queue.
 */
class SpotifyPlaybackResolver(
    private val importManager: SpotifyImportManager,
    private val mapper: SpotifyYouTubeMapper,
) {

    /**
     * Resolves all tracks from a Spotify playlist into playable SongItems.
     *
     * @param playlist the playlist metadata from [SpotifyImportManager.loadPlaylists()]
     * @param maxTracks optional cap on number of tracks to resolve
     * @return list of YouTube Music SongItems, in playlist order
     */
    suspend fun resolvePlaylist(
        playlist: PlaylistMeta,
        maxTracks: Int = Int.MAX_VALUE,
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val queue = SpotifyPlaylistQueue(
            client = importManager.authenticatedClient(),
            mapper = mapper,
            playlistUri = playlist.uri,
            maxTracks = maxTracks,
        )
        queue.resolveSongItems()
    }

    /**
     * Resolves the user's liked songs into playable SongItems.
     *
     * @param maxTracks optional cap on number of tracks to resolve
     * @return list of YouTube Music SongItems, in liked-songs order
     */
    suspend fun resolveLikedSongs(
        maxTracks: Int = Int.MAX_VALUE,
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val queue = SpotifyLikedSongsQueue(
            client = importManager.authenticatedClient(),
            mapper = mapper,
            maxTracks = maxTracks,
        )
        queue.resolveSongItems()
    }

    /**
     * Resolves the first page of a playlist for quick preview/play.
     */
    suspend fun resolvePlaylistPreview(
        playlist: PlaylistMeta,
        limit: Int = 25,
    ): List<SongItem> = resolvePlaylist(playlist, maxTracks = limit)

    /**
     * Resolves a single track by its Spotify URI for immediate playback.
     *
     * Note: the track must exist in the user's playlists or liked songs
     * to be discoverable via this resolver.
     */
    suspend fun resolveTrackByUri(trackUri: String): SongItem? = withContext(Dispatchers.IO) {
        val client = importManager.authenticatedClient()
        // Search across liked songs + first playlist for the URI
        // (simplified; a full implementation might cache a URI→SongItem map)
        val track = client.likedSongs(limit = 1000).find { it.uri == trackUri }
            ?: client.listPlaylists().firstOrNull()?.let { pl ->
                client.playlistTracks(pl.uri, limit = 5000).find { it.uri == trackUri }
            }
        track?.let { mapper.resolveToYouTubeItem(it) }
    }
}

/**
 * Factory for creating a SpotifyPlaybackResolver.
 *
 * Wires [SpotifyImportManager] (provides authenticated client) with
 * [SpotifyYouTubeMapper] (provides YouTube search + fuzzy matching).
 */
object SpotifyPlaybackResolverFactory {

    fun create(
        importManager: SpotifyImportManager,
        mapper: SpotifyYouTubeMapper,
    ): SpotifyPlaybackResolver =
        SpotifyPlaybackResolver(importManager, mapper)
}