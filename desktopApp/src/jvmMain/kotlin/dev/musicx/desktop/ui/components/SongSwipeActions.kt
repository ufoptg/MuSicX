package dev.musicx.desktop.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.shared.model.SongItem
import kotlinx.coroutines.launch
import dev.musicx.shared.ui.components.SongSwipeActions as SharedSongSwipeActions

@Composable
fun SongSwipeActions(
    song: SongItem,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    SharedSongSwipeActions(
        song = song,
        onFeedback = { message ->
            AppState.snackbarHostState.currentSnackbarData?.dismiss()
            AppState.scope.launch {
                AppState.snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        },
        enabled = enabled && !PlayerState.isListenTogetherGuest(),
        content = content,
    )
}
