package dev.musicx.shared.ui.mobile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.ui.components.ModalBottomSheet
import dev.musicx.shared.ui.menu.SongDetailsContent
import dev.musicx.shared.ui.playback.PlaybackUiState
import dev.musicx.shared.ui.theme.ComponentShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileSongDetailsSheet(
    state: PlaybackUiState,
    database: MusicDatabase,
    onDismiss: () -> Unit,
    song: SongItem? = state.currentSong,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = ComponentShapes.BottomSheetShape) {
        SongDetailsContent(
            song = song,
            database = database,
            playbackResource = state.currentPlaybackResource,
            durationMs = state.durationMs.takeIf { it > 0 && state.currentSong?.id == song?.id },
        )
    }
}
