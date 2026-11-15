/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package dev.musicx.android.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.musicx.android.LocalMiniPlayerBottomInset
import dev.musicx.android.LocalPlaylistAdder
import dev.musicx.android.playback.AudioCacheManager
import dev.musicx.android.playback.LocalPlayerConnection
import dev.musicx.android.playback.SongDownloadManager
import dev.musicx.android.ui.component.AdaptiveModalBottomSheet
import dev.musicx.android.ui.component.SwipeGestureHandler
import dev.musicx.android.ui.components.CollectionCoverEditor
import dev.musicx.android.ui.components.SongMetadataRow
import dev.musicx.android.ui.download.rememberDownloadPermissionGate
import dev.musicx.android.ui.menu.CollectionExportDialog
import dev.musicx.android.ui.menu.PlaylistMenu
import dev.musicx.android.ui.menu.SongMenu
import dev.musicx.android.viewmodels.AutoPlaylistPresentationInput
import dev.musicx.android.viewmodels.prepareAutoPlaylistSongs
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.db.collectionPlayCounts
import dev.musicx.shared.db.entities.CachedAudioEntity
import dev.musicx.shared.db.entities.KIND_CACHE
import dev.musicx.shared.db.entities.KIND_DOWNLOAD
import dev.musicx.shared.db.entities.toSongItem
import dev.musicx.shared.db.entities.withStoredDownloadMetadata
import dev.musicx.shared.db.refreshUploadedSongs
import dev.musicx.shared.db.synchronizeUploadedSongs
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.back
import dev.musicx.shared.generated.resources.cached_playlist
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.delete
import dev.musicx.shared.generated.resources.edit_playlist_cover
import dev.musicx.shared.generated.resources.episodes_for_later
import dev.musicx.shared.generated.resources.error_unknown
import dev.musicx.shared.generated.resources.filter_downloaded
import dev.musicx.shared.generated.resources.liked_songs
import dev.musicx.shared.generated.resources.more
import dev.musicx.shared.generated.resources.more_actions
import dev.musicx.shared.generated.resources.my_top_100
import dev.musicx.shared.generated.resources.podcasts
import dev.musicx.shared.generated.resources.remove
import dev.musicx.shared.generated.resources.remove_all
import dev.musicx.shared.generated.resources.remove_from_device
import dev.musicx.shared.generated.resources.remove_selected_count
import dev.musicx.shared.generated.resources.search_no_results
import dev.musicx.shared.generated.resources.songs
import dev.musicx.shared.generated.resources.stored_clear_cache_title
import dev.musicx.shared.generated.resources.stored_clear_collection
import dev.musicx.shared.generated.resources.stored_remove_all_downloads_title
import dev.musicx.shared.generated.resources.stored_remove_all_message
import dev.musicx.shared.generated.resources.stored_remove_selected_message
import dev.musicx.shared.generated.resources.stored_remove_selected_title
import dev.musicx.shared.generated.resources.upload
import dev.musicx.shared.generated.resources.upload_failed
import dev.musicx.shared.generated.resources.uploaded_playlist_name
import dev.musicx.shared.generated.resources.uploaded_song_to_youtube_music
import dev.musicx.shared.generated.resources.uploading
import dev.musicx.shared.model.AlbumTiny
import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.BuiltinPlaylist
import dev.musicx.shared.model.CollectionSort
import dev.musicx.shared.model.PlaylistItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.SortDirection
import dev.musicx.shared.model.TopPlaylistPeriod
import dev.musicx.shared.model.defaultDirection
import dev.musicx.shared.model.indexOfPlaylistOccurrence
import dev.musicx.shared.model.toSongItem
import dev.musicx.shared.settings.BlockedContent
import dev.musicx.shared.settings.HiddenLibraryItem
import dev.musicx.shared.settings.HiddenLibraryItemType
import dev.musicx.shared.settings.HiddenLibraryItems
import dev.musicx.shared.settings.SavedPlaylistCollectionSorting
import dev.musicx.shared.settings.SavedPlaylistSongSorting
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.settings.displayName
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.state.SongLikeState
import dev.musicx.shared.state.SongMetadataState
import dev.musicx.shared.ui.components.AlertDialog
import dev.musicx.shared.ui.components.CollectionFilterBar
import dev.musicx.shared.ui.components.CollectionFilterBarText
import dev.musicx.shared.ui.components.ContainedLoadingIndicator
import dev.musicx.shared.ui.components.PlaylistOrderControls
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.components.PopupVisibility
import dev.musicx.shared.ui.components.RefreshIndicator
import dev.musicx.shared.ui.components.SongSelectionControls
import dev.musicx.shared.ui.components.TopPlaylistOrderControls
import dev.musicx.shared.ui.components.images.AsyncImage
import dev.musicx.shared.ui.components.libraryCover
import dev.musicx.shared.ui.components.rememberDownloadPlaylistFilter
import dev.musicx.shared.ui.components.topPlaylistOrderControlsText
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.localizedErrorMessage
import dev.musicx.shared.ui.navigation.navigationPopupSource
import dev.musicx.shared.ui.screens.PlaylistContent
import dev.musicx.shared.ui.screens.collectionFilterBarText
import dev.musicx.shared.ui.screens.playlistContentText
import dev.musicx.shared.ui.screens.playlistOrderControlsText
import dev.musicx.shared.ui.selection.rememberSelectionState
import dev.musicx.shared.ui.settings.StorageConfirmationDialog
import dev.musicx.shared.ui.tv.tvFocusHighlight
import com.valentinilk.shimmer.shimmer
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
    type: String,
    onBackClick: () -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null,
    onOpenDownloadQueue: () -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current
    val database: MusicDatabase = koinInject()
    val audioCacheManager: AudioCacheManager = koinInject()
    val songDownloadManager: SongDownloadManager = koinInject()
    val requireDownloadNotifications = rememberDownloadPermissionGate()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val miniPlayerInset = LocalMiniPlayerBottomInset.current
    val currentSong by playerConnection?.currentSong?.collectAsState(initial = null)
        ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState()
        ?: remember { mutableStateOf(false) }

    val currentType = type
    var editingCover by remember { mutableStateOf<String?>(null) }
    editingCover?.let { CollectionCoverEditor(it, onDismiss = { editingCover = null }) }
    val collectionCovers = Settings.shared.collectionCovers

    val playlistTitles =
        mapOf(
            "liked" to stringResource(Res.string.liked_songs),
            "downloaded" to stringResource(Res.string.filter_downloaded),
            "top100" to stringResource(Res.string.my_top_100),
            "cached" to stringResource(Res.string.cached_playlist),
            "uploaded" to stringResource(Res.string.uploaded_playlist_name),
            "podcasts" to stringResource(Res.string.podcasts),
            "SE" to stringResource(Res.string.episodes_for_later),
        )
    val songsTitle = stringResource(Res.string.songs)

    fun autoPlaylistTitle(type: String) = playlistTitles[type] ?: songsTitle

    var songs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var collectionEntries by remember { mutableStateOf<List<CachedAudioEntity>>(emptyList()) }
    var collectionQuery by remember { mutableStateOf("") }
    var collectionDirection by remember(currentType) {
        mutableStateOf(
            Settings.shared
                .playlistCollectionSorting(
                    "special:$currentType",
                    if (currentType == "downloaded") Settings.shared.downloadedCollectionSort else Settings.shared.cachedCollectionSort,
                    if (currentType == "downloaded") {
                        Settings.shared.downloadedCollectionSortDirection
                    } else {
                        Settings.shared.cachedCollectionSortDirection
                    },
                ).direction,
        )
    }
    var collectionSort by remember(currentType) {
        mutableStateOf(
            Settings.shared
                .playlistCollectionSorting(
                    "special:$currentType",
                    if (currentType == "downloaded") Settings.shared.downloadedCollectionSort else Settings.shared.cachedCollectionSort,
                    if (currentType == "downloaded") {
                        Settings.shared.downloadedCollectionSortDirection
                    } else {
                        Settings.shared.cachedCollectionSortDirection
                    },
                ).sort,
        )
    }
    val countIds =
        remember(currentType, collectionSort, collectionEntries) {
            if (currentType in setOf("downloaded", "cached") && collectionSort == CollectionSort.MOST_PLAYED) {
                collectionEntries.map { it.songId }
            } else {
                emptyList()
            }
        }
    val playCounts by remember(database, countIds) {
        if (countIds.isEmpty()) flowOf(emptyMap()) else database.collectionPlayCounts(countIds)
    }.collectAsState(emptyMap())
    var playlistSort by remember(currentType) {
        mutableStateOf(
            Settings.shared
                .playlistSongSorting(
                    "special:$currentType",
                    if (currentType == "liked") Settings.shared.likedSongSort else Settings.shared.playlistSongSort,
                    if (currentType == "liked") Settings.shared.likedSongSortDirection else Settings.shared.playlistSongSortDirection,
                ).sort,
        )
    }
    var playlistSortDirection by remember(currentType) {
        mutableStateOf(
            Settings.shared
                .playlistSongSorting(
                    "special:$currentType",
                    if (currentType == "liked") Settings.shared.likedSongSort else Settings.shared.playlistSongSort,
                    if (currentType == "liked") Settings.shared.likedSongSortDirection else Settings.shared.playlistSongSortDirection,
                ).direction,
        )
    }
    var topPeriod by remember { mutableStateOf(TopPlaylistPeriod.ALL_TIME) }
    var topDirection by remember { mutableStateOf(SortDirection.DESCENDING) }
    var likedReorderMode by remember { mutableStateOf(false) }
    val addToPlaylist = LocalPlaylistAdder.current
    val selection = rememberSelectionState(currentType)
    val downloadFilter = rememberDownloadPlaylistFilter(database)
    val downloadFilterState by downloadFilter.state.collectAsState()
    LaunchedEffect(currentType) { downloadFilter.select(null) }
    LaunchedEffect(downloadFilterState) { selection.exit() }
    var pendingRemovalIds by remember(currentType) { mutableStateOf<Set<String>?>(null) }
    var selectedMenuSong by remember { mutableStateOf<SongItem?>(null) }
    val downloadedSongIds by remember(
        database,
    ) {
        database
            .dao()
            .cachedAudioIdsByKind(KIND_DOWNLOAD)
            .map { it.toSet() }
            .distinctUntilChanged()
    }.collectAsState(initial = emptySet())

    LaunchedEffect(currentType) {
        if (currentType == "liked") {
            SongLikeState.likedSongsFlow.collect { likes ->
                songs = BlockedContent.filterSongs(likes)
            }
        }
    }

    val isStoredCollection = currentType == "downloaded" || currentType == "cached"
    val isUploadedCollection = currentType == "uploaded"
    val isLikedCollection = currentType == "liked"
    val isTopCollection = currentType == "top100"
    val metadataRevision by SongMetadataState.revision.collectAsState()

    fun presentationInput() =
        AutoPlaylistPresentationInput(
            type = currentType,
            songs = songs,
            metadataRevision = metadataRevision,
            collectionEntries = collectionEntries,
            query = collectionQuery,
            collectionSort = collectionSort,
            collectionDirection = collectionDirection,
            playCounts = playCounts,
            playlistSort = playlistSort,
            playlistDirection = playlistSortDirection,
            topDirection = topDirection,
            reorder = likedReorderMode,
            likedSongRecency = if (isLikedCollection) SongLikeState.likedSongRecency else emptyList(),
            downloadFilter = downloadFilterState,
        )

    var preparedSongs by remember(currentType) { mutableStateOf<List<SongItem>>(emptyList()) }
    var preparedDownloadFilter by remember(currentType) { mutableStateOf(downloadFilterState) }
    LaunchedEffect(currentType) {
        snapshotFlow { presentationInput() }.collectLatest { input ->
            val prepared = prepareAutoPlaylistSongs(input)
            if (presentationInput() == input) {
                preparedSongs = prepared
                preparedDownloadFilter = input.downloadFilter
            }
        }
    }
    val displayedSongs =
        if (currentType == "downloaded" &&
            (downloadFilterState.loading || downloadFilterState.failed || preparedDownloadFilter != downloadFilterState)
        ) {
            emptyList()
        } else if (isLikedCollection && likedReorderMode && collectionQuery.isBlank()) {
            songs
        } else {
            preparedSongs
        }
    val selectedSongs =
        remember(displayedSongs, selection.selectedIds) {
            if (selection.selectedIds.isEmpty()) emptyList() else displayedSongs.filter { selection.isSelected(it.id) }
        }
    val displayedSongIds = remember(displayedSongs) { displayedSongs.mapTo(mutableSetOf()) { it.id } }

    LaunchedEffect(songs) {
        selection.retain(songs.map { it.id })
    }

    suspend fun loadUploadedSongs() {
        try {
            database.refreshUploadedSongs(GlobalYouTubeRepository.instance)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Toast.makeText(context, getString(Res.string.upload_failed), Toast.LENGTH_LONG).show()
        }
    }

    val uploadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null || isUploading) return@rememberLauncherForActivityResult
            scope.launch {
                isUploading = true
                try {
                    val result =
                        withContext(Dispatchers.IO) {
                            val fileName = context.displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
                            val contentLength = context.contentLength(uri) ?: 0L
                            GlobalYouTubeRepository.instance.uploadSong(fileName, contentLength) {
                                requireNotNull(context.contentResolver.openInputStream(uri)).toByteReadChannel(Dispatchers.IO)
                            }
                        }
                    if (result.success) {
                        Toast.makeText(context, getString(Res.string.uploaded_song_to_youtube_music), Toast.LENGTH_SHORT).show()
                        if (currentType == "uploaded") {
                            val knownIds = songs.mapTo(mutableSetOf()) { it.id }
                            scope.launch {
                                GlobalYouTubeRepository.instance.awaitProcessedUpload(knownIds) { processed ->
                                    if (currentType == "uploaded") {
                                        scope.launch { database.synchronizeUploadedSongs(processed) }
                                    }
                                }
                            }
                        }
                    } else {
                        Toast
                            .makeText(
                                context,
                                result.localizedErrorMessage(),
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, getString(Res.string.upload_failed), Toast.LENGTH_LONG).show()
                } finally {
                    isUploading = false
                }
            }
        }

    LaunchedEffect(currentType, topPeriod.takeIf { currentType == "top100" }) {
        isLoading = true
        selection.exit()
        collectionQuery = ""
        collectionEntries = emptyList()
        likedReorderMode = false
        when (currentType) {
            "liked" -> {
                songs = BlockedContent.filterSongs(SongLikeState.likedSongs)
                isLoading = false

                if (GlobalYouTubeRepository.instance.isYouTubeHistoryAvailable()) {
                    SongLikeState.refreshLikesFromRemote(force = false)
                }
            }

            "downloaded" -> {
                database.dao().cachedAudioByKind(KIND_DOWNLOAD).collectLatest { downloadedSongs ->
                    val storedSongs =
                        withContext(Dispatchers.IO) {
                            database.dao().getSongsByIds(downloadedSongs.map { it.songId }).associateBy { it.id }
                        }
                    collectionEntries = downloadedSongs
                    songs =
                        withContext(Dispatchers.Default) {
                            BlockedContent.filterSongs(
                                downloadedSongs.map { entry ->
                                    SongItem(
                                        id = entry.songId,
                                        title = entry.title,
                                        thumbnail = entry.thumbnailUrl,
                                        artists =
                                            entry.artist.split(",").mapNotNull { name ->
                                                name.trim().takeIf { it.isNotBlank() && it != "Unknown" }?.let {
                                                    ArtistTiny(
                                                        id = null,
                                                        name = it,
                                                    )
                                                }
                                            },
                                        album = entry.album?.let { AlbumTiny(id = null, name = it) },
                                    ).withStoredDownloadMetadata(storedSongs[entry.songId])
                                },
                            )
                        }
                    isLoading = false
                }
                return@LaunchedEffect
            }

            "cached" -> {
                database.dao().cachedAudioByKind(KIND_CACHE).collectLatest { cachedSongs ->
                    collectionEntries = cachedSongs
                    songs =
                        withContext(Dispatchers.Default) {
                            BlockedContent.filterSongs(
                                cachedSongs.map { entry ->
                                    SongItem(
                                        id = entry.songId,
                                        title = entry.title,
                                        thumbnail = entry.thumbnailUrl,
                                        artists =
                                            entry.artist.split(",").mapNotNull { name ->
                                                name.trim().takeIf { it.isNotBlank() && it != "Unknown" }?.let {
                                                    ArtistTiny(
                                                        id = null,
                                                        name = it,
                                                    )
                                                }
                                            },
                                        album = entry.album?.let { AlbumTiny(id = null, name = it) },
                                    )
                                },
                            )
                        }
                    isLoading = false
                }
                return@LaunchedEffect
            }

            "top100" -> {
                songs =
                    database
                        .dao()
                        .mostPlayedSongs(topPeriod.fromTimestamp(Clock.System.now().toEpochMilliseconds()), limit = 100)
                        .first()
                        .map { it.toSongItem() }
            }

            "uploaded" -> {
                launch {
                    try {
                        loadUploadedSongs()
                    } finally {
                        isLoading = false
                    }
                }
                database.dao().uploadedSongsByNameAsc().collect { stored ->
                    songs = stored.map { it.toSongItem() }
                    if (stored.isNotEmpty()) isLoading = false
                }
                return@LaunchedEffect
            }

            "podcasts" -> {
                val remoteEpisodes =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            GlobalYouTubeRepository.instance.getNewPodcastEpisodes()
                        }
                    }.getOrDefault(emptyList())
                songs =
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
                        withContext(Dispatchers.IO) {
                            GlobalYouTubeRepository.instance.getSavedEpisodes(limit = null)
                        }
                    }.getOrDefault(emptyList())
                songs =
                    if (remoteEpisodes.isNotEmpty()) {
                        remoteEpisodes.map { it.toSongItem() }
                    } else {
                        database
                            .dao()
                            .savedPodcastEpisodesByCreateDateAsc()
                            .first()
                            .map { it.toSongItem() }
                    }
            }
        }
        songs = BlockedContent.filterSongs(songs)
        isLoading = false
    }

    val selectionBar: @Composable () -> Unit = {
        SongSelectionControls(
            selectedCount = selectedSongs.size,
            selectedSongs = selectedSongs,
            onPlay = { playerConnection?.playQueue(selectedSongs, 0, autoPlaylistTitle(currentType), finite = true) },
            onShuffle = { playerConnection?.shuffleQueue(selectedSongs, autoPlaylistTitle(currentType), finite = true) },
            allSelected = selection.allSelected(displayedSongIds),
            onDone = selection::exit,
            onSelectAll = { selection.toggleAll(displayedSongIds) },
            onPlayNext = { playerConnection?.addToQueue(selectedSongs, playNext = true) },
            onAddToQueue = { playerConnection?.addToQueue(selectedSongs) },
            onDownload = {
                requireDownloadNotifications {
                    scope.launch {
                        songDownloadManager.downloadAll(
                            selectedSongs,
                        )
                    }
                }
            },
            onAddToPlaylist = { addToPlaylist(selectedSongs) },
            onRemove =
                when {
                    isLikedCollection -> {
                        { SongLikeState.removeLikes(selectedSongs.map { it.id }) }
                    }

                    currentType == "downloaded" || currentType == "cached" -> {
                        { pendingRemovalIds = selectedSongs.mapTo(mutableSetOf()) { it.id } }
                    }

                    else -> {
                        null
                    }
                },
            removeLabel =
                if (currentType == "downloaded" || currentType == "cached") {
                    stringResource(Res.string.remove_selected_count, selectedSongs.size)
                } else {
                    null
                },
        )
    }

    var userRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val uploadingDescription = stringResource(Res.string.uploading)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selection.active) {
                selectionBar()
            } else {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.tvFocusHighlight(CircleShape),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(MuSicXIcons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { editingCover = currentType },
                            modifier = Modifier.tvFocusHighlight(CircleShape),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(MuSicXIcons.Outlined.Edit, contentDescription = stringResource(Res.string.edit_playlist_cover))
                        }
                        val action =
                            when (currentType) {
                                "liked" -> ({ showCollectionMenu = true })
                                "downloaded", "cached" -> ({ showClearConfirmation = true })
                                "uploaded" -> ({ uploadLauncher.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream")) })
                                else -> null
                            }
                        if (currentType != "liked") {
                            IconButton(
                                onClick = { showCollectionMenu = true },
                                modifier = Modifier.tvFocusHighlight(CircleShape),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(MuSicXIcons.Default.MoreVert, stringResource(Res.string.more_actions))
                            }
                        }
                        if (action != null) {
                            IconButton(
                                modifier = Modifier.tvFocusHighlight(CircleShape).navigationPopupSource(),
                                onClick = action,
                                enabled = currentType != "uploaded" || !isUploading,
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                if (currentType == "uploaded" && isUploading) {
                                    LoadingIndicator(
                                        modifier =
                                            Modifier
                                                .size(24.dp)
                                                .semantics { contentDescription = uploadingDescription },
                                    )
                                } else {
                                    Icon(
                                        imageVector =
                                            when (currentType) {
                                                "downloaded", "cached" -> MuSicXIcons.Outlined.Delete
                                                "uploaded" -> MuSicXIcons.Outlined.CloudUpload
                                                else -> MuSicXIcons.Default.MoreVert
                                            },
                                        contentDescription =
                                            when (currentType) {
                                                "downloaded", "cached" -> stringResource(Res.string.stored_clear_collection)
                                                "uploaded" -> stringResource(Res.string.upload)
                                                else -> stringResource(Res.string.more_actions)
                                            },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = userRefreshing,
            onRefresh = {
                if (isLikedCollection && !userRefreshing) {
                    userRefreshing = true
                    scope.launch {
                        try {
                            SongLikeState.refreshLikesFromRemote(force = true)
                        } finally {
                            userRefreshing = false
                        }
                    }
                }
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
            Box(modifier = Modifier.fillMaxSize().padding(bottom = paddingValues.calculateBottomPadding())) {
                PlaylistContent(
                    selectionActive = selection.active,
                    onMoreOptions = { showCollectionMenu = true },
                    playlist =
                        PlaylistItem(
                            id = currentType,
                            title = autoPlaylistTitle(currentType),
                            thumbnail = collectionCovers[currentType],
                            customThumbnail = collectionCovers[currentType],
                        ),
                    emptyContent =
                        if (currentType == "downloaded" && downloadFilterState.playlist != null) {
                            {
                                if (!downloadFilterState.loading && !downloadFilterState.failed) {
                                    Text(stringResource(Res.string.search_no_results), modifier = Modifier.padding(16.dp))
                                }
                            }
                        } else {
                            null
                        },
                    songs = displayedSongs,
                    artworkSongs = songs,
                    downloadedSongIds = downloadedSongIds,
                    text = playlistContentText(useTrackCount = currentType in setOf("podcasts", "SE")),
                    onPlayAll = {
                        if (displayedSongs.isNotEmpty()) {
                            playerConnection?.playQueue(
                                displayedSongs,
                                0,
                                autoPlaylistTitle(currentType),
                                finite = true,
                            )
                        }
                    },
                    onShuffle = {
                        if (displayedSongs.isNotEmpty()) {
                            playerConnection?.shuffleQueue(displayedSongs, autoPlaylistTitle(currentType), finite = true)
                        }
                    },
                    onTrackClick = { _, index ->
                        if (selection.active) {
                            selection.toggle(displayedSongs[index].id)
                        } else {
                            val song = displayedSongs[index]
                            val input = presentationInput().copy(query = "")
                            val title = autoPlaylistTitle(currentType)
                            scope.launch {
                                val queue = prepareAutoPlaylistSongs(input)
                                val target = queue.indexOfPlaylistOccurrence(song)
                                if (target >= 0) playerConnection?.playQueue(queue, target, title, finite = true)
                            }
                        }
                    },
                    onTrackMenuClick = { song, _ -> selectedMenuSong = song },
                    onTrackLongClick = { song ->
                        if (!likedReorderMode) selection.begin(song.id)
                    },
                    trackTrailingContent = { song, _ ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selection.active) {
                                Checkbox(checked = selection.isSelected(song.id), onCheckedChange = { selection.toggle(song.id) })
                            } else {
                                IconButton(
                                    modifier = Modifier.navigationPopupSource(),
                                    onClick = { selectedMenuSong = song },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(MuSicXIcons.Default.MoreVert, stringResource(Res.string.more))
                                }
                            }
                        }
                    },
                    contentPadding =
                        PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + miniPlayerInset,
                        ),
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onArtistClick = onArtistClick,
                    reorderMode = likedReorderMode,
                    onReorder = { fromIndex, toIndex ->
                        val from = displayedSongs.getOrNull(fromIndex)
                        val to = displayedSongs.getOrNull(toIndex)
                        if (from != null && to != null) {
                            SongLikeState.moveLike(from.id, to.id, selectedSongs.mapTo(mutableSetOf()) { it.id })
                            songs = BlockedContent.filterSongs(SongLikeState.likedSongs)
                        }
                    },
                    searchQuery = collectionQuery,
                    onSearchQueryChange = { collectionQuery = it },
                    trackContainer = { song, reordering, content ->
                        SwipeGestureHandler(
                            onSwipeLeft = { playerConnection?.addToQueue(song) },
                            onSwipeRight = { playerConnection?.addToQueue(song, playNext = true) },
                            enabled = playerConnection != null && Settings.shared.swipeToSong && !reordering && !selection.active,
                            content = content,
                        )
                    },
                    cover = libraryCover(currentType).takeIf { collectionCovers[currentType] == null },
                    aboveOrderControls =
                        if (currentType == "downloaded") {
                            {
                                DownloadQueuePanel(
                                    manager = songDownloadManager,
                                    onSeeAll = onOpenDownloadQueue,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        } else {
                            null
                        },
                    orderControls = {
                        when {
                            isStoredCollection -> {
                                Column {
                                    CollectionFilterBar(
                                        text = collectionFilterBarText(),
                                        query = collectionQuery,
                                        onQueryChange = { collectionQuery = it },
                                        sort = collectionSort,
                                        onSortChange = {
                                            collectionSort = it
                                            collectionDirection = it.defaultDirection
                                            Settings.shared.setPlaylistCollectionSorting(
                                                "special:$currentType",
                                                SavedPlaylistCollectionSorting(it, it.defaultDirection),
                                            )
                                        },
                                        sortDirection = collectionDirection,
                                        onSortDirectionChange = {
                                            collectionDirection = it
                                            Settings.shared.setPlaylistCollectionSorting(
                                                "special:$currentType",
                                                SavedPlaylistCollectionSorting(collectionSort, it),
                                            )
                                        },
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        showSearch = false,
                                    )
                                }
                            }

                            isTopCollection -> {
                                TopPlaylistOrderControls(
                                    text = topPlaylistOrderControlsText(),
                                    period = topPeriod,
                                    sortDirection = topDirection,
                                    onPeriodChange = { topPeriod = it },
                                    onSortDirectionChange = { topDirection = it },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }

                            isLikedCollection || isUploadedCollection -> {
                                PlaylistOrderControls(
                                    text = playlistOrderControlsText(),
                                    sort = playlistSort,
                                    sortDirection = playlistSortDirection,
                                    reorderMode = isLikedCollection && likedReorderMode,
                                    canReorder = isLikedCollection,
                                    onSortChange = {
                                        playlistSort = it
                                        Settings.shared.setPlaylistSongSorting(
                                            "special:$currentType",
                                            SavedPlaylistSongSorting(it, playlistSortDirection),
                                        )
                                    },
                                    onSortDirectionChange = {
                                        playlistSortDirection = it
                                        Settings.shared.setPlaylistSongSorting(
                                            "special:$currentType",
                                            SavedPlaylistSongSorting(playlistSort, it),
                                        )
                                    },
                                    onReorderModeChange = {
                                        likedReorderMode = it
                                    },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    },
                )

                if (isLoading || (isLikedCollection && songs.isEmpty() && SongLikeState.isRefreshing && !userRefreshing)) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = paddingValues.calculateTopPadding()),
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            }
        }
    }

    PopupContent(selectedMenuSong) { song ->
        SongMenu(
            song = song,
            isLiked = SongLikeState.isLiked(song.id),
            onDismiss = { selectedMenuSong = null },
            onStartRadio = { playerConnection?.startRadio(song) },
            onPlayNext = { playerConnection?.addToQueue(song, playNext = true) },
            onAddToQueue = { playerConnection?.addToQueue(song) },
            onLike = { SongLikeState.toggleLike(song) },
            onRemove =
                if (currentType == "cached") {
                    {
                        scope.launch {
                            if (!audioCacheManager.removeCache(song.id, playerConnection?.playbackInstanceId() ?: 0L)) {
                                Toast.makeText(context, getString(Res.string.error_unknown), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    null
                },
            removeLabel =
                when (currentType) {
                    "cached" -> stringResource(Res.string.remove_from_device)
                    else -> null
                },
        )
    }

    if (showCollectionMenu) {
        val title = autoPlaylistTitle(currentType)
        PlaylistMenu(
            playlist =
                BuiltinPlaylist.fromId("auto:$currentType")?.playlist(title)
                    ?: PlaylistItem(id = "auto:$currentType", title = title, thumbnail = null),
            fallbackIcon = MuSicXIcons.Filled.Favorite,
            onDismiss = { showCollectionMenu = false },
            onPlay = { playerConnection?.playQueue(songs, queueTitle = title, finite = true) },
            onShuffle = { playerConnection?.shuffleQueue(songs, queueTitle = title, finite = true) },
            onPlayNext = { playerConnection?.addToQueue(songs, playNext = true) },
            onAddToQueue = { playerConnection?.addToQueue(songs) },
            onHideFromLibrary = {
                HiddenLibraryItems.hide(HiddenLibraryItem(HiddenLibraryItemType.AUTO, currentType, title))
            },
            onExport = { showExportDialog = true },
            loadSongs = { songs },
        )
    }
    PopupVisibility(visible = showExportDialog) {
        CollectionExportDialog(
            title = autoPlaylistTitle(currentType),
            onDismiss = { showExportDialog = false },
            loadSongs = { songs },
        )
    }

    PopupVisibility(visible = showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = {
                Text(
                    stringResource(
                        if (currentType ==
                            "downloaded"
                        ) {
                            Res.string.stored_remove_all_downloads_title
                        } else {
                            Res.string.stored_clear_cache_title
                        },
                    ),
                )
            },
            text = { Text(stringResource(Res.string.stored_remove_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        scope.launch {
                            val removed =
                                if (currentType == "downloaded") {
                                    songDownloadManager.clearDownloads()
                                } else {
                                    audioCacheManager.clearCache()
                                }
                            if (!removed) Toast.makeText(context, getString(Res.string.error_unknown), Toast.LENGTH_LONG).show()
                            selection.exit()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.remove_all)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmation = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    PopupContent(pendingRemovalIds) { removalIds ->
        AlertDialog(
            onDismissRequest = { pendingRemovalIds = null },
            title = {
                val count = removalIds.size
                Text(pluralStringResource(Res.plurals.stored_remove_selected_title, count, count))
            },
            text = { Text(stringResource(Res.string.stored_remove_selected_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = removalIds
                        pendingRemovalIds = null
                        scope.launch {
                            val failed =
                                ids.filterNot { id ->
                                    if (currentType == "downloaded") {
                                        songs.firstOrNull { it.id == id }?.let { songDownloadManager.removeDownload(it) } == true
                                    } else {
                                        audioCacheManager.removeCache(id, playerConnection?.playbackInstanceId() ?: 0L)
                                    }
                                }
                            if (failed.isNotEmpty()) Toast.makeText(context, getString(Res.string.error_unknown), Toast.LENGTH_LONG).show()
                            if (failed.isEmpty()) selection.exit() else selection.replaceWith(failed.toSet())
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemovalIds = null },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

private fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

private fun Context.contentLength(uri: Uri): Long? =
    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0L } else null
    }
