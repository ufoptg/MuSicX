package com.metrolist.shared.spotify

import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.AlbumTiny
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.settings.Settings
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.SpotifyHomeFeedItem
import meow.nyx.spotlib.TrackItem

/**
 * JVM-backed [SpotifySearchProvider].
 *
 * The [clientFactory] is a suspend lambda that returns the authenticated
 * Spotify client (e.g. `androidSpotifyImportManager.playbackClient()`), or
 * null when no session is available. The factory is invoked per search so
 * the provider always uses the latest session.
 */
class SpotifySearchProviderImpl(
    private val clientFactory: suspend () -> SpotifyClient?,
) : SpotifySearchProvider {

    override suspend fun search(
        term: String,
        limit: Int,
    ): SpotifySearchResult? {
        val client = clientFactory() ?: return null
        return try {
            val response = client.search(term, limit)
            SpotifySearchResult(
                tracks = response.tracks.map(::mapTrack),
                albums = response.albums.map(::mapAlbum),
                artists = response.artists.map(::mapArtist),
                playlists = response.playlists.map(::mapPlaylist),
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun hasSession(): Boolean = Settings.shared.spotifySession.isNotBlank()

    private fun mapTrack(track: TrackItem): YTItem =
        SongItem(
            id = track.uri,
            title = track.name,
            thumbnail = null,
            artists = track.artists.map { ArtistTiny(id = null, name = it) },
            album = track.album?.let { AlbumTiny(id = track.albumUri, name = it) },
            duration = track.durationMs?.div(1000),
            isExplicit = track.explicit == true,
            isLocal = track.isLocal,
        )

    private fun mapAlbum(album: SpotifyHomeFeedItem.Album): YTItem =
        AlbumItem(
            id = album.uri,
            title = album.name,
            thumbnail = album.imageUrl,
            artists = album.artists.map { ArtistTiny(id = null, name = it) },
            type = album.albumType,
        )

    private fun mapArtist(artist: SpotifyHomeFeedItem.Artist): YTItem =
        ArtistItem(
            id = artist.uri,
            title = artist.name,
            thumbnail = artist.imageUrl,
        )

    private fun mapPlaylist(playlist: SpotifyHomeFeedItem.Playlist): YTItem =
        PlaylistItem(
            id = playlist.uri,
            title = playlist.name,
            thumbnail = playlist.imageUrl,
            author = playlist.ownerName,
            songCount = playlist.totalCount.takeIf { it > 0 }?.toString(),
            description = playlist.description,
            isEditable = false,
        )
}