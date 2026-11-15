@file:Suppress("FunctionName")

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.shared.ui.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.db.collectionPlayCounts
import dev.musicx.shared.db.entities.CachedAudioEntity
import dev.musicx.shared.db.entities.KIND_CACHE
import dev.musicx.shared.db.entities.KIND_DOWNLOAD
import dev.musicx.shared.db.entities.toSongItem
import dev.musicx.shared.db.mostPlayedHistorySongs
import dev.musicx.shared.db.refreshHistoryPlaylist
import dev.musicx.shared.db.refreshUploadedSongs
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.back
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.delete
import dev.musicx.shared.generated.resources.error
import dev.musicx.shared.generated.resources.hide_from_library_local
import dev.musicx.shared.generated.resources.library_empty
import dev.musicx.shared.generated.resources.more_options
import dev.musicx.shared.generated.resources.new_playlist
import dev.musicx.shared.generated.resources.play
import dev.musicx.shared.generated.resources.remove
import dev.musicx.shared.generated.resources.remove_selected_count
import dev.musicx.shared.generated.resources.retry
import dev.musicx.shared.generated.resources.stored_remove_selected_message
import dev.musicx.shared.generated.resources.stored_remove_selected_title
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.BuiltinPlaylist
import dev.musicx.shared.model.CollectionMeta
import dev.musicx.shared.model.CollectionSort
import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.model.PlaylistSongSort
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.SortDirection
import dev.musicx.shared.model.TopPlaylistPeriod
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.model.defaultDirection
import dev.musicx.shared.model.filterForPlaylistSearch
import dev.musicx.shared.model.indexOfPlaylistOccurrence
import dev.musicx.shared.model.playlistOccurrenceKey
import dev.musicx.shared.model.presentCollection
import dev.musicx.shared.model.sortedForLikedPlaylist
import dev.musicx.shared.model.sortedForPlaylist
import dev.musicx.shared.model.sortedForTopPlaylist
import dev.musicx.shared.playlist.addSongToStoredPlaylist
import dev.musicx.shared.playlist.moveSongInLocalPlaylist
import dev.musicx.shared.playlist.removeSongFromLocalPlaylist
import dev.musicx.shared.settings.BlockedContent
import dev.musicx.shared.settings.HiddenLibraryItems
import dev.musicx.shared.settings.LibraryFilterType
import dev.musicx.shared.settings.SavedPlaylistCollectionSorting
import dev.musicx.shared.settings.SavedPlaylistSongSorting
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.state.SongLikeState
import dev.musicx.shared.state.SongMetadataState
import dev.musicx.shared.storage.isAvailableDownload
import dev.musicx.shared.ui.components.AddSongSheetContent
import dev.musicx.shared.ui.components.AlertDialog
import dev.musicx.shared.ui.components.CollectionFilterBar
import dev.musicx.shared.ui.components.DownloadPlaylistFilterBar
import dev.musicx.shared.ui.components.LibraryItemContainer
import dev.musicx.shared.ui.components.LoadingScreen
import dev.musicx.shared.ui.components.ModalBottomSheet
import dev.musicx.shared.ui.components.PlaylistOrderControls
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.components.PopupVisibility
import dev.musicx.shared.ui.components.RefreshIndicator
import dev.musicx.shared.ui.components.ScrollingAddButton
import dev.musicx.shared.ui.components.SongListItem
import dev.musicx.shared.ui.components.SongSelectionControls
import dev.musicx.shared.ui.components.SongSwipeActions
import dev.musicx.shared.ui.components.TopPlaylistOrderControls
import dev.musicx.shared.ui.components.libraryCover
import dev.musicx.shared.ui.components.rememberDownloadPlaylistFilter
import dev.musicx.shared.ui.components.rememberScrollingFabState
import dev.musicx.shared.ui.components.topPlaylistOrderControlsText
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.menu.SpeedDialPinMenuItem
import dev.musicx.shared.ui.navigation.NavigationBackHandler
import dev.musicx.shared.ui.navigation.navigationPopupSource
import dev.musicx.shared.ui.playback.PlaybackController
import dev.musicx.shared.ui.playback.listState
import dev.musicx.shared.ui.screens.LibraryContent
import dev.musicx.shared.ui.screens.LibraryFilter
import dev.musicx.shared.ui.screens.PlaylistContent
import dev.musicx.shared.ui.screens.collectionFilterBarText
import dev.musicx.shared.ui.screens.libraryAutoPlaylists
import dev.musicx.shared.ui.screens.libraryContentText
import dev.musicx.shared.ui.screens.playlistContentText
import dev.musicx.shared.ui.screens.playlistOrderControlsText
import dev.musicx.shared.ui.screens.rememberLibrarySearchScrollState
import dev.musicx.shared.ui.selection.rememberSelectionState
import dev.musicx.shared.ui.theme.MdSpacing
import dev.musicx.shared.viewmodels.LibraryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import dev.musicx.shared.model.toSongItem as episodeToSongItem

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun MobileLibrary(
    viewModel: LibraryViewModel,
    database: MusicDatabase,
    playbackController: PlaybackController,
    onItemClick: (YTItem) -> Unit,
    onAutoPlaylistClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: ((YTItem) -> Unit)? = null,
    onCreatePlaylist: (() -> Unit)? = null,
    onPlayAlbum: ((AlbumItem) -> Unit)? = null,
    bottomContentPadding: Dp = 0.dp,
) {
    val playbackState by remember(playbackController) {
        playbackController.listState
    }.collectAsState(playbackController.state.value.listState())
    val uiState by viewModel.uiState.collectAsState()
    val contentMatchedSongs by viewModel.contentMatchedSongs.collectAsState()
    val contentSearchHasMore by viewModel.contentSearchHasMore.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }
    NavigationBackHandler(enabled = isSearchOpen) {
        isSearchOpen = false
        searchQuery = ""
    }
    val hiddenVersion by HiddenLibraryItems.version.collectAsState()
    var selectedFilter by remember {
        mutableStateOf(
            when (Settings.shared.libraryFilter) {
                LibraryFilterType.DEFAULT -> {
                    when (Settings.shared.defaultOpenTab) {
                        "ARTISTS" -> LibraryFilter.ARTISTS
                        "ALBUMS" -> LibraryFilter.ALBUMS
                        "PLAYLISTS" -> LibraryFilter.PLAYLISTS
                        else -> null
                    }
                }

                LibraryFilterType.ALL -> {
                    null
                }

                LibraryFilterType.TRACKS -> {
                    LibraryFilter.TRACKS
                }

                LibraryFilterType.PLAYLISTS -> {
                    LibraryFilter.PLAYLISTS
                }

                LibraryFilterType.ARTISTS -> {
                    LibraryFilter.ARTISTS
                }

                LibraryFilterType.ALBUMS -> {
                    LibraryFilter.ALBUMS
                }

                LibraryFilterType.PODCASTS -> {
                    LibraryFilter.PODCASTS
                }
            },
        )
    }

    NavigationBackHandler(enabled = selectedFilter != null && !isSearchOpen) {
        selectedFilter = null
        Settings.shared.libraryFilter = LibraryFilterType.ALL
    }

    var isGridView by remember { mutableStateOf(Settings.shared.libraryGridView) }
    var manualOrder by remember { mutableStateOf(Settings.shared.libraryManualOrder) }
    val autoPlaylists = libraryAutoPlaylists()

    val hiddenItemKeys = remember(hiddenVersion, Settings.shared.hiddenLibraryPlaylistsJson) { HiddenLibraryItems.keys() }
    var userRefreshing by remember { mutableStateOf(false) }
    val isLoadingNow by rememberUpdatedState(uiState.isLoading)
    LaunchedEffect(userRefreshing) {
        if (!userRefreshing) return@LaunchedEffect
        withTimeoutOrNull(1_500L) { snapshotFlow { isLoadingNow }.first { it } }
        snapshotFlow { isLoadingNow }.first { !it }
        userRefreshing = false
    }
    val pullToRefreshState = rememberPullToRefreshState()
    val libraryGridState = rememberLibrarySearchScrollState(searchQuery, rememberLazyGridState())
    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = userRefreshing,
            onRefresh = {
                userRefreshing = true
                if (!isLoadingNow) viewModel.refresh()
            },
            state = pullToRefreshState,
            indicator = {
                RefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = userRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LibraryContent(
                presentation = viewModel.presentation,
                text = libraryContentText(),
                items = uiState.items,
                searchQuery = searchQuery,
                isSearchOpen = isSearchOpen,
                onSearchQueryChange = { searchQuery = it },
                onSearchToggle = {
                    isSearchOpen = !isSearchOpen
                    if (!isSearchOpen) searchQuery = ""
                },
                contentMatchedSongs = contentMatchedSongs,
                contentSearchHasMore = contentSearchHasMore,
                onLoadMoreContentMatches = viewModel::loadMoreContentMatches,
                onSearchDescendingChange = viewModel::setSearchDescending,
                autoPlaylists = autoPlaylists,
                hiddenItemKeys = hiddenItemKeys,
                isLoading = uiState.isLoading,
                isGridView = isGridView,
                selectedFilter = selectedFilter,
                manualOrder = manualOrder,
                topPadding = MdSpacing.xs + MdSpacing.xxs,
                bottomPadding = bottomContentPadding,
                compactGrid = true,
                onFilterChange = { filter ->
                    selectedFilter = filter
                    Settings.shared.libraryFilter =
                        when (filter) {
                            null -> LibraryFilterType.ALL
                            LibraryFilter.TRACKS -> LibraryFilterType.TRACKS
                            LibraryFilter.PLAYLISTS -> LibraryFilterType.PLAYLISTS
                            LibraryFilter.ARTISTS -> LibraryFilterType.ARTISTS
                            LibraryFilter.ALBUMS -> LibraryFilterType.ALBUMS
                            LibraryFilter.PODCASTS -> LibraryFilterType.PODCASTS
                        }
                },
                onGridViewChange = {
                    isGridView = it
                    Settings.shared.libraryGridView = it
                },
                onManualOrderChange = {
                    manualOrder = it
                    Settings.shared.libraryManualOrder = it
                },
                onItemClick = onItemClick,
                onPlaySongs = { songs, index -> playbackController.playQueue(songs, index) },
                currentSongId = playbackState.currentSongId,
                isPlaying = playbackState.isPlaying,
                onAutoPlaylistClick = { type ->
                    autoPlaylists.firstOrNull { it.type == type }?.let { onAutoPlaylistClick(type, it.title) }
                },
                itemContainer = { hiddenItem, ytItem, isGridView, onClick, content ->
                    var autoMenuExpanded by remember { mutableStateOf(false) }
                    val onMenuClick: (() -> Unit)? =
                        if (ytItem == null) {
                            { autoMenuExpanded = true }
                        } else {
                            onItemLongClick?.let { callback -> { callback(ytItem) } }
                        }
                    Box {
                        LibraryItemContainer(
                            isGridView = isGridView,
                            onMenuClick = onMenuClick,
                            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onMenuClick),
                            content = content,
                        )
                        DropdownMenuPopup(expanded = autoMenuExpanded, onDismissRequest = { autoMenuExpanded = false }) {
                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                                BuiltinPlaylist.fromId("auto:${hiddenItem.id}")?.let { builtin ->
                                    val playlist = builtin.playlist(stringResource(builtin.title))
                                    SpeedDialPinMenuItem(playlist, database) { autoMenuExpanded = false }
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.hide_from_library_local)) },
                                    onClick = {
                                        HiddenLibraryItems.hide(hiddenItem)
                                        autoMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                scrollState = libraryGridState,
                onPlayAlbum = onPlayAlbum,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (onCreatePlaylist != null) {
            ScrollingAddButton(
                label = stringResource(Res.string.new_playlist),
                onClick = onCreatePlaylist,
                state = rememberScrollingFabState(libraryGridState),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = bottomContentPadding).padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileSongCollectionScreen(
    type: String,
    title: String,
    localPlaylistId: String?,
    database: MusicDatabase,
    playbackController: PlaybackController,
    onBack: () -> Unit,
    onMoreOptions: (PlaylistItem) -> Unit,
    onSongMenu: (SongItem, (() -> Unit)?, String?) -> Unit,
    onDownload: (List<SongItem>) -> Unit,
    onRemoveDownloads: (List<String>) -> Unit,
    onAddToPlaylist: (List<SongItem>) -> Unit,
    onSwipeFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    var showAddSong by remember(localPlaylistId) { mutableStateOf(false) }
    val downloadFilter = if (localPlaylistId == null && type == "downloaded") rememberDownloadPlaylistFilter(database) else null
    val downloadFilterState = downloadFilter?.state?.collectAsState()?.value
    val storedPlaylist by remember(database, localPlaylistId) {
        localPlaylistId?.let { database.dao().playlist(it) } ?: flowOf(null)
    }.collectAsState(null)
    val isStoredCollection = localPlaylistId == null && type in setOf("downloaded", "cached")
    val isLikedCollection = localPlaylistId == null && type == "liked"
    val isTopCollection = localPlaylistId == null && type == "top100"
    var topPeriod by remember(type) { mutableStateOf(TopPlaylistPeriod.ALL_TIME) }
    var topDirection by remember(type) { mutableStateOf(SortDirection.DESCENDING) }
    val playlistSortingKey = localPlaylistId?.let { "local:$it" } ?: "special:$type"
    var collectionEntries by remember(type) { mutableStateOf<List<CachedAudioEntity>>(emptyList()) }
    var collectionQuery by remember(type) { mutableStateOf("") }
    val defaultCollectionSort =
        when {
            type == "downloaded" -> Settings.shared.downloadedCollectionSort
            type == "cached" -> Settings.shared.cachedCollectionSort
            else -> CollectionSort.NEWEST
        }
    val defaultCollectionDirection =
        when {
            type == "downloaded" -> Settings.shared.downloadedCollectionSortDirection
            type == "cached" -> Settings.shared.cachedCollectionSortDirection
            else -> SortDirection.DESCENDING
        }
    val collectionSorting =
        Settings.shared.playlistCollectionSorting(
            playlistSortingKey,
            defaultCollectionSort,
            defaultCollectionDirection,
        )
    val collectionSort = collectionSorting.sort
    val collectionDirection = collectionSorting.direction
    val countIds =
        remember(isStoredCollection, collectionSort, collectionEntries) {
            if (isStoredCollection && collectionSort == CollectionSort.MOST_PLAYED) collectionEntries.map { it.songId } else emptyList()
        }
    val playCounts by remember(database, countIds) {
        if (countIds.isEmpty()) flowOf(emptyMap()) else database.collectionPlayCounts(countIds)
    }.collectAsState(emptyMap())
    val metadata =
        remember(collectionEntries, playCounts) {
            collectionEntries.associate {
                it.songId to
                    CollectionMeta(it.cachedAt, it.fileSize, it.lastAccessedAt, playCounts[it.songId] ?: 0)
            }
        }
    val playlistReorder =
        remember(localPlaylistId, database, scope) {
            MobilePlaylistReorder(
                scope = scope,
                persist = { mappingId, successorMappingId ->
                    moveSongInLocalPlaylist(database, checkNotNull(localPlaylistId), mappingId, successorMappingId)
                },
                reload = {
                    database
                        .dao()
                        .playlistSongs(checkNotNull(localPlaylistId))
                        .first()
                        .map { it.toSongItem() to it.map }
                },
            )
        }
    var loadedSongs by remember(type, localPlaylistId) { mutableStateOf<List<SongItem>>(emptyList()) }
    var isLoading by remember(type, localPlaylistId) { mutableStateOf(true) }
    var loadError by remember(type, localPlaylistId) { mutableStateOf(false) }
    var retryGeneration by remember(type, localPlaylistId) { mutableIntStateOf(0) }
    val defaultPlaylistSort = if (localPlaylistId != null) Settings.shared.playlistSongSort else Settings.shared.likedSongSort
    val defaultPlaylistDirection =
        if (localPlaylistId != null) Settings.shared.playlistSongSortDirection else Settings.shared.likedSongSortDirection
    val playlistSorting =
        Settings.shared.playlistSongSorting(
            playlistSortingKey,
            defaultPlaylistSort,
            defaultPlaylistDirection,
        )
    val playlistSort = playlistSorting.sort
    val playlistSortDirection = playlistSorting.direction
    var playlistReorderMode by remember(type, localPlaylistId) { mutableStateOf(false) }
    val selection = rememberSelectionState(localPlaylistId ?: type)
    LaunchedEffect(downloadFilterState) { selection.exit() }
    var selectionQuery by remember(localPlaylistId ?: type) { mutableStateOf("") }
    var confirmRemoveSelected by remember(type) { mutableStateOf(false) }
    val playbackState by remember(playbackController) {
        playbackController.listState
    }.collectAsState(playbackController.state.value.listState())
    val blockedVersion by BlockedContent.version.collectAsState()
    val blocked = remember(blockedVersion) { BlockedContent.snapshot() }

    LaunchedEffect(type, localPlaylistId, database, retryGeneration, topPeriod.takeIf { isTopCollection }) {
        isLoading = true
        loadError = false
        if (type == "uploaded" && localPlaylistId == null) {
            launch {
                try {
                    withContext(Dispatchers.IO) {
                        database.refreshUploadedSongs(GlobalYouTubeRepository.instance, force = retryGeneration > 0)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    loadError = true
                } finally {
                    isLoading = false
                }
            }
            database.dao().uploadedSongsByNameAsc().collect { stored ->
                loadedSongs = stored.map { it.toSongItem() }
                if (stored.isNotEmpty()) isLoading = false
            }
            return@LaunchedEffect
        }
        loadedSongs =
            try {
                val loaded =
                    if (localPlaylistId != null) {
                        database.refreshHistoryPlaylist(localPlaylistId)
                        database
                            .dao()
                            .playlistSongs(localPlaylistId)
                            .collect { entries ->
                                playlistReorder.update(entries.map { it.toSongItem() to it.map })
                                isLoading = false
                            }
                        return@LaunchedEffect
                    } else {
                        when (type) {
                            "liked" -> {
                                SongLikeState.likedSongsFlow.collect {
                                    loadedSongs = it
                                    isLoading = false
                                }
                                return@LaunchedEffect
                            }

                            "downloaded" -> {
                                database.dao().cachedAudioByKind(KIND_DOWNLOAD).collect { entries ->
                                    collectionEntries = entries
                                    loadedSongs =
                                        entries.filter(::isAvailableDownload).map { entry ->
                                            SongItem(
                                                id = entry.songId,
                                                title = entry.title,
                                                thumbnail = entry.thumbnailUrl,
                                                artists =
                                                    entry.artist.split(",").mapNotNull { name ->
                                                        name.trim().takeIf { it.isNotBlank() && it != "Unknown" }?.let {
                                                            ArtistTiny(id = null, name = it)
                                                        }
                                                    },
                                                album = null,
                                                duration = null,
                                            )
                                        }
                                    isLoading = false
                                }
                                return@LaunchedEffect
                            }

                            "top100" -> {
                                database
                                    .mostPlayedHistorySongs(
                                        sinceMs = topPeriod.fromTimestamp(Clock.System.now().toEpochMilliseconds()),
                                    ).collect { songs ->
                                        loadedSongs = songs
                                        isLoading = false
                                    }
                                return@LaunchedEffect
                            }

                            "cached" -> {
                                database.dao().cachedAudioByKind(KIND_CACHE).collect { entries ->
                                    collectionEntries = entries
                                    loadedSongs =
                                        entries.map { entry ->
                                            SongItem(
                                                id = entry.songId,
                                                title = entry.title,
                                                thumbnail = entry.thumbnailUrl,
                                                artists =
                                                    entry.artist.split(",").mapNotNull { name ->
                                                        name.trim().takeIf { it.isNotBlank() && it != "Unknown" }?.let {
                                                            ArtistTiny(id = null, name = it)
                                                        }
                                                    },
                                                album = null,
                                                duration = null,
                                            )
                                        }
                                    isLoading = false
                                }
                                return@LaunchedEffect
                            }

                            "podcasts" -> {
                                val remoteEpisodes =
                                    runCatching {
                                        GlobalYouTubeRepository.instance.getNewPodcastEpisodes()
                                    }.getOrDefault(emptyList())
                                if (remoteEpisodes.isNotEmpty()) {
                                    remoteEpisodes
                                } else {
                                    database
                                        .dao()
                                        .savedPodcastEpisodesByCreateDateAsc()
                                        .first()
                                        .map { it.toSongItem() }
                                }
                            }

                            "SE" -> {
                                val remoteEpisodes =
                                    runCatching {
                                        GlobalYouTubeRepository.instance.getSavedEpisodes(limit = null)
                                    }.getOrDefault(emptyList())
                                if (remoteEpisodes.isNotEmpty()) {
                                    remoteEpisodes.map { it.episodeToSongItem() }
                                } else {
                                    database
                                        .dao()
                                        .savedPodcastEpisodesByCreateDateAsc()
                                        .first()
                                        .map { it.toSongItem() }
                                }
                            }

                            else -> {
                                emptyList()
                            }
                        }
                    }
                loaded
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                loadError = true
                emptyList()
            }
        isLoading = false
    }

    val likedSongRecency = SongLikeState.likedSongRecency
    val playlistEntries = remember(playlistReorder.entries, blocked) { playlistReorder.entries.filterNot { blocked.isBlocked(it.first) } }
    val baseSongs =
        remember(localPlaylistId, playlistEntries, loadedSongs, blocked) {
            if (localPlaylistId != null) playlistEntries.map { it.first } else loadedSongs.filterNot(blocked::isBlocked)
        }
    val songs =
        remember(
            baseSongs,
            likedSongRecency,
            isLikedCollection,
            isTopCollection,
            topDirection,
            playlistSort,
            playlistSortDirection,
            playlistReorderMode,
            isStoredCollection,
            metadata,
            downloadFilterState,
            collectionQuery,
            collectionSort,
            collectionDirection,
        ) {
            if (isStoredCollection) {
                (
                    downloadFilterState?.apply(
                        baseSongs,
                    ) ?: baseSongs
                ).presentCollection(collectionQuery, collectionSort, collectionDirection) {
                    metadata[it.id]
                }
            } else if (isTopCollection) {
                baseSongs.sortedForTopPlaylist(topDirection)
            } else if (localPlaylistId != null && !playlistReorderMode) {
                baseSongs.sortedForPlaylist(playlistSort, playlistSortDirection)
            } else if (isLikedCollection && !playlistReorderMode) {
                baseSongs.sortedForLikedPlaylist(playlistSort, playlistSortDirection, likedSongRecency)
            } else {
                baseSongs
            }
        }
    val errorText = stringResource(Res.string.error)
    val retryText = stringResource(Res.string.retry)
    val builtin = BuiltinPlaylist.fromId(localPlaylistId ?: "auto:$type")
    val resolvedTitle = builtin?.let { stringResource(it.title) } ?: title

    when {
        isLoading -> {
            LoadingScreen(modifier.fillMaxSize())
        }

        loadError && loadedSongs.isEmpty() -> {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(errorText)
                Button(onClick = { retryGeneration++ }, modifier = Modifier.padding(top = 12.dp), shapes = ButtonDefaults.shapes()) {
                    Text(retryText)
                }
            }
        }

        else -> {
            val customCover =
                if (localPlaylistId ==
                    null
                ) {
                    Settings.shared.collectionCovers[type]
                } else {
                    storedPlaylist?.playlist?.customThumbnailUrl
                }
            PopupVisibility(visible = showAddSong && localPlaylistId != null) {
                ModalBottomSheet(onDismissRequest = { showAddSong = false }) {
                    AddSongSheetContent(
                        existingSongIds = baseSongs.mapTo(mutableSetOf()) { it.id },
                        onDismiss = { showAddSong = false },
                        onAddSong = { song, allowDuplicate ->
                            scope.launch {
                                addSongToStoredPlaylist(
                                    database,
                                    GlobalYouTubeRepository.instance,
                                    checkNotNull(localPlaylistId),
                                    song,
                                    allowDuplicate,
                                ).onFailure { onSwipeFeedback(errorText) }
                            }
                        },
                    )
                }
            }
            val playlist =
                remember(type, localPlaylistId, resolvedTitle, baseSongs, storedPlaylist, customCover) {
                    PlaylistItem(
                        id = localPlaylistId ?: type,
                        title = if (builtin != null) resolvedTitle else storedPlaylist?.title ?: resolvedTitle,
                        thumbnail = customCover ?: storedPlaylist?.thumbnailUrl ?: baseSongs.firstOrNull()?.thumbnail,
                        customThumbnail = customCover,
                        author = null,
                        songCount = "${baseSongs.size}",
                        isEditable = storedPlaylist?.playlist?.isEditable == true,
                        isLocal = localPlaylistId != null,
                    )
                }
            val fallbackIcon =
                when (type) {
                    "liked" -> MuSicXIcons.Filled.Favorite
                    "downloaded" -> MuSicXIcons.Outlined.Download
                    "top100" -> MuSicXIcons.AutoMirrored.Outlined.TrendingUp
                    "cached" -> MuSicXIcons.Outlined.Cached
                    "uploaded" -> MuSicXIcons.Outlined.Backup
                    "podcasts" -> MuSicXIcons.Filled.Podcasts
                    "SE" -> MuSicXIcons.AutoMirrored.Outlined.QueueMusic
                    else -> MuSicXIcons.AutoMirrored.Rounded.QueueMusic
                }

            val metadataRevision by SongMetadataState.revision.collectAsState()
            val displayedSongs = remember(songs, selectionQuery, metadataRevision) { songs.filterForPlaylistSearch(selectionQuery) }

            fun key(song: SongItem) =
                if (isLikedCollection) {
                    song.id
                } else {
                    song.playlistOccurrenceKey(
                        songs
                            .indexOfFirst {
                                it === song
                            }.coerceAtLeast(0),
                    )
                }
            val displayedRows =
                remember(songs, displayedSongs, isLikedCollection) {
                    val visible = displayedSongs.toSet()
                    songs.mapIndexedNotNull { index, song ->
                        if (song in visible) song to (if (isLikedCollection) song.id else song.playlistOccurrenceKey(index)) else null
                    }
                }
            val selectedSongs =
                remember(displayedRows, selection.active, selection.selectedIds) {
                    if (selection.active) displayedRows.filter { selection.isSelected(it.second) }.map { it.first } else emptyList()
                }
            val displayedKeys = remember(displayedRows) { displayedRows.mapTo(mutableSetOf()) { it.second } }

            fun openSongMenu(song: SongItem) {
                val mapping = playlistEntries.firstOrNull { it.first === song }?.second
                onSongMenu(
                    song,
                    mapping?.let { selected ->
                        {
                            scope.launch { removeSongFromLocalPlaylist(database, selected).onFailure { loadError = true } }
                        }
                    },
                    null,
                )
            }
            PlaylistContent(
                isTrackSelected = { selection.isSelected(key(it)) },
                trackContainer = { song, _, content ->
                    SongSwipeActions(song, onSwipeFeedback, enabled = !playlistReorderMode, content = content)
                },
                playlist = playlist,
                songs = displayedSongs,
                artworkSongs = baseSongs,
                onAddSong = if (localPlaylistId != null) ({ showAddSong = true }) else null,
                searchQuery = selectionQuery,
                onSearchQueryChange = { selectionQuery = it },
                text = playlistContentText(useTrackCount = localPlaylistId == null && type in setOf("podcasts", "SE")),
                onPlayAll = {
                    if (songs.isNotEmpty()) playbackController.play(songs.first(), songs)
                },
                onShuffle = {
                    playbackController.shuffleQueue(songs)
                },
                onMoreOptions =
                    when {
                        localPlaylistId != null -> {
                            { onMoreOptions(playlist.copy(isLocal = true)) }
                        }

                        else -> {
                            { onMoreOptions(playlist.copy(id = "auto:$type")) }
                        }
                    },
                onTrackClick = { song, index ->
                    if (selection.active) {
                        selection.toggle(displayedRows[index].second)
                    } else {
                        val target = songs.indexOfPlaylistOccurrence(song)
                        if (target >= 0) playbackController.playQueue(songs, target)
                    }
                },
                onTrackLongClick = { song -> selection.begin(key(song)) },
                selectionActive = selection.active,
                selectionControls = {
                    SongSelectionControls(
                        selectedCount = selectedSongs.size,
                        selectedSongs = selectedSongs,
                        onPlay = { selectedSongs.firstOrNull()?.let { playbackController.play(it, selectedSongs) } },
                        onShuffle = { playbackController.shuffleQueue(selectedSongs) },
                        allSelected = selection.allSelected(displayedKeys),
                        onDone = selection::exit,
                        onSelectAll = { selection.toggleAll(displayedKeys) },
                        onPlayNext = { playbackController.playNext(selectedSongs) },
                        onAddToQueue = { playbackController.addToQueue(selectedSongs) },
                        onDownload = { onDownload(selectedSongs) },
                        onAddToPlaylist = { onAddToPlaylist(selectedSongs) },
                        onRemove =
                            when {
                                isLikedCollection -> {
                                    { SongLikeState.removeLikes(selectedSongs.map { it.id }) }
                                }

                                localPlaylistId != null -> {
                                    {
                                        val keys = selectedSongs.mapTo(mutableSetOf(), ::key)
                                        val mappings = playlistEntries.filter { key(it.first) in keys }.map { it.second }
                                        selection.exit()
                                        scope.launch {
                                            for (mapping in mappings) {
                                                if (removeSongFromLocalPlaylist(database, mapping).isFailure) {
                                                    loadError = true
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }

                                type == "downloaded" -> {
                                    { confirmRemoveSelected = true }
                                }

                                else -> {
                                    null
                                }
                            },
                        removeLabel =
                            if (type == "downloaded") {
                                stringResource(Res.string.remove_selected_count, selectedSongs.size)
                            } else {
                                null
                            },
                    )
                },
                trackTrailingContent = { song, index ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selection.active) {
                            val occurrenceKey = displayedRows[index].second
                            Checkbox(checked = selection.isSelected(occurrenceKey), onCheckedChange = { selection.toggle(occurrenceKey) })
                        } else {
                            IconButton(
                                modifier = Modifier.navigationPopupSource(),
                                onClick = { openSongMenu(song) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(MuSicXIcons.Default.MoreVert, stringResource(Res.string.more_options))
                            }
                        }
                    }
                },
                onTrackMenuClick = { song, _ -> openSongMenu(song) },
                reorderMode = playlistReorderMode,
                onReorder = { fromIndex, toIndex ->
                    val from = displayedSongs.getOrNull(fromIndex)
                    val to = displayedSongs.getOrNull(toIndex)
                    if (from != null && to != null) {
                        if (localPlaylistId != null) {
                            val fromMappingId = playlistEntries.firstOrNull { it.first === from }?.second?.id
                            val toMappingId = playlistEntries.firstOrNull { it.first === to }?.second?.id
                            if (fromMappingId != null && toMappingId != null) {
                                playlistReorder.move(
                                    fromMappingId,
                                    toMappingId,
                                    selectedSongs.mapNotNullTo(mutableSetOf()) { it.playlistLocalMappingId },
                                )
                            }
                        } else if (isLikedCollection) {
                            SongLikeState.moveLike(from.id, to.id, selectedSongs.mapTo(mutableSetOf()) { it.id })
                        }
                    }
                },
                onReorderFinished = { playlistReorder.commit { onSwipeFeedback(errorText) } },
                aboveOrderControls =
                    if (isStoredCollection && downloadFilter != null) {
                        { DownloadPlaylistFilterBar(downloadFilter, database) }
                    } else {
                        null
                    },
                orderControls =
                    if (isStoredCollection) {
                        {
                            Column {
                                CollectionFilterBar(
                                    showSearch = false,
                                    text = collectionFilterBarText(),
                                    query = collectionQuery,
                                    onQueryChange = { collectionQuery = it },
                                    sort = collectionSort,
                                    onSortChange = {
                                        Settings.shared.setPlaylistCollectionSorting(
                                            playlistSortingKey,
                                            SavedPlaylistCollectionSorting(it, it.defaultDirection),
                                        )
                                    },
                                    sortDirection = collectionDirection,
                                    onSortDirectionChange = {
                                        Settings.shared.setPlaylistCollectionSorting(
                                            playlistSortingKey,
                                            SavedPlaylistCollectionSorting(collectionSort, it),
                                        )
                                    },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    } else if (isTopCollection) {
                        {
                            TopPlaylistOrderControls(
                                text = topPlaylistOrderControlsText(),
                                period = topPeriod,
                                sortDirection = topDirection,
                                onPeriodChange = { topPeriod = it },
                                onSortDirectionChange = { topDirection = it },
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else if (isLikedCollection || localPlaylistId != null) {
                        {
                            PlaylistOrderControls(
                                text = playlistOrderControlsText(),
                                sort = playlistSort,
                                sortDirection = playlistSortDirection,
                                reorderMode = playlistReorderMode,
                                canReorder = true,
                                onSortChange = {
                                    Settings.shared.setPlaylistSongSorting(
                                        playlistSortingKey,
                                        SavedPlaylistSongSorting(it, playlistSortDirection),
                                    )
                                },
                                onSortDirectionChange = {
                                    Settings.shared.setPlaylistSongSorting(
                                        playlistSortingKey,
                                        SavedPlaylistSongSorting(playlistSort, it),
                                    )
                                },
                                onReorderModeChange = {
                                    playlistReorderMode = it
                                },
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        null
                    },
                onBackClick = onBack,
                fallbackIcon = fallbackIcon,
                cover = if (localPlaylistId == null && customCover == null) libraryCover(type) else null,
                currentSongId = playbackState.currentSongId,
                isPlaying = playbackState.isPlaying,
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                modifier = modifier.fillMaxSize(),
            )
            PopupVisibility(visible = confirmRemoveSelected) {
                AlertDialog(
                    onDismissRequest = { confirmRemoveSelected = false },
                    title = {
                        Text(
                            pluralStringResource(Res.plurals.stored_remove_selected_title, selectedSongs.size, selectedSongs.size),
                        )
                    },
                    text = { Text(stringResource(Res.string.stored_remove_selected_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmRemoveSelected = false
                                onRemoveDownloads(selectedSongs.map(SongItem::id))
                                selection.exit()
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { confirmRemoveSelected = false },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(Res.string.cancel)) }
                    },
                )
            }
        }
    }
}
