@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.discover_library_tile
import com.metrolist.shared.generated.resources.refresh
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.ui.components.YTListItem
import com.metrolist.shared.ui.components.rememberItemPlayback
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun DiscoverLibraryScreen(
    songs: List<SongItem>,
    onRefresh: () -> Unit,
    onTrackClick: (SongItem) -> Unit,
    currentSongId: State<String?>,
    isPlaying: State<Boolean>,
    onBackClick: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.discover_library_tile)) },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = MetrolistIcons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = MetrolistIcons.Filled.Refresh,
                        contentDescription = stringResource(Res.string.refresh),
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        )
        Box(Modifier.fillMaxSize().padding(PaddingValues(bottom = 72.dp))) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(songs, key = { it.id }) { song ->
                    val playback by rememberItemPlayback(song.id, currentSongId, isPlaying)
                    YTListItem(
                        item = song,
                        onClick = { onTrackClick(song) },
                        isCurrent = playback.isCurrent,
                        isPlaying = playback.isPlaying,
                    )
                }
            }
        }
    }
}
