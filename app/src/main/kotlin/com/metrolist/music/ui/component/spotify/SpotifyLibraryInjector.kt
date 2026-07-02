/**
 * MuSicX Spotify Library Playlists Injector
 *
 * Provides a LazyListScope extension that lists the user's Spotify playlists
 * and a Liked Songs entry inline in LibraryPlaylistsScreen, plus a
 * LazyGridScope extension for GRID view. Guarded so both are a no-op when
 * Spotify is disabled or the user is not logged in.
 */

package com.metrolist.music.ui.component.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.EnableSpotifyKey
import com.metrolist.music.constants.SpotifySpDcKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.ui.component.GridItem
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.SpotifyViewModel

/**
 * Injects a "Your Spotify Library" section at the top of a LazyColumn.
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
    val likedTotal by viewModel.likedSongsTotal.collectAsStateWithLifecycle()

    // Trigger the initial data load exactly once when the section first appears
    // (viewModel is scoped to the enclosing NavBackStackEntry, so this survives
    // list recompositions but re-runs on true re-mount).
    LaunchedEffect(Unit) {
        if (viewModel.spotifyPlaylists.value.isEmpty()) viewModel.loadPlaylists()
        if (viewModel.likedSongs.value.isEmpty()) viewModel.loadLikedSongs()
    }

    Text(
        text = stringResource(R.string.spotify_playlists),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )

    // --- Liked Songs entry ---
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("spotify/liked") }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1DB954)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = stringResource(R.string.liked_songs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (likedTotal > 0) {
                Text(
                    text = "$likedTotal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // --- Folders (top-level) ---
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.folder),
                    contentDescription = null,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(text = folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    // --- Root-level playlists ---
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
            Spacer(Modifier.size(12.dp))
            Text(text = playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// -----------------------------------------------------------------------------
// GRID version — used by LibraryPlaylistsScreen when LibraryViewType == GRID.
// Each Spotify entry occupies one grid cell (default 1-column span), matching
// the visual style of the surrounding auto-playlist / user-playlist tiles.
//
// State is observed by the caller via [rememberSpotifyLibraryGridState] so
// each Spotify tile can be emitted as its own LazyGridScope.item() (Compose
// LazyGrid requires one item = one cell).
// -----------------------------------------------------------------------------

/**
 * Immutable snapshot of Spotify library data used by [spotifyLibraryPlaylistsGrid].
 * `null` folders / playlists means "not logged in / disabled" and no tiles render.
 */
data class SpotifyLibraryGridState(
    val enabled: Boolean,
    val folders: List<com.metrolist.spotify.models.SpotifyLibraryFolder>,
    val playlists: List<com.metrolist.spotify.models.SpotifyPlaylist>,
    val likedTotal: Int,
)

/**
 * Observes the SpotifyViewModel from the caller's @Composable scope, kicks off
 * the initial data load on first appearance, and returns a stable snapshot for
 * the grid to render. Must be called from a @Composable.
 */
@Composable
fun rememberSpotifyLibraryGridState(
    viewModel: SpotifyViewModel = hiltViewModel(),
): SpotifyLibraryGridState {
    val enableSpotify by rememberPreference(EnableSpotifyKey, defaultValue = false)
    val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
    val folders by viewModel.spotifyRootFolders.collectAsStateWithLifecycle()
    val playlists by viewModel.spotifyRootPlaylists.collectAsStateWithLifecycle()
    val likedTotal by viewModel.likedSongsTotal.collectAsStateWithLifecycle()

    val enabled = enableSpotify && spDc.isNotEmpty()

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        if (viewModel.spotifyPlaylists.value.isEmpty()) viewModel.loadPlaylists()
        if (viewModel.likedSongs.value.isEmpty()) viewModel.loadLikedSongs()
    }

    return SpotifyLibraryGridState(enabled, folders, playlists, likedTotal)
}

/**
 * Injects Spotify Liked Songs tile, folder tiles and playlist tiles into a
 * LazyVerticalGrid. Each Spotify entry becomes its own grid cell. No-op if
 * `state.enabled == false`.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.spotifyLibraryPlaylistsGrid(
    navController: NavController,
    state: SpotifyLibraryGridState,
) {
    if (!state.enabled) return

    item(key = "musicx_spotify_liked_grid_tile") {
        GridItem(
            title = stringResource(R.string.spotify_liked_songs),
            subtitle = if (state.likedTotal > 0) "${state.likedTotal}" else "",
            thumbnailContent = {
                val iconSize = maxWidth / 2
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1DB954)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.spotify),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(iconSize),
                    )
                }
            },
            fillMaxWidth = true,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { navController.navigate("spotify/liked") }),
        )
    }

    items(
        items = state.folders,
        key = { folder -> "sp_folder_${folder.uri}" },
    ) { folder ->
        val folderId = folder.uri.substringAfterLast(":")
        GridItem(
            title = folder.name,
            subtitle = "",
            thumbnailContent = {
                val iconSize = maxWidth / 2
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.folder),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                    )
                }
            },
            fillMaxWidth = true,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {
                    navController.navigate("spotify/folder/$folderId")
                }),
        )
    }

    items(
        items = state.playlists,
        key = { pl -> "sp_playlist_${pl.id}" },
    ) { sp ->
        val thumb = sp.images.firstOrNull()?.url
        val synthetic = Playlist(
            playlist = PlaylistEntity(
                id = "spotify_${sp.id}",
                name = sp.name,
                thumbnailUrl = thumb,
                remoteSongCount = sp.tracks?.total,
                isEditable = false,
            ),
            songCount = 0,
            songThumbnails = listOfNotNull(thumb),
        )
        PlaylistGridItem(
            playlist = synthetic,
            fillMaxWidth = true,
            badges = { /* no download-state indicator for remote Spotify playlists */ },
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {
                    navController.navigate("spotify/playlist/${sp.id}")
                }),
        )
    }
}


