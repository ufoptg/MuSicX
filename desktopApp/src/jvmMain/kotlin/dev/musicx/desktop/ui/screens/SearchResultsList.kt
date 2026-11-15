/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.desktop.state.content.PlaylistAlbumState
import dev.musicx.desktop.state.content.PodcastState
import dev.musicx.desktop.state.content.SearchState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.ui.components.AlbumContextMenu
import dev.musicx.desktop.ui.components.ArtistContextMenu
import dev.musicx.desktop.ui.components.EpisodeContextMenu
import dev.musicx.desktop.ui.components.PlaylistContextMenu
import dev.musicx.desktop.ui.components.PodcastContextMenu
import dev.musicx.desktop.ui.components.SongContextMenu
import dev.musicx.desktop.ui.components.SongSwipeActions
import dev.musicx.shared.generated.resources.album
import dev.musicx.shared.generated.resources.artist
import dev.musicx.shared.generated.resources.episode
import dev.musicx.shared.generated.resources.playlist
import dev.musicx.shared.generated.resources.podcast
import dev.musicx.shared.generated.resources.search
import dev.musicx.shared.generated.resources.songs
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.EpisodeItem
import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.model.PodcastItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.model.toSongItem
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.ui.menu.contextMenuAnchor
import dev.musicx.shared.ui.menu.rememberCursorContextMenuState
import dev.musicx.shared.ui.screens.SearchScreen
import dev.musicx.shared.ui.screens.searchScreenText
import kotlinx.coroutines.launch

@Composable
fun SearchResultsList(
    colorScheme: ColorScheme,
    onBack: () -> Unit,
) {
    val uiState = SearchState.uiState
    var selectedItemForMenu by remember { mutableStateOf<YTItem?>(null) }
    val menuAnchor = rememberCursorContextMenuState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .contextMenuAnchor(menuAnchor),
    ) {
        SearchScreen(
            itemContainer = { item, content ->
                if (item is SongItem) SongSwipeActions(item, content = content) else content()
            },
            uiState = uiState,
            text = searchScreenText(),
            onQueryChange = { SearchState.onQueryChange(it) },
            onSearch = { SearchState.search(it) },
            onRemoveHistoryItem = SearchState::removeSearchQuery,
            onBack = onBack,
            onItemClick = { item ->
                openSearchItem(item, emptyList())
            },
            onItemLongClick = { item ->
                selectedItemForMenu = item
                menuAnchor.open()
            },
            onSongSave = { PlayerState.showAddToPlaylistSong = it },
            onHistoryItemClick = { query ->
                SearchState.search(query)
            },
            onHistoryItemInsert = { query ->
                SearchState.onQueryChange(query)
            },
            onClearHistory = {
                SearchState.clearSearchHistory()
            },
            onFilterSelected = { filter ->
                SearchState.updateSearchFilter(filter)
            },
            onRetryLoadMore = SearchState::retryLoadMoreFilteredResults,
            onLoadMore = {
                SearchState.loadMoreFilteredResults()
            },
            onArtistClick = { artistId ->
                ArtistState.fetchArtistData(artistId)
            },
            onAlbumClick = { albumId ->
                PlaylistAlbumState.fetchAlbumData(albumId)
            },
            onShuffleClick = { item ->
                if (item is ArtistItem) {
                    AppState.scope.launch {
                        GlobalYouTubeRepository.instance
                            .getArtistSongs(
                                item.id,
                            ).takeIf { it.isNotEmpty() }
                            ?.let {
                                PlayerState.shuffleQueue(it, songsOnly = true)
                            }
                    }
                }
            },
            onMixClick = { item ->
                if (item is ArtistItem) {
                    AppState.scope.launch {
                        GlobalYouTubeRepository.instance
                            .startArtistRadio(
                                item.id,
                            ).takeIf { it.isNotEmpty() }
                            ?.let {
                                PlayerState.playTrack(it.first(), it)
                            }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            showSearchBar = false,
        )

        selectedItemForMenu?.let { item ->
            when (item) {
                is SongItem -> {
                    SongContextMenu(
                        song = item,
                        expanded = menuAnchor.expanded,
                        offset = menuAnchor.offset,
                        onDismiss = menuAnchor::dismiss,
                    )
                }

                is AlbumItem -> {
                    AlbumContextMenu(
                        album = item,
                        expanded = menuAnchor.expanded,
                        offset = menuAnchor.offset,
                        onDismiss = menuAnchor::dismiss,
                    )
                }

                is ArtistItem -> {
                    ArtistContextMenu(
                        artist = item,
                        expanded = menuAnchor.expanded,
                        offset = menuAnchor.offset,
                        onDismiss = menuAnchor::dismiss,
                    )
                }

                is PlaylistItem -> {
                    PlaylistContextMenu(
                        playlist = item,
                        expanded = menuAnchor.expanded,
                        offset = menuAnchor.offset,
                        onDismiss = menuAnchor::dismiss,
                    )
                }

                is PodcastItem -> {
                    PodcastContextMenu(
                        podcast = item,
                        expanded = menuAnchor.expanded,
                        offset = menuAnchor.offset,
                        onDismiss = menuAnchor::dismiss,
                    )
                }

                is EpisodeItem -> {
                    EpisodeContextMenu(
                        episode = item,
                        expanded = menuAnchor.expanded,
                        offset = menuAnchor.offset,
                        onDismiss = menuAnchor::dismiss,
                    )
                }

                else -> {
                    selectedItemForMenu = null
                }
            }
        }
    }
}

private fun openSearchItem(
    item: YTItem,
    songs: List<SongItem>,
) {
    when (item) {
        is SongItem -> PlayerState.playTrack(item, songs.ifEmpty { null })
        is EpisodeItem -> PlayerState.playTrack(item.toSongItem())
        is PodcastItem -> PodcastState.fetch(item.id)
        is AlbumItem -> PlaylistAlbumState.fetchAlbumData(item.id)
        is ArtistItem -> ArtistState.fetchArtistData(item)
        is PlaylistItem -> PlaylistAlbumState.fetchPlaylistData(item.id, item.thumbnail)
        else -> Unit
    }
}
