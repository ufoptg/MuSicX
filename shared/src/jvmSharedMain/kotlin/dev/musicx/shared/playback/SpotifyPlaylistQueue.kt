package dev.musicx.shared.playback

import dev.musicx.shared.spotify.SpotifyYouTubeMapper
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.TrackItem

/**
 * Spotify-backed queue seeded from a Spotify playlist.
 *
 * Fetches all tracks from the playlist via [SpotifyClient.playlistTracks]
 * (up to [maxTracks]) and resolves them to YouTube Music items through
 * the fuzzy matcher.
 */
class SpotifyPlaylistQueue(
    private val client: SpotifyClient,
    mapper: SpotifyYouTubeMapper,
    private val playlistUri: String,
    private val maxTracks: Int = Int.MAX_VALUE,
) : SpotifyQueue(mapper) {

    override suspend fun loadTracks(): List<TrackItem> = client.playlistTracks(playlistUri, maxTracks)
}