package dev.musicx.shared.ui.components

import androidx.compose.runtime.Composable
import dev.musicx.shared.model.BuiltinPlaylist
import dev.musicx.shared.model.PlaylistItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun localizedBuiltinPlaylist(playlist: PlaylistItem): PlaylistItem {
    val builtin = BuiltinPlaylist.fromId(playlist.id) ?: return playlist
    val resolved = builtin.resolve(playlist, stringResource(builtin.title))
    return resolved.copy(thumbnail = libraryArtwork(resolved.id, resolved.thumbnail, resolved.customThumbnail))
}
