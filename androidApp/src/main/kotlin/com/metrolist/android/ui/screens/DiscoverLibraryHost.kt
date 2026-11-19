@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.android.playback.LocalPlayerConnection
import com.metrolist.android.ui.components.handleItemClick
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.shuffle
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.screens.DiscoverLibraryScreen
import com.metrolist.shared.ui.screens.homeContentText
import com.metrolist.shared.ui.tv.tvFocusHighlight
import org.jetbrains.compose.resources.stringResource
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun DiscoverLibraryHost(
    onNavigateBack: () -> Unit = {},
    homeViewModel: HomeViewModel = koinViewModel(),
) {
    val homeState by homeViewModel.shared.uiState.collectAsState()
    val text = homeContentText()
    val playerConnection = LocalPlayerConnection.current
    val currentSong = playerConnection?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    val isPlaying = playerConnection?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val songs = homeState.discoverSomethingNew.filterIsInstance<SongItem>()
    val showSongActions = songs.isNotEmpty()

    Column(Modifier.fillMaxWidth()) {
        if (showSongActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        playerConnection?.playQueue(songs, queueTitle = text.discoverLibraryTile, startVideo = false)
                    },
                    modifier = Modifier.weight(1f).tvFocusHighlight(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(MetrolistIcons.Default.PlayArrow, contentDescription = null)
                    Text(stringResource(Res.string.play))
                }
                FilledTonalButton(
                    onClick = {
                        playerConnection?.shuffleQueue(songs, queueTitle = text.discoverLibraryTile)
                    },
                    modifier = Modifier.weight(1f).tvFocusHighlight(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(MetrolistIcons.Rounded.Shuffle, contentDescription = null)
                    Text(stringResource(Res.string.shuffle))
                }
            }
            HorizontalDivider(Modifier.padding(top = 18.dp))
        }
        DiscoverLibraryScreen(
            songs = songs,
            onRefresh = homeViewModel::refreshDiscoverBatch,
            onTrackClick = { song ->
                handleItemClick(
                    song,
                    onNavigateToAlbum = {},
                    onNavigateToArtist = {},
                    onNavigateToPlaylist = { _, _ -> },
                    onNavigateToPodcast = {},
                    playerConnection,
                    scope,
                    onNavigateToBrowse = { _, _, _ -> },
                    queue = songs.filterIsInstance<YTItem>().filter { it is SongItem },
                    startVideo = false,
                )
            },
            currentSongId = remember(currentSong) { derivedStateOf { currentSong.value?.id } },
            isPlaying = isPlaying,
            onBackClick = onNavigateBack,
        )
    }
}