package dev.musicx.desktop.ui.components

import androidx.compose.runtime.Composable
import dev.musicx.desktop.state.app.AppState
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.editSongMetadata
import dev.musicx.shared.model.resetSongMetadata
import dev.musicx.shared.state.SongMetadataState
import dev.musicx.shared.ui.menu.EditSongDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.koin.core.context.GlobalContext

@Composable
internal fun SongMetadataEditorDialog(
    song: SongItem,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val effectiveSong = SongMetadataState.apply(song)
    EditSongDialog(
        title = effectiveSong.title,
        artist =
            effectiveSong.artists
                .firstOrNull()
                ?.name
                .orEmpty(),
        onSave = { title, artist ->
            AppState.scope
                .async(Dispatchers.IO) {
                    editSongMetadata(GlobalContext.get().get<MusicDatabase>(), song, title, artist)
                }.await()
        },
        onSaved = onSaved,
        onDismiss = onDismiss,
        onReset = {
            AppState.scope
                .async(Dispatchers.IO) {
                    resetSongMetadata(GlobalContext.get().get<MusicDatabase>(), song.id)
                }.await()
        },
    )
}
