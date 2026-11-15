package dev.musicx.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.desktop.state.content.BrowseState
import dev.musicx.desktop.state.content.PlaylistAlbumState
import dev.musicx.desktop.state.content.PodcastState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.ui.components.AlbumContextMenu
import dev.musicx.desktop.ui.components.SongContextMenu
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.error
import dev.musicx.shared.generated.resources.play
import dev.musicx.shared.generated.resources.shuffle
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.BrowseItem
import dev.musicx.shared.model.BrowsePageData
import dev.musicx.shared.model.EpisodeItem
import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.model.PodcastItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.model.toSongItem
import dev.musicx.shared.settings.BlockedContent
import dev.musicx.shared.ui.menu.cursorContextMenu
import dev.musicx.shared.ui.menu.rememberCursorContextMenuState
import dev.musicx.shared.ui.navigation.LocalNavigator
import dev.musicx.shared.ui.navigation.Screen
import dev.musicx.shared.ui.screens.BrowseContent
import dev.musicx.shared.viewmodels.BrowseViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseScreen(
    colorScheme: ColorScheme,
    section: Screen.HomeSection? = null,
) {
    val navigator = LocalNavigator.current
    val menu = rememberCursorContextMenuState()
    val selectedSong = remember { mutableStateOf<SongItem?>(null) }
    val selectedAlbum = remember { mutableStateOf<AlbumItem?>(null) }

    val scope = rememberCoroutineScope()
    val sectionModel =
        remember(section) {
            section?.section?.let {
                BrowseViewModel(it.browseId, it.params, it.title, scope, initialSection = it)
            }
        }
    val blockedVersion by BlockedContent.version.collectAsState()
    val page = if (section == null) BrowseState.browsePageData else sectionModel?.page ?: BrowsePageData(title = section.title)
    val visiblePage = remember(page, blockedVersion) { page.copy(sections = BlockedContent.filterSections(page.sections)) }
    val songs =
        visiblePage.sections
            .flatMap { it.items }
            .filterIsInstance<SongItem>()
            .filterNot { it.isUnavailable }
    val showSongActions = section != null && songs.isNotEmpty() && visiblePage.sections.all { shelf -> shelf.items.all { it is SongItem } }
    val loadError = stringResource(Res.string.error)

    Box(Modifier.fillMaxSize().cursorContextMenu(menu, openOnSecondaryClick = false)) {
        BrowseContent(
            page = visiblePage,
            browseId = if (section == null) BrowseState.selectedBrowseId else section.section?.browseId,
            title = section?.title ?: BrowseState.browsePageData.title ?: BrowseState.selectedBrowseTitle,
            sectionPage = section != null,
            isLoading = sectionModel?.isLoading ?: (section == null && BrowseState.isBrowseLoading),
            isLoadingMore = sectionModel?.isLoadingMore ?: (section == null && BrowseState.isLoadingMore),
            onBackClick = { navigator.goBack() },
            onItemClick = ::openBrowseItem,
            onItemLongClick = {
                if (it is SongItem) {
                    selectedAlbum.value = null
                    selectedSong.value = it
                    menu.open()
                }
                if (it is AlbumItem) {
                    selectedSong.value = null
                    selectedAlbum.value = it
                    menu.open()
                }
            },
            onLoadMore = {
                if (sectionModel != null) {
                    sectionModel.loadMore()
                } else if (section == null) {
                    BrowseState.loadMore()
                }
            },
            error = loadError.takeIf { sectionModel?.loadFailed == true },
            onRetry = { sectionModel?.load() },
            loadMoreError = loadError.takeIf { sectionModel?.loadMoreFailed ?: (section == null && BrowseState.loadMoreFailed) },
            hasMore = sectionModel?.hasMore ?: (section == null && BrowseState.hasMore),
            headerContent =
                if (showSongActions) {
                    {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { songs.firstOrNull()?.let { PlayerState.playTrack(it, songs) } },
                                modifier = Modifier.weight(1f),
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(stringResource(Res.string.play)) }
                            FilledTonalButton(
                                onClick = { PlayerState.shuffleQueue(songs) },
                                modifier = Modifier.weight(1f),
                                shapes = ButtonDefaults.shapes(),
                            ) { Text(stringResource(Res.string.shuffle)) }
                        }
                    }
                } else {
                    null
                },
            currentSongId = remember { derivedStateOf { PlayerState.currentTrack?.id } },
            isPlaying = remember { derivedStateOf { PlayerState.isPlaying } },
        )
        selectedAlbum.value?.let { album ->
            AlbumContextMenu(
                album = album,
                expanded = menu.expanded,
                offset = menu.offset,
                onDismiss = {
                    menu.dismiss()
                },
            )
        }
        selectedSong.value?.let { song ->
            SongContextMenu(
                song = song,
                expanded = menu.expanded,
                offset = menu.offset,
                onDismiss = {
                    menu.dismiss()
                },
            )
        }
    }
}

private fun openBrowseItem(
    item: YTItem,
    songs: List<SongItem>,
) {
    when (item) {
        is SongItem -> PlayerState.playTrack(item, songs.ifEmpty { null })
        is AlbumItem -> PlaylistAlbumState.fetchAlbumData(item.id)
        is ArtistItem -> ArtistState.fetchArtistData(item)
        is PlaylistItem -> PlaylistAlbumState.fetchPlaylistData(item.id, item.thumbnail)
        is BrowseItem -> BrowseState.fetchBrowseData(item.id, item.params, item.title)
        is PodcastItem -> PodcastState.fetch(item.id)
        is EpisodeItem -> PlayerState.playTrack(item.toSongItem())
    }
}
