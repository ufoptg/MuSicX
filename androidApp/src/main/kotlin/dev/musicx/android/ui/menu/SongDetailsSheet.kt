/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.android.ui.menu

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.musicx.android.playback.PlayerConnection
import dev.musicx.android.ui.component.AdaptiveModalBottomSheet
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.ui.components.rememberModalSheetState
import dev.musicx.shared.ui.menu.SongDetailsContent
import dev.musicx.shared.ui.theme.ComponentShapes
import dev.musicx.shared.youtube.playback.PlaybackResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(
    song: SongItem?,
    playbackResource: PlaybackResource? = null,
    volume: Float? = null,
    onDismiss: () -> Unit,
) {
    val database: MusicDatabase = koinInject()
    val playerConnection: PlayerConnection = koinInject()
    val currentResource by playerConnection.currentPlaybackResource.collectAsState()
    val currentVolume by playerConnection.volume.collectAsState()
    val sheetState = rememberModalSheetState(SheetValue.Hidden)
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = ComponentShapes.BottomSheetShape) {
        SongDetailsContent(song, database, playbackResource ?: currentResource, volume ?: currentVolume)
    }
}
