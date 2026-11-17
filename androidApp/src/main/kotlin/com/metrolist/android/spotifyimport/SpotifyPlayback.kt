/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.spotifyimport

import com.metrolist.android.playback.PlayerConnection
import com.metrolist.shared.playback.MusicQueue
import com.metrolist.shared.spotifymatch.SpotifyYouTubeMapper

object SpotifyPlayback {
    /**
     * Resolve a Spotify playlist, album or liked-songs into YouTube Music tracks and
     * queue them for playback through [PlayerConnection].
     *
     * @param playlistUri  Full Spotify URI, e.g. "spotify:playlist:<id>" or "spotify:album:<id>".
     * @param title        Title shown for the queue.
     * @param playerConnection  Optional reference to the Android player connection.
     */
    suspend fun playPlaylist(
        playlistUri: String,
        title: String,
        playerConnection: PlayerConnection?,
    ) {
        val client = androidSpotifyImportManager.playbackClient()
        val tracks =
            when {
                playlistUri.startsWith("spotify:playlist:") -> client.playlistTracks(playlistUri, maxTracks = Int.MAX_VALUE)
                playlistUri.startsWith("spotify:album:") -> client.album(playlistUri)
                playlistUri.contains("liked-songs") -> client.likedSongs(maxTracks = Int.MAX_VALUE)
                else -> emptyList()
            }
        val songItems = tracks.mapNotNull { SpotifyYouTubeMapper.mapToYouTube(it) }
        val queue = MusicQueue()
        queue.setQueue(songItems, startIndex = 0)
        playerConnection?.playQueue(queue.items.value, startIndex = 0, queueTitle = title)
    }

    /**
     * Resolve a Spotify album into YouTube Music tracks and queue them for playback.
     *
     * @param albumUri  Full Spotify URI, e.g. "spotify:album:<id>".
     * @param title     Title shown for the queue.
     * @param playerConnection  Optional reference to the Android player connection.
     */
    suspend fun playAlbum(
        albumUri: String,
        title: String,
        playerConnection: PlayerConnection?,
    ) {
        val client = androidSpotifyImportManager.playbackClient()
        val tracks = client.album(albumUri)
        val songItems = tracks.mapNotNull { SpotifyYouTubeMapper.mapToYouTube(it) }
        val queue = MusicQueue()
        queue.setQueue(songItems, startIndex = 0)
        playerConnection?.playQueue(queue.items.value, startIndex = 0, queueTitle = title)
    }

    /**
     * Resolve a Spotify user's liked songs into YouTube Music tracks and queue them.
     *
     * @param playerConnection  Optional reference to the Android player connection.
     */
    suspend fun playLikedSongs(playerConnection: PlayerConnection?) {
        val client = androidSpotifyImportManager.playbackClient()
        val tracks = client.likedSongs(maxTracks = Int.MAX_VALUE)
        val songItems = tracks.mapNotNull { SpotifyYouTubeMapper.mapToYouTube(it) }
        val queue = MusicQueue()
        queue.setQueue(songItems, startIndex = 0)
        playerConnection?.playQueue(queue.items.value, startIndex = 0)
    }
}
