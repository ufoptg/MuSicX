package dev.musicx.android.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.musicx.android.LocalMiniPlayerBottomInset
import dev.musicx.android.playback.LocalPlayerConnection
import dev.musicx.android.ui.menu.AlbumMenu
import dev.musicx.android.ui.menu.ArtistMenu
import dev.musicx.android.ui.menu.SongMenu
import dev.musicx.android.utils.shareImage
import dev.musicx.android.viewmodels.StatsPeriod
import dev.musicx.android.viewmodels.StatsUiState
import dev.musicx.android.viewmodels.StatsViewModel
import dev.musicx.shared.db.HistoryAlbumStatistic
import dev.musicx.shared.db.HistoryArtistStatistic
import dev.musicx.shared.db.HistoryStatGroup
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.share
import dev.musicx.shared.generated.resources.statistics_load_failed
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.state.SongLikeState
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.menu.MediaMenuPlaybackActions
import dev.musicx.shared.ui.menu.loadMediaItemSongs
import dev.musicx.shared.ui.playback.LocalPlaybackController
import dev.musicx.shared.ui.screens.StatisticsDetail
import dev.musicx.shared.ui.screens.StatisticsDetailScreen
import dev.musicx.shared.ui.screens.StatisticsItem
import dev.musicx.shared.ui.screens.StatisticsScreen
import dev.musicx.shared.ui.screens.StatisticsUiState
import dev.musicx.shared.ui.screens.statisticsPlayCountDuration
import dev.musicx.shared.ui.statistics.StatisticsDateRange
import dev.musicx.shared.ui.statistics.StatisticsPeriodMode
import dev.musicx.shared.ui.statistics.statisticsMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (detail: String, period: String, dateRange: StatisticsDateRange?, mode: StatisticsPeriodMode) -> Unit,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val shareLabel = stringResource(Res.string.share)
    val uiState by viewModel.uiState.collectAsState()
    val playItem = rememberStatisticsPlaybackAction()
    val openItemMenu = rememberStatisticsItemMenu(uiState, viewModel)
    StatisticsScreen(
        exportLabel = shareLabel,
        onExport = { shareImage(context, it, shareLabel) },
        state =
            StatisticsUiState(
                period = uiState.period,
                mode = uiState.mode,
                dateRange = uiState.dateRange,
                totalPlays = uiState.totalScrobbles,
                totalTimeMs = uiState.totalTimeMs,
                artistCount = uiState.artistCount,
                albumCount = uiState.albumCount,
                dailyStats = uiState.dailyStats,
                hourlyStats = uiState.hourlyStats,
                tracks = uiState.topSongs.mapIndexed { index, item -> item.toStatisticsItem(index) },
                albums = uiState.topAlbums.mapIndexed { index, item -> item.toStatisticsItem(index) },
                artists = uiState.topArtists.mapIndexed { index, item -> item.toStatisticsItem(index) },
                isLoading = uiState.isLoading,
                hasLoaded = uiState.hasLoaded,
                errorMessage = if (uiState.loadFailed) stringResource(Res.string.statistics_load_failed) else null,
            ),
        onPeriodSelected = { viewModel.setPeriod(it) },
        onDateRangeSelected = { viewModel.setPeriod(uiState.period, it) },
        onRefresh = viewModel::refreshStats,
        onModeSelected = viewModel::setMode,
        onPlayAll = rememberStatisticsQueuePlayback(viewModel),
        onOpenDetail = { detail ->
            onOpenDetail(detail.name, uiState.period.name, uiState.dateRange, uiState.mode)
        },
        onItemClick = statisticsItemClick(playItem, openItemMenu),
        onPlayItem = playItem,
        onItemMenu = openItemMenu,
        onNavigateBack = onNavigateBack,
        bottomContentPadding = LocalMiniPlayerBottomInset.current,
    )
}

@Composable
fun StatsDetailScreen(
    detail: String,
    period: String,
    dateRange: StatisticsDateRange?,
    mode: StatisticsPeriodMode,
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod = remember(period) { StatsPeriod.entries.firstOrNull { it.name == period } ?: StatsPeriod.WEEK }
    val selectedDetail = remember(detail) { StatisticsDetail.entries.firstOrNull { it.name == detail } ?: StatisticsDetail.TRACKS }
    val playItem = rememberStatisticsPlaybackAction()
    val openItemMenu = rememberStatisticsItemMenu(uiState, viewModel)

    LaunchedEffect(selectedPeriod, dateRange, mode) {
        if (uiState.period != selectedPeriod || uiState.dateRange != dateRange ||
            uiState.mode != mode
        ) {
            viewModel.setPeriod(selectedPeriod, dateRange, mode)
        }
    }

    val items =
        when (selectedDetail) {
            StatisticsDetail.TRACKS -> uiState.topSongs.mapIndexed { index, item -> item.toStatisticsItem(index) }
            StatisticsDetail.ALBUMS -> uiState.topAlbums.mapIndexed { index, item -> item.toStatisticsItem(index) }
            StatisticsDetail.ARTISTS -> uiState.topArtists.mapIndexed { index, item -> item.toStatisticsItem(index) }
        }
    StatisticsDetailScreen(
        detail = selectedDetail,
        items = items,
        onNavigateBack = onNavigateBack,
        contentPadding = PaddingValues(bottom = LocalMiniPlayerBottomInset.current),
        onPlayItem = playItem,
        onItemMenu = openItemMenu,
        onPlayAll = rememberStatisticsQueuePlayback(viewModel),
        onItemClick = statisticsItemClick(playItem, openItemMenu),
        isLoading = uiState.isLoading,
        errorMessage = if (uiState.loadFailed) stringResource(Res.string.statistics_load_failed) else null,
    )
}

@Composable
private fun rememberStatisticsPlaybackAction(): (StatisticsItem) -> Unit {
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    return { item ->
        if (item.id.startsWith("track:")) {
            playbackJob?.cancel()
            playbackJob =
                scope.launch {
                    GlobalYouTubeRepository.instance
                        .getSongMetadata(item.id.removePrefix("track:"))
                        ?.let { playerConnection?.playQueue(listOf(it), 0) }
                }
        }
    }
}

@Composable
private fun HistoryStatGroup.toStatisticsItem(index: Int) =
    StatisticsItem(
        id = "track:$songId",
        title = title,
        rankedTitle = "${index + 1}. $title",
        subtitle = statisticsPlayCountDuration(playCount, playTimeMs),
        thumbnailUrl = thumbnailUrl,
    )

@Composable
private fun HistoryAlbumStatistic.toStatisticsItem(index: Int) =
    StatisticsItem(
        id = "album:$index:$name",
        title = name,
        rankedTitle = "${index + 1}. $name",
        subtitle = statisticsPlayCountDuration(playCount, playTimeMs),
        thumbnailUrl = thumbnailUrl,
    )

@Composable
private fun HistoryArtistStatistic.toStatisticsItem(index: Int) =
    StatisticsItem(
        id = "artist:$index:$name",
        title = name,
        rankedTitle = "${index + 1}. $name",
        subtitle = statisticsPlayCountDuration(playCount, playTimeMs),
        thumbnailUrl = thumbnailUrl,
        circularArtwork = true,
    )

@Composable
private fun rememberStatisticsQueuePlayback(viewModel: StatsViewModel): (Boolean) -> Unit {
    val context = LocalContext.current
    val loadFailedMessage = stringResource(Res.string.statistics_load_failed)
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    return { shuffle ->
        job?.cancel()
        job =
            scope.launch {
                try {
                    val songs = viewModel.rankedSongs()
                    if (shuffle) {
                        player?.shuffleQueue(songs)
                    } else if (songs.isNotEmpty()) {
                        player?.playQueue(songs, 0)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(context, loadFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }
}

private fun statisticsItemClick(
    playItem: (StatisticsItem) -> Unit,
    openMenu: (StatisticsItem) -> Unit,
): (StatisticsItem) -> Unit =
    { item ->
        if (item.id.startsWith("track:")) playItem(item) else openMenu(item)
    }

@Composable
private fun rememberStatisticsItemMenu(
    state: StatsUiState,
    viewModel: StatsViewModel,
): (StatisticsItem) -> Unit {
    val context = LocalContext.current
    val loadFailedMessage = stringResource(Res.string.statistics_load_failed)
    var menuItem by remember { mutableStateOf<YTItem?>(null) }
    val player = LocalPlayerConnection.current
    val playback = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    var menuJob by remember { mutableStateOf<Job?>(null) }
    PopupContent(menuItem) { item ->
        val actions =
            MediaMenuPlaybackActions(item, playback, withSongs = { action ->
                scope.launch {
                    try {
                        action(loadMediaItemSongs(item))
                        menuItem = null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        menuItem = null
                        Toast.makeText(context, loadFailedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            })
        when (item) {
            is SongItem -> {
                SongMenu(
                    song = item,
                    isLiked = SongLikeState.isLiked(item.id),
                    onDismiss = { menuItem = null },
                    onPlayNext = { player?.addToQueue(item, playNext = true) },
                    onAddToQueue = { player?.addToQueue(item) },
                    onLike = { SongLikeState.toggleLike(item) },
                )
            }

            is AlbumItem -> {
                AlbumMenu(album = item, onDismiss = {
                    menuItem = null
                }, onPlay = actions::play, onShuffle = actions::shuffle, onPlayNext = actions::playNext, onAddToQueue = actions::addToQueue)
            }

            is ArtistItem -> {
                ArtistMenu(artist = item, onDismiss = {
                    menuItem = null
                }, onPlay = actions::play, onShuffle = actions::shuffle, onPlayNext = actions::playNext, onAddToQueue = actions::addToQueue)
            }

            else -> {}
        }
    }
    return { item ->
        menuJob?.cancel()
        menuJob =
            scope.launch {
                try {
                    menuItem =
                        when {
                            item.id.startsWith("track:") -> {
                                viewModel.songItem(item.id.removePrefix("track:"))
                            }

                            item.id.startsWith(
                                "album:",
                            ) -> {
                                state.topAlbums
                                    .getOrNull(
                                        item.id
                                            .removePrefix("album:")
                                            .substringBefore(':')
                                            .toInt(),
                                    )?.statisticsMediaItem()
                            }

                            else -> {
                                state.topArtists
                                    .getOrNull(
                                        item.id
                                            .removePrefix("artist:")
                                            .substringBefore(':')
                                            .toInt(),
                                    )?.statisticsMediaItem()
                            }
                        }
                    if (menuItem == null) Toast.makeText(context, loadFailedMessage, Toast.LENGTH_SHORT).show()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    menuItem = null
                    Toast.makeText(context, loadFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }
}
