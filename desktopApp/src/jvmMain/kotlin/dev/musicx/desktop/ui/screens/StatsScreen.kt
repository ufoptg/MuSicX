/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.musicx.desktop.history.HistoryRepository
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.desktop.state.content.PlaylistAlbumState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.desktop.ui.components.AlbumContextMenu
import dev.musicx.desktop.ui.components.ArtistContextMenu
import dev.musicx.desktop.ui.components.SongContextMenu
import dev.musicx.desktop.utils.saveImage
import dev.musicx.shared.db.HistoryAlbumStatistic
import dev.musicx.shared.db.HistoryArtistStatistic
import dev.musicx.shared.db.HistoryStatGroup
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.statistics_load_failed
import dev.musicx.shared.generated.resources.statistics_save_image
import dev.musicx.shared.model.AlbumItem
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.model.SearchFilter
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.ui.menu.cursorContextMenu
import dev.musicx.shared.ui.menu.rememberCursorContextMenuState
import dev.musicx.shared.ui.screens.StatisticsItem
import dev.musicx.shared.ui.screens.StatisticsPeriod
import dev.musicx.shared.ui.screens.StatisticsScreen
import dev.musicx.shared.ui.screens.StatisticsUiState
import dev.musicx.shared.ui.screens.statisticsPlayCountDuration
import dev.musicx.shared.ui.statistics.StatisticsDateRange
import dev.musicx.shared.ui.statistics.StatisticsPeriodMode
import dev.musicx.shared.ui.statistics.epochRange
import dev.musicx.shared.ui.statistics.matchingStatisticsAlbum
import dev.musicx.shared.ui.statistics.statisticsMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun StatsScreen() {
    val exportLabel = stringResource(Res.string.statistics_save_image)
    val loadFailedMessage = stringResource(Res.string.statistics_load_failed)
    val songMenu = rememberCursorContextMenuState()
    var menuItem by remember { mutableStateOf<YTItem?>(null) }
    val scope = rememberCoroutineScope()
    var menuJob by remember { mutableStateOf<Job?>(null) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var mode by remember { mutableStateOf(StatisticsPeriodMode.CONTINUOUS) }
    var period by remember { mutableStateOf(StatisticsPeriod.WEEK) }
    var dateRange by remember { mutableStateOf<StatisticsDateRange?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var songItems by remember { mutableStateOf<Map<String, SongItem>>(emptyMap()) }
    var topSongs by remember { mutableStateOf<List<HistoryStatGroup>>(emptyList()) }
    var topArtists by remember { mutableStateOf<List<HistoryArtistStatistic>>(emptyList()) }
    var topAlbums by remember { mutableStateOf<List<HistoryAlbumStatistic>>(emptyList()) }
    var totalScrobbles by remember { mutableStateOf(0) }
    var artistCount by remember { mutableStateOf(0) }
    var albumCount by remember { mutableStateOf(0) }
    var totalTimeMs by remember { mutableStateOf(0L) }
    var dailyStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var hourlyStats by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasLoaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    val artistThumbnails = remember { mutableStateMapOf<String, String?>() }
    val artistIds = remember { mutableStateMapOf<String, String>() }
    var artistFallbacks by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    LaunchedEffect(period, mode, dateRange, refreshKey) {
        isLoading = true
        loadFailed = false
        try {
            val (sinceMs, untilMs) = period.epochRange(dateRange, mode = mode)
            val snapshot = withContext(Dispatchers.IO) { HistoryRepository.getStatisticsSnapshot(sinceMs, untilMs) }
            val songs = snapshot.songs
            val artists = snapshot.artists
            val albums = snapshot.albums
            val totals = snapshot.totals
            topSongs = songs
            songItems = snapshot.songItems.associateBy { it.id }
            topArtists = artists
            artistFallbacks =
                topArtists.associate { artist ->
                    artist.name to snapshot.songs.firstOrNull { it.artists.contains(artist.name) }?.thumbnailUrl
                }
            topAlbums = albums
            artistCount = artists.size
            albumCount = albums.size
            totalScrobbles = totals.playCount
            totalTimeMs = totals.playTimeMs
            dailyStats = snapshot.daily.associate { it.bucket to it.playCount }
            hourlyStats = snapshot.hourly.associate { it.bucket.toInt() to it.playCount }
            hasLoaded = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    when (val item = menuItem) {
        is SongItem -> {
            SongContextMenu(song = item, expanded = songMenu.expanded, offset = songMenu.offset, onDismiss = songMenu::dismiss)
        }

        is AlbumItem -> {
            AlbumContextMenu(
                album = item,
                expanded = songMenu.expanded,
                offset = songMenu.offset,
                onDismiss = songMenu::dismiss,
            )
        }

        is ArtistItem -> {
            ArtistContextMenu(
                artist = item,
                expanded = songMenu.expanded,
                offset = songMenu.offset,
                onDismiss = songMenu::dismiss,
            )
        }

        else -> {
        }
    }
    StatisticsScreen(
        exportLabel = exportLabel,
        onExport = { saveImage(it, exportLabel, "metrolist-statistics.png") },
        modifier = Modifier.cursorContextMenu(songMenu, openOnSecondaryClick = false),
        state =
            StatisticsUiState(
                period = period,
                mode = mode,
                dateRange = dateRange,
                totalPlays = totalScrobbles,
                totalTimeMs = totalTimeMs,
                artistCount = artistCount,
                albumCount = albumCount,
                dailyStats = dailyStats,
                hourlyStats = hourlyStats,
                tracks =
                    topSongs.mapIndexed { index, item ->
                        StatisticsItem(
                            id = "track:${item.songId}",
                            title = item.title,
                            rankedTitle = "${index + 1}. ${item.title}",
                            subtitle = statisticsPlayCountDuration(item.playCount, item.playTimeMs),
                            thumbnailUrl = item.thumbnailUrl,
                        )
                    },
                albums =
                    topAlbums.mapIndexed { index, item ->
                        StatisticsItem(
                            id = "album:$index:${item.name}",
                            title = item.name,
                            rankedTitle = "${index + 1}. ${item.name}",
                            subtitle = statisticsPlayCountDuration(item.playCount, item.playTimeMs),
                            thumbnailUrl = item.thumbnailUrl,
                        )
                    },
                artists =
                    topArtists.mapIndexed { index, item ->
                        StatisticsItem(
                            id = "artist:$index:${item.name}",
                            title = item.name,
                            rankedTitle = "${index + 1}. ${item.name}",
                            subtitle = statisticsPlayCountDuration(item.playCount, item.playTimeMs),
                            thumbnailUrl = artistThumbnails[item.name] ?: artistFallbacks[item.name],
                            circularArtwork = true,
                        )
                    },
                isLoading = isLoading,
                hasLoaded = hasLoaded,
                errorMessage = if (loadFailed) stringResource(Res.string.statistics_load_failed) else null,
            ),
        onArtistVisible = { item ->
            val artistName = statisticsArtistName(item.id)
            if (!artistThumbnails.containsKey(artistName)) {
                val match =
                    withContext(Dispatchers.IO) {
                        try {
                            GlobalYouTubeRepository.instance
                                .searchFiltered(artistName, SearchFilter.ARTIST)
                                .items
                                .filterIsInstance<ArtistItem>()
                                .firstOrNull { it.title.equals(artistName, ignoreCase = true) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                artistThumbnails[artistName] = match?.thumbnail
                match?.let { artistIds[artistName] = it.id }
            }
        },
        onItemMenu = { item ->
            menuJob?.cancel()
            menuJob =
                scope.launch {
                    try {
                        menuItem =
                            when {
                                item.id.startsWith("track:") -> {
                                    songItems[item.id.removePrefix("track:")]
                                        ?: HistoryRepository
                                            .getStatisticsSongs(
                                                topSongs.filter {
                                                    it.songId ==
                                                        item.id.removePrefix("track:")
                                                },
                                            ).firstOrNull()
                                }

                                item.id.startsWith(
                                    "album:",
                                ) -> {
                                    topAlbums
                                        .getOrNull(
                                            item.id
                                                .removePrefix("album:")
                                                .substringBefore(':')
                                                .toInt(),
                                        )?.statisticsMediaItem()
                                }

                                else -> {
                                    topArtists
                                        .getOrNull(
                                            item.id
                                                .removePrefix("artist:")
                                                .substringBefore(':')
                                                .toInt(),
                                        )?.statisticsMediaItem()
                                }
                            }
                        if (menuItem != null) songMenu.open() else AppState.snackbarHostState.showSnackbar(loadFailedMessage)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        menuItem = null
                        AppState.snackbarHostState.showSnackbar(loadFailedMessage)
                    }
                }
        },
        onModeSelected = {
            if (mode != it || dateRange != null) {
                isLoading = true
                mode = it
                dateRange = null
            }
        },
        onPlayAll = { shuffle ->
            playbackJob?.cancel()
            val ranking = topSongs
            playbackJob =
                scope.launch {
                    try {
                        val songs = HistoryRepository.getStatisticsSongs(ranking)
                        if (shuffle) PlayerState.shuffleQueue(songs) else songs.firstOrNull()?.let { PlayerState.playTrack(it, songs) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        AppState.snackbarHostState.showSnackbar(loadFailedMessage)
                    }
                }
        },
        onPeriodSelected = {
            if (period != it || dateRange != null) {
                isLoading = true
                period = it
                dateRange = null
            }
        },
        onDateRangeSelected = {
            if (dateRange != it) {
                isLoading = true
                dateRange = it
            }
        },
        onRefresh = { refreshKey++ },
        onItemClick = { item ->
            when {
                item.id.startsWith("track:") -> {
                    item.id.removePrefix("track:").let(PlayerState::playFromId)
                }

                item.id.startsWith("album:") -> {
                    val index =
                        item.id
                            .removePrefix("album:")
                            .substringBefore(':')
                            .toIntOrNull()
                    val album = index?.let(topAlbums::getOrNull) ?: return@StatisticsScreen
                    val query = "${album.name} ${album.artists}".trim()
                    AppState.scope.launch {
                        val foundId =
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    GlobalYouTubeRepository.instance
                                        .search(query)
                                        .filterIsInstance<AlbumItem>()
                                        .matchingStatisticsAlbum(album.name, album.artists)
                                        ?.id
                                }.getOrNull()
                            }
                        if (foundId != null) {
                            PlaylistAlbumState.fetchAlbumData(foundId)
                        }
                    }
                }

                item.id.startsWith("artist:") -> {
                    val artistName = statisticsArtistName(item.id)
                    val cachedId = artistIds[artistName]
                    if (cachedId != null) {
                        ArtistState.fetchArtistData(cachedId)
                    } else {
                        AppState.scope.launch {
                            val foundId =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val filtered =
                                            GlobalYouTubeRepository.instance
                                                .searchFiltered(artistName, SearchFilter.ARTIST)
                                                .items
                                                .filterIsInstance<ArtistItem>()
                                        (
                                            filtered.firstOrNull { it.title.equals(artistName, ignoreCase = true) }
                                                ?: filtered.firstOrNull()
                                        )?.id
                                            ?: GlobalYouTubeRepository.instance
                                                .search(artistName)
                                                .filterIsInstance<ArtistItem>()
                                                .firstOrNull()
                                                ?.id
                                    }.getOrNull()
                                }
                            if (foundId != null) {
                                artistIds[artistName] = foundId
                                ArtistState.fetchArtistData(foundId)
                            }
                        }
                    }
                }
            }
        },
        onPlayItem = { item ->
            if (item.id.startsWith("track:")) {
                item.id.removePrefix("track:").let(PlayerState::playFromId)
            }
        },
    )
}
