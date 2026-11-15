package dev.musicx.shared.playback

import dev.musicx.shared.spotify.SpotifyYouTubeMapper
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.TrackItem

/**
 * Spotify-backed queue seeded from the user's liked songs.
 *
 * Fetches all tracks marked as liked via [SpotifyClient.likedSongs]
 * (up to [maxTracks]) and resolves them to YouTube Music items through
 * the fuzzy matcher.
 */
class SpotifyLikedSongsQueue(
    private val client: SpotifyClient,
    mapper: SpotifyYouTubeMapper,
    private val maxTracks: Int = Int.MAX_VALUE,
) : SpotifyQueue(mapper) {

    override suspend fun loadTracks(): List<TrackItem> = client.likedSongs(maxTracks)

}