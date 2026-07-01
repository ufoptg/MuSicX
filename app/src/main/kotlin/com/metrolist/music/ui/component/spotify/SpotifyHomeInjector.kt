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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.constants.EnableSpotifyKey
import com.metrolist.music.constants.SpotifySpDcKey
import com.metrolist.music.constants.UseSpotifyHomeKey
import com.metrolist.music.models.SectionType
import com.metrolist.music.models.SpotifyHomeSection
import com.metrolist.music.ui.component.SpotifyAlbumSectionRow
import com.metrolist.music.ui.component.SpotifyArtistSectionRow
import com.metrolist.music.ui.component.SpotifyPlaylistSectionRow
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
        SectionType.TRACKS -> {
            // Track sections require player-connection wiring; deferred.
            // No-op keeps the injector safe even when top-tracks are present.
        }
    }
}
