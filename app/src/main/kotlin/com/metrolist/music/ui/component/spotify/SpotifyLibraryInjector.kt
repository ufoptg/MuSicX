/**
 * MuSicX Spotify Library Playlists Injector
 *
 * Provides a LazyListScope extension that lists the user's Spotify playlists
 * inline in LibraryPlaylistsScreen. Guarded so it's a no-op when Spotify is
 * disabled or the user is not logged in.
 */

package com.metrolist.music.ui.component.spotify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.EnableSpotifyKey
import com.metrolist.music.constants.SpotifySpDcKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.SpotifyViewModel

/**
 * Injects a "Your Spotify Playlists" section at the top of a LazyColumn.
 * Call from within a LazyColumn scope inside LibraryPlaylistsScreen.
 */
fun LazyListScope.spotifyLibraryPlaylists(
    navController: NavController,
) {
    item(key = "musicx_spotify_library_gate") {
        val enableSpotify by rememberPreference(EnableSpotifyKey, defaultValue = false)
        val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
        if (!enableSpotify || spDc.isEmpty()) return@item

        SpotifyLibraryPlaylistsSection(navController)
    }
}

@Composable
private fun SpotifyLibraryPlaylistsSection(
    navController: NavController,
    viewModel: SpotifyViewModel = hiltViewModel(),
) {
    val playlists by viewModel.spotifyRootPlaylists.collectAsStateWithLifecycle()
    val folders by viewModel.spotifyRootFolders.collectAsStateWithLifecycle()

    if (playlists.isEmpty() && folders.isEmpty()) return

    Text(
        text = stringResource(R.string.spotify_playlists),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )

    folders.take(20).forEach { folder ->
        // Spotify folder URIs look like "spotify:folder:<id>"; extract the tail.
        val folderId = folder.uri.substringAfterLast(":")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("spotify/folder/$folderId") }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .padding(4.dp),
            ) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.folder),
                    contentDescription = null,
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Text(text = folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    playlists.take(50).forEach { playlist ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("spotify/playlist/${playlist.id}") }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val image = playlist.images.firstOrNull()?.url
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Text(text = playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
