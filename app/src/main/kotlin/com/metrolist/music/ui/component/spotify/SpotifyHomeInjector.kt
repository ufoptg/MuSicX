/**
 * MuSicX Spotify Home Injector
 *
 * Self-contained composable that renders Spotify home sections on the main
 * HomeScreen. Guarded by user preferences so it is a no-op when disabled or
 * not logged in. Keeps HomeScreen.kt untouched except for a single call site.
 */

package com.metrolist.music.ui.component.spotify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.EnableSpotifyKey
import com.metrolist.music.constants.SpotifySpDcKey
import com.metrolist.music.constants.UseSpotifyHomeKey
import com.metrolist.music.models.SectionType
import com.metrolist.music.models.SpotifyHomeSection
import com.metrolist.music.playback.SpotifyYouTubeMapper
import com.metrolist.music.playback.queues.SpotifyQueue
import com.metrolist.music.ui.component.SpotifyAlbumSectionRow
import com.metrolist.music.ui.component.SpotifyArtistSectionRow
import com.metrolist.music.ui.component.SpotifyPlaylistSectionRow
import com.metrolist.music.ui.component.SpotifyTrackSectionRow
import com.metrolist.music.ui.component.resolveSpotifySectionTitle
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.SpotifyHomeViewModel

/**
 * Injects Spotify home sections into a LazyColumn. Call from within a LazyColumn
 * scope. No-op unless Spotify is enabled + home injection enabled + logged in.
 *
 * Usage inside HomeScreen.kt:
 *     LazyColumn(...) {
 *         spotifyHomeSections(navController)
 *         // ... existing YouTube sections
 *     }
 */
fun LazyListScope.spotifyHomeSections(
    navController: NavController,
) {
    item(key = "musicx_spotify_home_gate") {
        val enableSpotify by rememberPreference(EnableSpotifyKey, defaultValue = false)
        val useSpotifyHome by rememberPreference(UseSpotifyHomeKey, defaultValue = true)
        val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")

        if (!enableSpotify || !useSpotifyHome || spDc.isEmpty()) return@item

        SpotifyHomeSectionsContent(navController)
    }
}

@Composable
private fun SpotifyHomeSectionsContent(
    navController: NavController,
    viewModel: SpotifyHomeViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()

    Column {
        sections.forEach { section ->
            SpotifySectionBlock(section, navController)
        }
    }
}

@Composable
private fun SpotifySectionBlock(
    section: SpotifyHomeSection,
    navController: NavController,
) {
    val title = resolveSpotifySectionTitle(section)
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
    )
    when (section.type) {
        SectionType.PLAYLISTS -> SpotifyPlaylistSectionRow(
            playlists = section.playlists,
            onPlaylistClick = { p -> navController.navigate("spotify/playlist/${p.id}") },
        )
        SectionType.ALBUMS -> SpotifyAlbumSectionRow(
            albums = section.albums,
            onAlbumClick = { a -> navController.navigate("spotify/album/${a.id}") },
        )
        SectionType.ARTISTS -> SpotifyArtistSectionRow(
            artists = section.artists,
            onArtistClick = { /* No Spotify artist screen ported yet; no-op */ },
        )
        SectionType.TRACKS -> SpotifyTracksBlock(section)
    }
}

/**
 * Renders a Spotify TRACKS section (e.g. "Recently Played", "Your Top Tracks").
 * Wires the row up to the player so tapping a track builds a SpotifyQueue and
 * starts playback via the shared PlayerConnection.
 */
@Composable
private fun SpotifyTracksBlock(section: SpotifyHomeSection) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    // Match HomeScreen's item-width heuristic: ~half the screen when the screen
    // is wide enough, otherwise near-full-width so long titles remain readable.
    val screenWidthDp = configuration.screenWidthDp.dp
    val itemWidthFactor = if (screenWidthDp * 0.475f >= 320.dp) 0.475f else 0.9f
    val horizontalItemWidth = screenWidthDp * itemWidthFactor

    val mapper = remember(database) { SpotifyYouTubeMapper(database) }

    SpotifyTrackSectionRow(
        tracks = section.tracks,
        horizontalItemWidth = horizontalItemWidth,
        isPlaying = isPlaying,
        currentMediaId = mediaMetadata?.id,
        onTrackClick = { track ->
            playerConnection.playQueue(
                SpotifyQueue(
                    initialTrack = track,
                    mapper = mapper,
                    context = context,
                    database = database,
                ),
            )
        },
        onTrackLongClick = { /* Long-press menu not ported to MuSicX yet */ },
    )
}
