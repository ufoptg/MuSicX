/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val MuSicXRed = Color(0xFFED5564)

private val MuSicXColors =
    darkColorScheme(
        primary = MuSicXRed,
        onPrimary = Color.White,
        background = Color(0xFF0E0E10),
        onBackground = Color(0xFFF2F2F5),
        surface = Color(0xFF161619),
        onSurface = Color(0xFFF2F2F5),
        surfaceVariant = Color(0xFF26262B),
        onSurfaceVariant = Color(0xFFB6B6C0),
        error = Color(0xFFFF6B6B),
    )

/**
 * Top-level navigation destinations, mirroring the Android app's bottom-navigation sections.
 * On desktop these are shown in a left-hand [NavigationRail].
 */
private enum class Destination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Home("Home", Icons.Default.Home),
    Search("Search", Icons.Default.Search),
    Library("Library", Icons.Default.LibraryMusic),
}

fun main() = application {
    val client = remember { DesktopInnerTube() }
    val player = remember { DesktopAudioPlayer() }
    DisposableEffect(Unit) {
        player.prewarm()
        onDispose {
            player.close()
            client.close()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "MuSicX",
        icon = painterResource("ic_launcher.png"),
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        MaterialTheme(colorScheme = MuSicXColors) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MuSicXApp(client, player)
            }
        }
    }
}

@Composable
private fun MuSicXApp(
    client: DesktopInnerTube,
    player: DesktopAudioPlayer,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Home feed
    var homeRows by remember { mutableStateOf<List<HomeRow>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }

    // Playback queue (decoupled from the list a song was started from, so Home / Search /
    // Library can all feed the player) + a session "recently played" history for Library.
    var queue by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(-1) }
    var history by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    var busyId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableStateOf(100) }

    var destination by remember { mutableStateOf(Destination.Home) }
    var playerExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val nowPlaying = queue.getOrNull(currentIndex)

    fun playFrom(list: List<SearchHit>, index: Int) {
        val hit = list.getOrNull(index) ?: return
        if (busyId != null) return
        scope.launch {
            busyId = hit.videoId
            queue = list
            currentIndex = index
            error = null
            positionMs = 0L
            durationMs = 0L
            try {
                DesktopLog.log("playHit: resolving ${hit.videoId} (${hit.title})")
                val resolveStart = System.currentTimeMillis()
                val stream = withContext(Dispatchers.IO) { client.resolveAudioStream(hit.videoId) }
                DesktopLog.log("playHit: resolved in ${System.currentTimeMillis() - resolveStart} ms")
                player.play(stream)
                playing = true
                // Push to the front of the session history (most-recent-first, deduped).
                history = (listOf(hit) + history.filterNot { it.videoId == hit.videoId }).take(50)
            } catch (t: Throwable) {
                DesktopLog.log("playHit failed", t)
                error = t.message ?: t::class.simpleName ?: "Playback failed"
                playing = false
            } finally {
                busyId = null
            }
        }
    }

    fun playNext() {
        if (currentIndex + 1 < queue.size) playFrom(queue, currentIndex + 1)
    }

    fun playPrevious() {
        if (currentIndex - 1 >= 0) playFrom(queue, currentIndex - 1)
    }

    // Auto-advance to the next queued track when one finishes.
    DisposableEffect(player) {
        player.onEnded = { scope.launch { playNext() } }
        onDispose { player.onEnded = null }
    }

    // Poll VLC for playback position while a track is active.
    LaunchedEffect(nowPlaying?.videoId) {
        while (isActive && nowPlaying != null) {
            positionMs = player.positionMs()
            durationMs = player.durationMs()
            kotlinx.coroutines.delay(500)
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        scope.launch {
            loading = true
            error = null
            try {
                results = client.searchSongs(q)
                if (results.isEmpty()) error = "No songs found"
            } catch (t: Throwable) {
                results = emptyList()
                error = t.message ?: t::class.simpleName ?: "Search failed"
            } finally {
                loading = false
            }
        }
    }

    fun loadHome() {
        if (homeLoading || homeRows.isNotEmpty()) return
        scope.launch {
            homeLoading = true
            homeError = null
            try {
                homeRows = client.homeFeed()
                if (homeRows.isEmpty()) homeError = "Couldn't load recommendations"
            } catch (t: Throwable) {
                homeError = t.message ?: t::class.simpleName ?: "Failed to load home"
            } finally {
                homeLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadHome() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AppNavigationRail(
                    selected = destination,
                    onSelect = { destination = it },
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Crossfade(targetState = destination) { dest ->
                        when (dest) {
                            Destination.Home ->
                                HomeScreen(
                                    rows = homeRows,
                                    loading = homeLoading,
                                    error = homeError,
                                    nowPlayingId = nowPlaying?.videoId,
                                    busyId = busyId,
                                    onPlay = { list, index -> playFrom(list, index) },
                                    onRetry = { homeRows = emptyList(); loadHome() },
                                )
                            Destination.Search ->
                                SearchScreen(
                                    query = query,
                                    onQueryChange = { query = it },
                                    loading = loading,
                                    error = error,
                                    results = results,
                                    nowPlayingId = nowPlaying?.videoId,
                                    busyId = busyId,
                                    onSearch = ::runSearch,
                                    onPlayIndex = { index -> playFrom(results, index) },
                                )
                            Destination.Library ->
                                LibraryScreen(
                                    history = history,
                                    nowPlayingId = nowPlaying?.videoId,
                                    busyId = busyId,
                                    onPlayIndex = { index -> playFrom(history, index) },
                                )
                        }
                    }
                }
            }

            NowPlayingBar(
                nowPlaying = nowPlaying,
                playing = playing,
                busy = busyId != null,
                positionMs = positionMs,
                durationMs = durationMs,
                seekPreview = seekPreview,
                hasNext = currentIndex + 1 < queue.size,
                hasPrevious = currentIndex > 0,
                volume = volume,
                onVolumeChange = {
                    volume = it
                    player.setVolume(it)
                },
                onTogglePlay = {
                    if (nowPlaying != null) {
                        player.togglePause()
                        playing = player.isPlaying
                    }
                },
                onNext = ::playNext,
                onPrevious = ::playPrevious,
                onSeekChange = { seekPreview = it },
                onSeekCommit = {
                    player.seekToFraction(it)
                    seekPreview = null
                },
                onExpand = { if (nowPlaying != null) playerExpanded = true },
            )
        }

        AnimatedVisibility(
            visible = playerExpanded && nowPlaying != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            FullPlayer(
                nowPlaying = nowPlaying,
                playing = playing,
                busy = busyId != null,
                positionMs = positionMs,
                durationMs = durationMs,
                seekPreview = seekPreview,
                hasNext = currentIndex + 1 < queue.size,
                hasPrevious = currentIndex > 0,
                volume = volume,
                onVolumeChange = {
                    volume = it
                    player.setVolume(it)
                },
                onTogglePlay = {
                    if (nowPlaying != null) {
                        player.togglePause()
                        playing = player.isPlaying
                    }
                },
                onNext = ::playNext,
                onPrevious = ::playPrevious,
                onSeekChange = { seekPreview = it },
                onSeekCommit = {
                    player.seekToFraction(it)
                    seekPreview = null
                },
                onCollapse = { playerExpanded = false },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    selected: Destination,
    onSelect: (Destination) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Image(
            painter = painterResource("ic_launcher.png"),
            contentDescription = "MuSicX",
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Destination.entries.forEach { item ->
            NavigationRailItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    rows: List<HomeRow>,
    loading: Boolean,
    error: String?,
    nowPlayingId: String?,
    busyId: String?,
    onPlay: (List<SearchHit>, Int) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        ScreenTitle(title = "Home", subtitle = "Made for you")
        when {
            loading ->
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            error != null ->
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Retry",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
                    )
                }
            else ->
                LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    items(rows, key = { it.title }) { row ->
                        HomeRowView(
                            row = row,
                            nowPlayingId = nowPlayingId,
                            busyId = busyId,
                            onPlay = onPlay,
                        )
                    }
                }
        }
    }
}

@Composable
private fun HomeRowView(
    row: HomeRow,
    nowPlayingId: String?,
    busyId: String?,
    onPlay: (List<SearchHit>, Int) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(row.items, key = { it.videoId }) { hit ->
                val index = row.items.indexOf(hit)
                SongCard(
                    hit = hit,
                    isActive = hit.videoId == nowPlayingId,
                    isBusy = busyId == hit.videoId,
                    onClick = { onPlay(row.items, index) },
                )
            }
        }
    }
}

@Composable
private fun SongCard(
    hit: SearchHit,
    isActive: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            RemoteImage(
                url = hit.thumbnailUrl,
                contentDescription = hit.title,
                modifier = Modifier.size(134.dp).clip(RoundedCornerShape(10.dp)),
            )
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = hit.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!hit.subtitle.isNullOrBlank()) {
            Text(
                text = hit.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    results: List<SearchHit>,
    nowPlayingId: String?,
    busyId: String?,
    onSearch: () -> Unit,
    onPlayIndex: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        ScreenTitle(title = "Search", subtitle = "Search YouTube Music — tap a song to play")
        SearchBar(query = query, onQueryChange = onQueryChange, enabled = !loading, onSearch = onSearch)

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                items(results, key = { it.videoId }) { hit ->
                    val index = results.indexOf(hit)
                    ResultRow(
                        hit = hit,
                        isActive = hit.videoId == nowPlayingId,
                        isBusy = busyId == hit.videoId,
                        onClick = { onPlayIndex(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    history: List<SearchHit>,
    nowPlayingId: String?,
    busyId: String?,
    onPlayIndex: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        ScreenTitle(title = "Library", subtitle = "Recently played this session")
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = "Nothing here yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Play a song and it'll show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                items(history, key = { it.videoId }) { hit ->
                    val index = history.indexOf(hit)
                    ResultRow(
                        hit = hit,
                        isActive = hit.videoId == nowPlayingId,
                        isBusy = busyId == hit.videoId,
                        onClick = { onPlayIndex(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Song or artist") },
        )
        IconButton(
            onClick = onSearch,
            enabled = enabled && query.isNotBlank(),
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ResultRow(
    hit: SearchHit,
    isActive: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            RemoteImage(
                url = hit.thumbnailUrl,
                contentDescription = hit.title,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hit.subtitle.isNullOrBlank()) {
                Text(
                    text = hit.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isActive && !isBusy) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingBar(
    nowPlaying: SearchHit?,
    playing: Boolean,
    busy: Boolean,
    positionMs: Long,
    durationMs: Long,
    seekPreview: Float?,
    hasNext: Boolean,
    hasPrevious: Boolean,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onExpand: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RemoteImage(
                    url = nowPlaying?.thumbnailUrl,
                    contentDescription = nowPlaying?.title,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = nowPlaying != null, onClick = onExpand),
                )
                Column(
                    modifier = Modifier.weight(1f).clickable(enabled = nowPlaying != null, onClick = onExpand),
                ) {
                    Text(
                        text = nowPlaying?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val secondary =
                        when {
                            busy -> "Loading…"
                            nowPlaying == null -> "Choose a song and press play"
                            !nowPlaying.subtitle.isNullOrBlank() -> nowPlaying.subtitle
                            else -> "Now playing"
                        }
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onExpand, enabled = nowPlaying != null) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Expand player", modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onPrevious, enabled = hasPrevious && !busy) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(30.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    enabled = nowPlaying != null && !busy,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = hasNext && !busy) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(30.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = if (volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Slider(
                        value = volume / 100f,
                        onValueChange = { onVolumeChange((it * 100).toInt()) },
                        modifier = Modifier.width(110.dp),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
            }

            SeekRow(
                positionMs = positionMs,
                durationMs = durationMs,
                seekPreview = seekPreview,
                enabled = nowPlaying != null && durationMs > 0,
                onSeekChange = onSeekChange,
                onSeekCommit = onSeekCommit,
            )
        }
    }
}

@Composable
private fun FullPlayer(
    nowPlaying: SearchHit?,
    playing: Boolean,
    busy: Boolean,
    positionMs: Long,
    durationMs: Long,
    seekPreview: Float?,
    hasNext: Boolean,
    hasPrevious: Boolean,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onCollapse: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse player", modifier = Modifier.size(30.dp))
                }
                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RemoteImage(
                    url = nowPlaying?.thumbnailUrl,
                    contentDescription = nowPlaying?.title,
                    modifier = Modifier.size(320.dp).clip(RoundedCornerShape(20.dp)),
                )
                Text(
                    text = nowPlaying?.title ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 28.dp),
                )
                Text(
                    text = nowPlaying?.subtitle ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SeekRow(
                positionMs = positionMs,
                durationMs = durationMs,
                seekPreview = seekPreview,
                enabled = nowPlaying != null && durationMs > 0,
                onSeekChange = onSeekChange,
                onSeekCommit = onSeekCommit,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = hasPrevious && !busy) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.width(24.dp))
                IconButton(
                    onClick = onTogglePlay,
                    enabled = nowPlaying != null && !busy,
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                IconButton(onClick = onNext, enabled = hasNext && !busy) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Slider(
                    value = volume / 100f,
                    onValueChange = { onVolumeChange((it * 100).toInt()) },
                    modifier = Modifier.width(200.dp).padding(start = 8.dp),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun SeekRow(
    positionMs: Long,
    durationMs: Long,
    seekPreview: Float?,
    enabled: Boolean,
    onSeekChange: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
) {
    val fraction =
        seekPreview ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatTime(seekPreview?.let { (it * durationMs).toLong() } ?: positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = fraction,
            onValueChange = onSeekChange,
            onValueChangeFinished = { onSeekCommit(seekPreview ?: fraction) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
        )
        Text(
            text = formatTime(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** In-memory thumbnail cache so lists don't re-download artwork on every recompose/scroll. */
private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
private fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { imageCache[it] }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        imageCache[url]?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = URL(url).openStream().use { it.readBytes() }
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            }
        if (loaded != null) {
            imageCache[url] = loaded
            bitmap = loaded
        }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
