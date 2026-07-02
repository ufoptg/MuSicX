/**
 * MuSicX Spotify Home ViewModel
 *
 * Standalone ViewModel that loads Spotify home-feed sections for injection into
 * MuSicX's HomeScreen. Kept separate from the shared HomeViewModel so we don't
 * conflict with future upstream refactors.
 *
 * Ported from ufoptg/meld (HomeViewModel.loadSpotifyHomeSections) into a
 * dedicated Hilt ViewModel.
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.SectionType
import com.metrolist.music.models.SpotifyHomeSection
import com.metrolist.music.playback.SpotifyProfileCache
import com.metrolist.music.utils.reportException
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyAlbum
import com.metrolist.spotify.models.SpotifyArtist
import com.metrolist.spotify.models.SpotifyHomeFeedItem
import com.metrolist.spotify.models.SpotifyHomeFeedSection
import com.metrolist.spotify.models.SpotifyImage
import com.metrolist.spotify.models.SpotifyPlaylist
import com.metrolist.spotify.models.SpotifyPlaylistOwner
import com.metrolist.spotify.models.SpotifyPlaylistTracksRef
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SpotifyHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _sections = MutableStateFlow<List<SpotifyHomeSection>>(emptyList())
    val sections = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        refresh(hideExplicit = false)
    }

    fun refresh(hideExplicit: Boolean) {
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                loadSections(hideExplicit)
            } catch (e: Exception) {
                Timber.e(e, "SpotifyHomeViewModel.refresh failed")
                reportException(e)
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    private suspend fun loadSections(hideExplicit: Boolean) {
        val out = mutableListOf<SpotifyHomeSection>()

        // Section 0: recently played (from Spotify /me/player/recently-played)
        try {
            Spotify.recentlyPlayed(limit = 20).onSuccess { recent ->
                val filtered = if (hideExplicit) recent.filter { !it.explicit } else recent
                if (filtered.isNotEmpty()) {
                    out.add(
                        SpotifyHomeSection(
                            title = "spotify_recently_played",
                            type = SectionType.TRACKS,
                            tracks = filtered,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyHomeViewModel: recently-played section failed")
        }

        // Section 1: your top tracks (from profile cache)
        try {
            val profileTracks = SpotifyProfileCache.getTopTracks(context, database, limit = 20)
            val topTracks = if (hideExplicit) profileTracks.filter { !it.explicit } else profileTracks
            if (topTracks.isNotEmpty()) {
                out.add(
                    SpotifyHomeSection(
                        title = "spotify_top_tracks",
                        type = SectionType.TRACKS,
                        tracks = topTracks,
                    ),
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyHomeViewModel: top-tracks section failed")
        }

        // Section 2: new releases
        try {
            Spotify.newReleases(limit = 20).onSuccess { newReleases ->
                val albums = newReleases.albums?.items.orEmpty()
                if (albums.isNotEmpty()) {
                    out.add(
                        SpotifyHomeSection(
                            title = "spotify_new_releases",
                            type = SectionType.ALBUMS,
                            albums = albums,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyHomeViewModel: new-releases section failed")
        }

        // Section 3+: Spotify's own personalized home feed
        try {
            Spotify.home(sectionItemsLimit = 10).onSuccess { feed ->
                feed.sections.forEach { raw -> convertHomeSection(raw)?.let(out::add) }
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyHomeViewModel: home-feed section failed")
        }

        _sections.value = out
    }

    private fun convertHomeSection(feedSection: SpotifyHomeFeedSection): SpotifyHomeSection? {
        val title = feedSection.title ?: return null
        val playlists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Playlist>()
        val albums = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Album>()
        val artists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Artist>()

        val counts = listOf(
            SectionType.PLAYLISTS to playlists.size,
            SectionType.ALBUMS to albums.size,
            SectionType.ARTISTS to artists.size,
        )
        val (dominant, size) = counts.maxByOrNull { it.second } ?: return null
        if (size == 0) return null

        return when (dominant) {
            SectionType.PLAYLISTS -> SpotifyHomeSection(
                title = title,
                type = SectionType.PLAYLISTS,
                playlists = playlists.map(::toSpotifyPlaylist),
            )
            SectionType.ALBUMS -> SpotifyHomeSection(
                title = title,
                type = SectionType.ALBUMS,
                albums = albums.map(::toSpotifyAlbum),
            )
            SectionType.ARTISTS -> SpotifyHomeSection(
                title = title,
                type = SectionType.ARTISTS,
                artists = artists.map(::toSpotifyArtist),
            )
            SectionType.TRACKS -> null
        }
    }

    private fun toSpotifyPlaylist(p: SpotifyHomeFeedItem.Playlist): SpotifyPlaylist =
        SpotifyPlaylist(
            id = p.id,
            name = p.name,
            description = p.description,
            images = p.imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
            owner = p.ownerName?.let { SpotifyPlaylistOwner(displayName = it) },
            tracks = SpotifyPlaylistTracksRef(total = p.totalCount),
            uri = p.uri,
        )

    private fun toSpotifyAlbum(a: SpotifyHomeFeedItem.Album): SpotifyAlbum =
        SpotifyAlbum(
            id = a.id,
            name = a.name,
            albumType = a.albumType,
            artists = a.artists,
            images = a.imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
            uri = a.uri,
        )

    private fun toSpotifyArtist(ar: SpotifyHomeFeedItem.Artist): SpotifyArtist =
        SpotifyArtist(
            id = ar.id,
            name = ar.name,
            images = ar.imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
            uri = ar.uri,
        )
}
