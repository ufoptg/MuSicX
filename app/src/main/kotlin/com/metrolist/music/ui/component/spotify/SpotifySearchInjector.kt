/**
 * MuSicX Spotify Search Injector
 *
 * LazyListScope extension that injects a Spotify search-results section
 * alongside YouTube search results in OnlineSearchResult.
 */

package com.metrolist.music.ui.component.spotify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.EnableSpotifyKey
import com.metrolist.music.constants.SpotifySpDcKey
import com.metrolist.music.constants.UseSpotifySearchKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyAlbum
import com.metrolist.spotify.models.SpotifyPlaylist
import com.metrolist.spotify.models.SpotifySearchResult
import com.metrolist.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun LazyListScope.spotifySearchSection(
    query: String,
    navController: NavController,
) {
    item(key = "musicx_spotify_search_$query") {
        val enableSpotify by rememberPreference(EnableSpotifyKey, defaultValue = false)
        val useSpotifySearch by rememberPreference(UseSpotifySearchKey, defaultValue = true)
        val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
        if (!enableSpotify || !useSpotifySearch || spDc.isEmpty() || query.isBlank()) return@item

        SpotifySearchSectionContent(query, navController)
    }
}

@Composable
private fun SpotifySearchSectionContent(query: String, navController: NavController) {
    var result by remember(query) { mutableStateOf<SpotifySearchResult?>(null) }

    LaunchedEffect(query) {
        result = withContext(Dispatchers.IO) {
            runCatching {
                Spotify.search(
                    query = query,
                    types = listOf("track", "album", "playlist"),
                    limit = 8,
                ).getOrNull()
            }.getOrNull()
        }
    }

    val r = result ?: return
    val playlists = r.playlists?.items.orEmpty().take(4)
    val albums = r.albums?.items.orEmpty().take(4)
    val tracks = r.tracks?.items.orEmpty().take(6)
    if (playlists.isEmpty() && albums.isEmpty() && tracks.isEmpty()) return

    Text(
        text = stringResource(R.string.spotify_integration),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )

    playlists.forEach { p -> PlaylistRow(p) { navController.navigate("spotify/playlist/${p.id}") } }
    albums.forEach { a -> AlbumRow(a) { navController.navigate("spotify/album/${a.id}") } }
    tracks.forEach { t -> TrackRow(t) }
}

@Composable
private fun PlaylistRow(p: SpotifyPlaylist, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = p.images.firstOrNull()?.url, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Playlist · Spotify", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AlbumRow(a: SpotifyAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = a.images.firstOrNull()?.url, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Album · ${a.artists.joinToString { it.name }}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrackRow(t: SpotifyTrack) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = t.album?.images?.firstOrNull()?.url, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Song · ${t.artists.joinToString { it.name }}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
