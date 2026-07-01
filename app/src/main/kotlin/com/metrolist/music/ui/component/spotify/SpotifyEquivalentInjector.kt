/**
 * MuSicX Spotify Equivalent Injector
 *
 * Adds a "Open in Spotify" button on ArtistScreen / AlbumScreen when at
 * least one child song has a resolved Spotify match. Opens the Spotify
 * web URL via ACTION_VIEW so it works even if the Spotify app isn't
 * installed (the browser will handle it).
 */

package com.metrolist.music.ui.component.spotify

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.constants.EnableSpotifyKey
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adds an "Open on Spotify" LazyColumn item to AlbumScreen / ArtistScreen
 * when [seedSongId] has a Spotify match. No-op otherwise.
 */
fun LazyListScope.spotifyEquivalentButton(
    seedSongId: String,
    key: String = "musicx_spotify_equiv_$seedSongId",
) {
    item(key = key) {
        SpotifyEquivalentButton(seedSongId)
    }
}

@Composable
private fun SpotifyEquivalentButton(seedSongId: String) {
    val enableSpotify by rememberPreference(EnableSpotifyKey, defaultValue = false)
    if (!enableSpotify) return

    val database = LocalDatabase.current
    val context = LocalContext.current
    var spotifyTrackId by remember(seedSongId) { mutableStateOf<String?>(null) }

    LaunchedEffect(seedSongId) {
        spotifyTrackId = withContext(Dispatchers.IO) {
            runCatching { database.getSpotifyMatchByYouTubeId(seedSongId)?.spotifyId }.getOrNull()
        }
    }

    val id = spotifyTrackId ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, "https://open.spotify.com/track/$id".toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.spotify),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.spotify_integration))
        }
    }
}
