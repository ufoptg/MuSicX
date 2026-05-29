/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_SONG
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.SongFilter
import com.metrolist.music.constants.SongFilterKey
import com.metrolist.music.constants.SongSortDescendingKey
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.constants.SongSortTypeKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.db.entities.UploadState
import com.metrolist.music.extensions.matchesNormalizedQuery
import com.metrolist.music.extensions.normalizeForSearch
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LibrarySearchEmptyPlaceholder
import com.metrolist.music.ui.component.LibrarySearchHeader
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.utils.isScrollingUp
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LibrarySongsViewModel
import com.metrolist.music.viewmodels.UploadViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
    uploadViewModel: UploadViewModel = hiltViewModel(),
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val queueAllSongsStr = stringResource(R.string.queue_all_songs)
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            SongSortTypeKey,
            SongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val debouncedSearchQuery by viewModel.debouncedSearchQuery.collectAsStateWithLifecycle()
    val normalizedQuery = remember(debouncedSearchQuery) { debouncedSearchQuery.normalizeForSearch() }

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    val uploadJobs by uploadViewModel.jobs.collectAsStateWithLifecycle()

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                uploadViewModel.enqueue(uris)
            }
        }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                SongFilter.UPLOADED -> viewModel.syncUploadedSongs()
                else -> return@LaunchedEffect
            }
        }
    }

    val lazyListState = rememberLazyListState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val filteredSongs =
        (if (hideExplicit) {
            songs.filter { !it.song.explicit }
        } else {
            songs
        }).filter { song ->
            val artistNames = song.artists.map { it.name }.toTypedArray()
            matchesNormalizedQuery(normalizedQuery, song.song.title, song.album?.title, *artistNames)
        }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(
                key = "filter",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        label = { Text(stringResource(R.string.songs)) },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = onDeselect,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "",
                            )
                        },
                    )
                    ChipsRow(
                        chips =
                            listOf(
                                SongFilter.LIKED to stringResource(R.string.filter_liked),
                                SongFilter.LIBRARY to stringResource(R.string.filter_library),
                                SongFilter.UPLOADED to stringResource(R.string.filter_uploaded),
                                SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                            ),
                        currentValue = filter,
                        onValueUpdate = {
                            filter = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Minimal inline upload jobs list (polished UploadStatusBar lands in iter 5).
            if (uploadJobs.isNotEmpty()) {
                item(
                    key = "upload_jobs",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        uploadJobs.forEach { job ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row {
                                    Text(
                                        text = job.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = job.state.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (job.state == UploadState.RUNNING) {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { job.progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                LibrarySearchHeader(
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onBack = {
                        isSearchActive = false
                        viewModel.updateSearchQuery("")
                    },
                    keyboardController = keyboardController,
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                SongSortType.NAME -> R.string.sort_by_name
                                SongSortType.ARTIST -> R.string.sort_by_artist
                                SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            }
                        },
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.n_song,
                                filteredSongs.size,
                                filteredSongs.size,
                            ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp).size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }
            }

            if (filteredSongs.isEmpty() && searchQuery.isNotBlank()) {
                item(
                    key = "empty_search_result",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                }
            }

            itemsIndexed(
                items = filteredSongs,
                key = { _, item -> item.song.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG },
            ) { index, song ->
                SongListItem(
                    song = song,
                    showInLibraryIcon = true,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    showLikedIcon = true,
                    showDownloadIcon = filter != SongFilter.DOWNLOADED,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = queueAllSongsStr,
                                            items = filteredSongs.map { it.toMediaItem() },
                                            startIndex = index,
                                        ),
                                    )
                                }
                            }.animateItem(),
                )
            }
        }

        // Show upload FAB when on UPLOADED filter, shuffle FAB otherwise
        HideOnScrollFAB(
            visible = if (filter == SongFilter.UPLOADED) true else filteredSongs.isNotEmpty(),
            lazyListState = lazyListState,
            icon = if (filter == SongFilter.UPLOADED) R.drawable.upload else R.drawable.shuffle,
            onClick = {
                if (filter == SongFilter.UPLOADED) {
                    filePickerLauncher.launch(
                        arrayOf(
                            "audio/mpeg",
                            "audio/mp4",
                            "audio/x-m4a",
                            "audio/flac",
                            "audio/ogg",
                            "audio/x-ms-wma",
                        ),
                    )
                } else {
                    playerConnection.playQueue(
                        ListQueue(
                            title = queueAllSongsStr,
                            items = filteredSongs.shuffled().map { it.toMediaItem() },
                        ),
                    )
                }
            },
        )
    }
}
