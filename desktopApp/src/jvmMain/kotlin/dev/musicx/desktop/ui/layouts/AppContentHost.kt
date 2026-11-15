/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.desktop.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.musicx.desktop.deviceconnect.DesktopDeviceConnect
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.desktop.state.content.BrowseState
import dev.musicx.desktop.state.content.LibraryContentState
import dev.musicx.desktop.state.content.PlaylistAlbumState
import dev.musicx.desktop.state.content.PodcastState
import dev.musicx.desktop.state.content.SearchState
import dev.musicx.desktop.state.playback.ListenTogetherPlaybackRequest
import dev.musicx.desktop.state.playback.ListenTogetherState
import dev.musicx.desktop.state.playback.LyricsState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.ui.screens.AboutArtistScreenHost
import dev.musicx.desktop.ui.screens.AlbumScreen
import dev.musicx.desktop.ui.screens.ArtistScreenHost
import dev.musicx.desktop.ui.screens.BrowseScreen
import dev.musicx.desktop.ui.screens.HistoryScreen
import dev.musicx.desktop.ui.screens.HomeScreen
import dev.musicx.desktop.ui.screens.LibraryScreen
import dev.musicx.desktop.ui.screens.ListenTogetherScreen
import dev.musicx.desktop.ui.screens.LocalPlaylistScreen
import dev.musicx.desktop.ui.screens.PlaylistScreen
import dev.musicx.desktop.ui.screens.PodcastScreen
import dev.musicx.desktop.ui.screens.SearchResultsList
import dev.musicx.desktop.ui.screens.StatsScreen
import dev.musicx.shared.db.isHistoryPlaylistId
import dev.musicx.shared.deviceconnect.DeviceConnectPairingHost
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.listen_together_guest_choice_message
import dev.musicx.shared.generated.resources.listen_together_guest_choice_title
import dev.musicx.shared.generated.resources.listen_together_guest_queue
import dev.musicx.shared.generated.resources.listen_together_guest_solo
import dev.musicx.shared.ui.components.AlertDialog
import dev.musicx.shared.ui.components.CollectionHeaderNavigation
import dev.musicx.shared.ui.components.LoadingScreen
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.components.lyrics.LyricsTranslationSnackbar
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.listentogether.ListenTogetherGlobalEvents
import dev.musicx.shared.ui.menu.HiddenHomeItemsSnackbar
import dev.musicx.shared.ui.navigation.LocalNavigator
import dev.musicx.shared.ui.navigation.NavigationTransition
import dev.musicx.shared.ui.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AppContentHost(
    route: Screen,
    colorScheme: ColorScheme,
    libraryGridState: LazyGridState,
    onLibraryScrollStateChange: (LazyGridState) -> Unit,
    modifier: Modifier = Modifier,
    onSearchChange: (String) -> Unit = {},
) {
    val libraryNeeded = route == Screen.Library || (route is Screen.Playlist && isHistoryPlaylistId(route.id))
    if (libraryNeeded && !PlayerState.isExpanded && !UiState.videoFullscreenObscuresPlayer) {
        LifecycleStartEffect(Unit) {
            LibraryContentState.setActive(true)
            onStopOrDispose { LibraryContentState.setActive(false) }
        }
    }
    val navigator = LocalNavigator.current
    HiddenHomeItemsSnackbar(AppState.snackbarHostState)
    LyricsTranslationSnackbar(AppState.snackbarHostState, LyricsState.lyricsTranslationFailures)
    ListenTogetherGlobalEvents(ListenTogetherState.listenTogetherManager) { AppState.snackbarHostState.showSnackbar(it) }
    val deviceConnectState by DesktopDeviceConnect.manager.state.collectAsState()
    DeviceConnectPairingHost(
        state = deviceConnectState,
        onApprovePairing = DesktopDeviceConnect.manager::approvePairing,
        onRejectPairing = DesktopDeviceConnect.manager::rejectPairing,
    )
    var pendingPlaybackRequest by remember { mutableStateOf<ListenTogetherPlaybackRequest?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        PlayerState.listenTogetherPlaybackRequests.collect { pendingPlaybackRequest = it }
    }
    LaunchedEffect(route) { LayoutUtils.syncContentState(route) }
    val contentShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.background(colorScheme.surface, contentShape).clip(contentShape),
    ) {
        key(LayoutUtils.contentRefreshKey) {
            NavigationTransition(route = route) { destination ->
                when (destination) {
                    is Screen.Artist -> {
                        if (ArtistState.selectedArtistId == destination.id ||
                            ArtistState.loadedArtistId == destination.id
                        ) {
                            ArtistScreenHost()
                        } else {
                            CollectionHeaderNavigation({ navigator.goBack() }) { LoadingScreen() }
                        }
                    }

                    is Screen.AboutArtist -> {
                        if (ArtistState.selectedArtistId == destination.id) {
                            AboutArtistScreenHost(destination.id, destination.name, destination.artist)
                        } else {
                            LoadingScreen()
                        }
                    }

                    is Screen.Playlist -> {
                        if (PlaylistAlbumState.selectedPlaylistId == destination.id) {
                            PlaylistScreen(colorScheme)
                        } else {
                            CollectionHeaderNavigation({ navigator.goBack() }) { LoadingScreen() }
                        }
                    }

                    is Screen.Album -> {
                        AlbumScreen(destination.id, colorScheme)
                    }

                    is Screen.HomeSection -> {
                        BrowseScreen(colorScheme, destination)
                    }

                    is Screen.Browse -> {
                        if (
                            BrowseState.selectedBrowseId == destination.browseId &&
                            BrowseState.selectedBrowseParams == destination.params
                        ) {
                            BrowseScreen(colorScheme)
                        } else {
                            CollectionHeaderNavigation({ navigator.goBack() }) { LoadingScreen() }
                        }
                    }

                    is Screen.Podcast -> {
                        if (PodcastState.selectedPodcastId == destination.id) PodcastScreen(colorScheme) else LoadingScreen()
                    }

                    Screen.Search -> {
                        SearchResultsList(
                            colorScheme = colorScheme,
                            onBack = {
                                SearchState.reset()
                                onSearchChange("")
                                navigator.goBack()
                            },
                        )
                    }

                    is Screen.AutoPlaylist -> {
                        LocalPlaylistScreen(destination.type)
                    }

                    Screen.Library, Screen.Statistics, Screen.ListenTogether -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 1400.dp).fillMaxSize()) {
                                when (destination) {
                                    Screen.Library -> {
                                        LibraryScreen(
                                            LibraryContentState.librarySections,
                                            colorScheme,
                                            libraryGridState,
                                            onScrollStateChange = onLibraryScrollStateChange,
                                        )
                                    }

                                    Screen.Statistics -> {
                                        StatsScreen()
                                    }

                                    else -> {
                                        ListenTogetherScreen(ListenTogetherState.listenTogetherManager)
                                    }
                                }
                            }
                        }
                    }

                    Screen.History -> {
                        HistoryScreen()
                    }

                    else -> {
                        HomeScreen()
                    }
                }
            }
        }
        PopupContent(pendingPlaybackRequest) { request ->
            AlertDialog(
                onDismissRequest = { pendingPlaybackRequest = null },
                icon = {
                    Icon(
                        imageVector = MuSicXIcons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = { Text(stringResource(Res.string.listen_together_guest_choice_title)) },
                text = { Text(stringResource(Res.string.listen_together_guest_choice_message)) },
                confirmButton = {
                    Button(
                        onClick = {
                            ListenTogetherState.listenTogetherManager.suggestTrack(request.song)
                            pendingPlaybackRequest = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(Res.string.listen_together_guest_queue))
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pendingPlaybackRequest = null }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                pendingPlaybackRequest = null
                                scope.launch {
                                    ListenTogetherState.listenTogetherManager.leaveRoom()
                                    withTimeoutOrNull(3000.milliseconds) {
                                        ListenTogetherState.listenTogetherManager.isInRoom.first { !it }
                                    }
                                    if (request.queue.isNotEmpty()) {
                                        PlayerState.playTrack(request.song, request.queue)
                                    } else {
                                        PlayerState.playTrack(request.song)
                                    }
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(Res.string.listen_together_guest_solo))
                        }
                    }
                },
            )
        }
    }
}
