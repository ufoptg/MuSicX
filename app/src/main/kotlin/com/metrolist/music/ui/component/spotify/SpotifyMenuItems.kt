/**
 * MuSicX Spotify Playlist-Add Dialog
 *
 * Standalone dialog that lists the user's Spotify playlists and adds a given
 * Spotify URI to the selected one. Called from SongMenu / PlayerMenu.
 */

package com.metrolist.music.ui.component.spotify

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.ui.menu.SpotifyPlaylistPickerDialog
import com.metrolist.spotify.Spotify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Renders the Spotify playlist picker + performs the add operation.
 * The caller controls visibility via [show].
 *
 * @param spotifyUri Spotify URI of the track, e.g. "spotify:track:...".
 *                   If null, the dialog is a no-op that immediately dismisses
 *                   with a toast (nothing to add).
 */
@Composable
fun SpotifyAddToPlaylistDialog(
    show: Boolean,
    spotifyUri: String?,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SpotifyPlaylistPickerDialog(
        onSelect = { picked ->
            onDismiss()
            if (spotifyUri.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.spotify_add_to_playlist_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                scope.launch(Dispatchers.IO) {
                    Spotify.addTracksToPlaylist(picked.id, listOf(spotifyUri))
                }
            }
        },
        onDismiss = onDismiss,
    )
}
