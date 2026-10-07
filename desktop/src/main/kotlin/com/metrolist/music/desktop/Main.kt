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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap

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
    Account("Account", Icons.Default.AccountCircle),
    Settings("Settings", Icons.Default.Settings),
}

private enum class RepeatMode { Off, All, One }

private enum class SettingsSection(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Appearance("Appearance", Icons.Default.Palette),
    Content("Content", Icons.Default.Language),
    AI("AI", Icons.Default.Translate),
    Player("Player", Icons.Default.PlayArrow),
    Storage("Storage", Icons.Default.Storage),
    Privacy("Privacy", Icons.Default.Security),
    BackupAndRestore("Backup & restore", Icons.Default.Restore),
    Integrations("Integrations", Icons.Default.Link),
    Updater("Updater", Icons.Default.Update),
    About("About", Icons.Default.Info),
    Equalizer("Equalizer", Icons.Default.QueueMusic),
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
    var favorites by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Restore persisted favorites/history, then keep them on disk.
    LaunchedEffect(Unit) {
        val data = withContext(Dispatchers.IO) { DesktopLibraryStore.load() }
        favorites = data.favorites
        history = data.history
    }

    fun persistLibrary() {
        val snapshot = DesktopLibraryData(favorites = favorites, history = history)
        scope.launch(Dispatchers.IO) { DesktopLibraryStore.save(snapshot) }
    }

    fun toggleFavorite(hit: SearchHit) {
        favorites =
            if (favorites.any { it.videoId == hit.videoId }) {
                favorites.filterNot { it.videoId == hit.videoId }
            } else {
                listOf(hit) + favorites
            }
        persistLibrary()
    }

    fun isFavorite(hit: SearchHit?): Boolean = hit != null && favorites.any { it.videoId == hit.videoId }

    var busyId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableStateOf(100) }

    var destination by remember { mutableStateOf(Destination.Home) }
    var signedIn by remember { mutableStateOf(false) }
    var playerExpanded by remember { mutableStateOf(false) }
    var queueExpanded by remember { mutableStateOf(false) }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.Off) }
    var likedSongs by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var likedLoading by remember { mutableStateOf(false) }
    var libraryPlaylists by remember { mutableStateOf<List<PlaylistHit>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var openPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var openPlaylistTracks by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var openPlaylistLoading by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var settingsSubScreen by remember { mutableStateOf<String?>(null) }

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
                persistLibrary()
            } catch (t: Throwable) {
                DesktopLog.log("playHit failed", t)
                error = t.message ?: t::class.simpleName ?: "Playback failed"
                playing = false
            } finally {
                busyId = null
            }
        }
    }

    fun playNext(fromEnded: Boolean = false) {
        if (fromEnded && repeatMode == RepeatMode.One && currentIndex in queue.indices) {
            playFrom(queue, currentIndex)
            return
        }
        if (shuffleOn && queue.size > 1) {
            var next = Random.nextInt(queue.size)
            while (next == currentIndex) next = Random.nextInt(queue.size)
            playFrom(queue, next)
            return
        }
        if (currentIndex + 1 < queue.size) {
            playFrom(queue, currentIndex + 1)
        } else if (fromEnded && repeatMode == RepeatMode.All && queue.isNotEmpty()) {
            playFrom(queue, 0)
        }
    }

    fun playPrevious() {
        if (currentIndex - 1 >= 0) playFrom(queue, currentIndex - 1)
    }

    // Auto-advance to the next queued track when one finishes.
    DisposableEffect(player) {
        player.onEnded = { scope.launch { playNext(fromEnded = true) } }
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

    LaunchedEffect(Unit) {
        val stored = DesktopSessionStore.load()
        if (!stored.cookie.isNullOrBlank()) {
            client.setSessionCookie(stored.cookie)
            signedIn = true
        }
        loadHome()
    }

    LaunchedEffect(signedIn, destination) {
        if (signedIn && destination == Destination.Library &&
            libraryPlaylists.isEmpty() && !playlistsLoading &&
            likedSongs.isEmpty() && !likedLoading
        ) {
            playlistsLoading = true
            scope.launch(Dispatchers.IO) {
                libraryPlaylists =
                    runCatching { client.likedPlaylists() }.getOrDefault(emptyList())
                playlistsLoading = false
            }
            likedLoading = true
            scope.launch(Dispatchers.IO) {
                likedSongs =
                    runCatching { client.likedSongs() }.getOrDefault(emptyList())
                likedLoading = false
            }
        }
    }

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
                                    isFavorite = ::isFavorite,
                                    onToggleFavorite = ::toggleFavorite,
                                )
                            Destination.Account ->
                                LoginScreen(
                                    signedIn = signedIn,
                                    onSignIn = { cookie ->
                                        DesktopSessionStore.save(DesktopSessionData(cookie = cookie))
                                        client.setSessionCookie(cookie)
                                        signedIn = true
                                        homeRows = emptyList()
                                        loadHome()
                                    },
                                    onSignOut = {
                                        DesktopSessionStore.clear()
                                        client.setSessionCookie(null)
                                        signedIn = false
                                        homeRows = emptyList()
                                        loadHome()
                                    },
                                )
                            Destination.Settings ->
                                when {
                                    settingsSubScreen == "sponsorblock" ->
                                        SettingsSponsorBlockScreen(onBack = { settingsSubScreen = null })
                                    settingsSubScreen == "discord" ->
                                        SettingsDiscordScreen(onBack = { settingsSubScreen = null })
                                    settingsSubScreen == "lastfm" ->
                                        SettingsLastFmScreen(onBack = { settingsSubScreen = null })
                                    settingsSubScreen == "listen_together" ->
                                        SettingsListenTogetherScreen(onBack = { settingsSubScreen = null })
                                    settingsSubScreen == "spotify" ->
                                        SettingsSpotifyScreen(onBack = { settingsSubScreen = null })
                                    settingsSubScreen == "android_auto" ->
                                        SettingsAndroidAutoScreen(onBack = { settingsSubScreen = null })
                                    settingsSubScreen == "eq_wizard" ->
                                        SettingsEqWizardScreen(onBack = { settingsSubScreen = null })
                                    settingsSection == SettingsSection.Appearance ->
                                        SettingsAppearanceScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.Content ->
                                        SettingsContentScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.AI ->
                                        SettingsAiScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.Player ->
                                        SettingsPlayerScreen(
                                            onBack = { settingsSection = null },
                                            onOpenSub = { settingsSubScreen = it },
                                        )
                                    settingsSection == SettingsSection.Storage ->
                                        SettingsStorageScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.Privacy ->
                                        SettingsPrivacyScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.BackupAndRestore ->
                                        SettingsBackupScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.Integrations ->
                                        SettingsIntegrationsScreen(
                                            onBack = { settingsSection = null },
                                            onOpenSub = { settingsSubScreen = it },
                                        )
                                    settingsSection == SettingsSection.Updater ->
                                        SettingsUpdaterScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.About ->
                                        SettingsAboutScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.Equalizer ->
                                        SettingsEqualizerScreen(
                                            onBack = { settingsSection = null },
                                            onOpenSub = { settingsSubScreen = it },
                                        )
                                    else ->
                                        SettingsScreen(
                                            sections = SettingsSection.entries.toList(),
                                            onSelectSection = { section ->
                                                settingsSection = section
                                                settingsSubScreen = null
                                            },
                                        )
                                }

                            Destination.Library ->
                                LibraryScreen(
                                    favorites = favorites,
                                    history = history,
                                    nowPlayingId = nowPlaying?.videoId,
                                    busyId = busyId,
                                    onPlayFavorites = { index -> playFrom(favorites, index) },
                                    onPlayHistory = { index -> playFrom(history, index) },
                                    isFavorite = ::isFavorite,
                                    onToggleFavorite = ::toggleFavorite,
                                    onClearFavorites = {
                                        favorites = emptyList()
                                        persistLibrary()
                                    },
                                    onClearHistory = {
                                        history = emptyList()
                                        persistLibrary()
                                    },
                                    signedIn = signedIn,
                                    likedSongs = likedSongs,
                                    likedLoading = likedLoading,
                                    libraryPlaylists = libraryPlaylists,
                                    playlistsLoading = playlistsLoading,
                                    openPlaylistTitle = openPlaylistTitle,
                                    openPlaylistTracks = openPlaylistTracks,
                                    openPlaylistLoading = openPlaylistLoading,
                                    onOpenPlaylist = { item ->
                                        openPlaylistTitle = item.title
                                        openPlaylistLoading = true
                                        openPlaylistTracks = emptyList()
                                        scope.launch(Dispatchers.IO) {
                                            val tracks =
                                                runCatching { client.playlistTracks(item.id) }
                                                    .getOrDefault(emptyList())
                                            openPlaylistTracks = tracks
                                            openPlaylistLoading = false
                                        }
                                    },
                                    onClosePlaylist = {
                                        openPlaylistTitle = null
                                        openPlaylistTracks = emptyList()
                                    },
                                    onPlayLiked = { index -> playFrom(likedSongs, index) },
                                    onPlayPlaylistTracks = { index -> playFrom(openPlaylistTracks, index) },
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
                isFavorite = isFavorite(nowPlaying),
                onToggleFavorite = { nowPlaying?.let(::toggleFavorite) },
                hasQueue = queue.isNotEmpty(),
                onToggleQueue = { queueExpanded = !queueExpanded },
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

        AnimatedVisibility(
            visible = queueExpanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            QueuePanel(
                queue = queue,
                currentIndex = currentIndex,
                nowPlayingId = nowPlaying?.videoId,
                busyId = busyId,
                shuffleOn = shuffleOn,
                repeatMode = repeatMode,
                onToggleShuffle = { shuffleOn = !shuffleOn },
                onCycleRepeat = {
                    repeatMode =
                        when (repeatMode) {
                            RepeatMode.Off -> RepeatMode.All
                            RepeatMode.All -> RepeatMode.One
                            RepeatMode.One -> RepeatMode.Off
                        }
                },
                onClose = { queueExpanded = false },
                onPlayIndex = { index -> playFrom(queue, index) },
                isFavorite = ::isFavorite,
                onToggleFavorite = ::toggleFavorite,
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
    isFavorite: (SearchHit) -> Boolean,
    onToggleFavorite: (SearchHit) -> Unit,
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
                        isFavorite = isFavorite(hit),
                        onToggleFavorite = { onToggleFavorite(hit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    favorites: List<SearchHit>,
    history: List<SearchHit>,
    nowPlayingId: String?,
    busyId: String?,
    onPlayFavorites: (Int) -> Unit,
    onPlayHistory: (Int) -> Unit,
    isFavorite: (SearchHit) -> Boolean,
    onToggleFavorite: (SearchHit) -> Unit,
    onClearFavorites: () -> Unit,
    onClearHistory: () -> Unit,
    signedIn: Boolean,
    likedSongs: List<SearchHit>,
    likedLoading: Boolean,
    libraryPlaylists: List<PlaylistHit>,
    playlistsLoading: Boolean,
    openPlaylistTitle: String?,
    openPlaylistTracks: List<SearchHit>,
    openPlaylistLoading: Boolean,
    onOpenPlaylist: (PlaylistHit) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlayLiked: (Int) -> Unit,
    onPlayPlaylistTracks: (Int) -> Unit,
) {
    var tab by remember { mutableStateOf(0) } // 0 Favorites, 1 History, 2 Playlists, 3 Liked
    val list = if (tab == 0) favorites else history
    val onPlayIndex = if (tab == 0) onPlayFavorites else onPlayHistory
    val onClear = if (tab == 0) onClearFavorites else onClearHistory
    val showClear = tab <= 1 && list.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        ScreenTitle(title = "Library", subtitle = "Your favorites and recently played")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Default.Favorite, null, Modifier.size(FilterChipDefaults.IconSize)) })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("History") },
                leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(FilterChipDefaults.IconSize)) })
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Playlists") },
                leadingIcon = { Icon(Icons.Default.LibraryMusic, null, Modifier.size(FilterChipDefaults.IconSize)) })
            FilterChip(selected = tab == 3, onClick = { tab = 3 }, label = { Text("Liked") },
                leadingIcon = { Icon(Icons.Default.MusicNote, null, Modifier.size(FilterChipDefaults.IconSize)) })
            Spacer(modifier = Modifier.weight(1f))
            if (showClear) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                    Text("Clear", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        when (tab) {
            0, 1 ->
                run {
                    if (list.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                if (tab == 0) Icons.Default.FavoriteBorder else Icons.Default.History,
                                null,
                                tint = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(64.dp),
                            )
                            Text(
                                if (tab == 0) "No favorites yet" else "Nothing played yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                if (tab == 0) "Tap the heart on any song to save it here." else "Play a song and it'll show up here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                            items(list, key = { it.videoId }) { hit ->
                                val index = list.indexOf(hit)
                                ResultRow(
                                    hit = hit,
                                    isActive = hit.videoId == nowPlayingId,
                                    isBusy = busyId == hit.videoId,
                                    onClick = { onPlayIndex(index) },
                                    isFavorite = isFavorite(hit),
                                    onToggleFavorite = { onToggleFavorite(hit) },
                                )
                            }
                        }
                    }
                }
            2 ->
                if (!signedIn) {
                    SignInHint(modifier = Modifier.weight(1f))
                } else if (openPlaylistTitle != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onClosePlaylist) { Text("← Back") }
                            Text(
                                openPlaylistTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        when {
                            openPlaylistLoading -> {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                                }
                            }
                            openPlaylistTracks.isEmpty() -> {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    Text(
                                        "No tracks found",
                                        modifier = Modifier.align(Alignment.Center),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                    items(openPlaylistTracks, key = { it.videoId }) { hit ->
                                        val index = openPlaylistTracks.indexOf(hit)
                                        ResultRow(
                                            hit = hit,
                                            isActive = hit.videoId == nowPlayingId,
                                            isBusy = busyId == hit.videoId,
                                            onClick = { onPlayPlaylistTracks(index) },
                                            isFavorite = isFavorite(hit),
                                            onToggleFavorite = { onToggleFavorite(hit) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (playlistsLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                } else if (libraryPlaylists.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            "No saved playlists",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                        items(libraryPlaylists, key = { it.id }) { item ->
                            PlaylistRow(item = item, onClick = { onOpenPlaylist(item) })
                        }
                    }
                }
            3 ->
                if (!signedIn) {
                    SignInHint(modifier = Modifier.weight(1f))
                } else if (likedLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                } else if (likedSongs.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            "No liked songs",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                        items(likedSongs, key = { it.videoId }) { hit ->
                            val index = likedSongs.indexOf(hit)
                            ResultRow(
                                hit = hit,
                                isActive = hit.videoId == nowPlayingId,
                                isBusy = busyId == hit.videoId,
                                onClick = { onPlayLiked(index) },
                                isFavorite = isFavorite(hit),
                                onToggleFavorite = { onToggleFavorite(hit) },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun SignInHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Sign in to sync your library",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Use the Account tab to sign in to YouTube Music.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PlaylistRow(
    item: PlaylistHit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    item.subtitle,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onClick) {
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SettingsScreen(
    sections: List<SettingsSection>,
    onSelectSection: (SettingsSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
    ) {
        ScreenTitle(
            title = "Settings",
            subtitle = "Manage app preferences and settings"
        )
        
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(sections, key = { it.label }) { section ->
                SettingsSectionTile(
                    section = section,
                    onClick = { onSelectSection(section) }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTile(
    section: SettingsSection,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = section.label,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = section.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Navigate",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
        ScreenTitle(title = title, subtitle = subtitle)
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            item { content() }
        }
    }
}

@Composable
private fun SettingsRowItem(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val enabled = onClick != null
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(if (enabled) Modifier.clickable { onClick?.invoke() } else Modifier)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean = false,
    enabled: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val note = subtitle ?: if (enabled) null else "Not available on desktop"
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = {}, enabled = enabled)
        }
    }
}

@Composable
private fun SettingsSliderItem(
    title: String,
    value: Float = 0f,
    enabled: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Not available on desktop",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Slider(
                value = value,
                onValueChange = {},
                enabled = enabled,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsAppearanceScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Appearance", subtitle = "Theme and visual options", onBack = onBack) {
        SettingsRowItem("Theme", "Not available on desktop")
        SettingsToggleItem("Dynamic colors")
        SettingsToggleItem("Pure black theme")
        SettingsRowItem("Lyrics text position", "Not available on desktop")
        SettingsRowItem("Lyrics animation style", "Not available on desktop")
    }
}

@Composable
private fun SettingsContentScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Content", subtitle = "Language, country and lyrics", onBack = onBack) {
        SettingsRowItem("Content language", "Not available on desktop")
        SettingsRowItem("Content country", "Not available on desktop")
        SettingsRowItem("App language", "Not available on desktop")
        SettingsRowItem("Lyrics provider selection", "Not available on desktop")
        SettingsRowItem("Romanization", "Not available on desktop")
    }
}

@Composable
private fun SettingsAiScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "AI", subtitle = "AI translation and providers", onBack = onBack) {
        SettingsRowItem("AI provider", "Not available on desktop")
        SettingsRowItem("Translation mode", "Not available on desktop")
        SettingsRowItem("Target language", "Not available on desktop")
        SettingsRowItem("API key", "Not available on desktop")
        SettingsRowItem("Base URL", "Not available on desktop")
        SettingsRowItem("Model", "Not available on desktop")
        SettingsRowItem("System prompt", "Not available on desktop")
    }
}

@Composable
private fun SettingsPlayerScreen(onBack: () -> Unit, onOpenSub: (String) -> Unit) {
    SettingsScaffold(title = "Player", subtitle = "Playback and audio", onBack = onBack) {
        SettingsRowItem("Audio quality", "Not available on desktop")
        SettingsRowItem("Loudness level", "Not available on desktop")
        SettingsToggleItem("Crossfade")
        SettingsSliderItem("Crossfade duration")
        SettingsToggleItem("Gapless playback")
        SettingsRowItem("SponsorBlock", "Skip segments", onClick = { onOpenSub("sponsorblock") })
    }
}

@Composable
private fun SettingsStorageScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Storage", subtitle = "Downloads and cache", onBack = onBack) {
        SettingsRowItem("Downloaded songs", "Not available on desktop")
        SettingsRowItem("Clear all downloads", "Not available on desktop")
        SettingsToggleItem("Enable song cache")
        SettingsSliderItem("Max song cache size")
        SettingsRowItem("Clear song cache", "Not available on desktop")
        SettingsRowItem("Clear image cache", "Not available on desktop")
    }
}

@Composable
private fun SettingsPrivacyScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Privacy", subtitle = "History and data", onBack = onBack) {
        SettingsToggleItem("Pause listen history")
        SettingsRowItem("Clear listen history", "Not available on desktop")
        SettingsToggleItem("Pause search history")
        SettingsRowItem("Clear search history", "Not available on desktop")
        SettingsToggleItem("Disable screenshot")
    }
}

@Composable
private fun SettingsBackupScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Backup & restore", subtitle = "Export and import data", onBack = onBack) {
        SettingsRowItem("Backup", "Not available on desktop")
        SettingsRowItem("Restore", "Not available on desktop")
        SettingsRowItem("Import online", "Not available on desktop")
        SettingsRowItem("Import CSV", "Not available on desktop")
    }
}

@Composable
private fun SettingsIntegrationsScreen(onBack: () -> Unit, onOpenSub: (String) -> Unit) {
    SettingsScaffold(title = "Integrations", subtitle = "Connected services", onBack = onBack) {
        SettingsRowItem("Discord", "Rich presence", onClick = { onOpenSub("discord") })
        SettingsRowItem("Last.fm", "Scrobbling", onClick = { onOpenSub("lastfm") })
        SettingsRowItem("Listen Together", "Shared sessions", onClick = { onOpenSub("listen_together") })
        SettingsRowItem("Spotify", "Spotify features", onClick = { onOpenSub("spotify") })
        SettingsRowItem("Android Auto", "Car integration", onClick = { onOpenSub("android_auto") })
    }
}

@Composable
private fun SettingsUpdaterScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Updater", subtitle = "App updates", onBack = onBack) {
        SettingsRowItem("Current version", "Not available on desktop")
        SettingsToggleItem("Check for updates")
        SettingsToggleItem("Update notifications")
    }
}

@Composable
private fun SettingsAboutScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "About", subtitle = "MuSicX", onBack = onBack) {
        SettingsRowItem("MuSicX", "Desktop build")
        SettingsRowItem("Version", "Not available on desktop")
        SettingsRowItem("YouTube Music", "Not available on desktop")
        SettingsRowItem("Community", "Not available on desktop")
    }
}

@Composable
private fun SettingsEqualizerScreen(onBack: () -> Unit, onOpenSub: (String) -> Unit) {
    SettingsScaffold(title = "Equalizer", subtitle = "Audio tuning", onBack = onBack) {
        SettingsRowItem("Preset", "Not available on desktop")
        SettingsSliderItem("Bass boost")
        SettingsSliderItem("Virtualizer")
        SettingsRowItem("Eq wizard", "Guided calibration", onClick = { onOpenSub("eq_wizard") })
    }
}

@Composable
private fun SettingsSponsorBlockScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "SponsorBlock", subtitle = "Skip segments", onBack = onBack) {
        SettingsToggleItem("Skip sponsored segments")
        SettingsToggleItem("Skip self-promotion")
        SettingsToggleItem("Skip interaction reminders")
        SettingsToggleItem("Skip intros")
        SettingsToggleItem("Skip outros")
    }
}

@Composable
private fun SettingsDiscordScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Discord", subtitle = "Rich presence", onBack = onBack) {
        SettingsToggleItem("Enable rich presence")
        SettingsRowItem("Status", "Not available on desktop")
        SettingsToggleItem("Show activity name")
    }
}

@Composable
private fun SettingsLastFmScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Last.fm", subtitle = "Scrobbling", onBack = onBack) {
        SettingsToggleItem("Scrobble tracks")
        SettingsRowItem("Username", "Not available on desktop")
        SettingsRowItem("API key", "Not available on desktop")
    }
}

@Composable
private fun SettingsListenTogetherScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Listen Together", subtitle = "Shared sessions", onBack = onBack) {
        SettingsToggleItem("Enable Listen Together")
        SettingsRowItem("Host session", "Not available on desktop")
        SettingsRowItem("Room code", "Not available on desktop")
    }
}

@Composable
private fun SettingsSpotifyScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Spotify", subtitle = "Spotify features", onBack = onBack) {
        SettingsToggleItem("Enable Spotify integration")
        SettingsToggleItem("Preload tracks")
        SettingsRowItem("Spotify login", "Not available on desktop")
    }
}

@Composable
private fun SettingsAndroidAutoScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Android Auto", subtitle = "Car integration", onBack = onBack) {
        SettingsToggleItem("Enable Android Auto")
        SettingsRowItem("Car screen", "Not available on desktop")
    }
}

@Composable
private fun SettingsEqWizardScreen(onBack: () -> Unit) {
    SettingsScaffold(title = "Equalizer wizard", subtitle = "Guided calibration", onBack = onBack) {
        SettingsRowItem("Start wizard", "Not available on desktop")
        SettingsRowItem("Grant microphone access", "Not available on desktop")
        SettingsRowItem("Save profile", "Not available on desktop")
    }
}

@Composable
private fun LoginScreen(
    signedIn: Boolean,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var cookieText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showLoginWindow by remember { mutableStateOf(false) }

    if (showLoginWindow) {
        LoginWebViewWindow(
            onSignedIn = { cookie ->
                showLoginWindow = false
                onSignIn(cookie)
            },
            onClose = { showLoginWindow = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        ScreenTitle(title = "Account", subtitle = "Sign in to YouTube Music")
        Spacer(modifier = Modifier.height(24.dp))
        if (signedIn) {
            Text("Signed in", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSignOut) { Text("Sign out") }
        } else {
            Text(
                "Tap \"Sign in (embedded browser)\" to sign in right here, or use the system browser and paste the cookie below.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { showLoginWindow = true }) { Text("Sign in (embedded browser)") }
            Spacer(modifier = Modifier.height(4.dp))
            Text("or sign in via system browser and paste the cookie below:")
            TextButton(onClick = {
                runCatching { java.awt.Desktop.getDesktop().browse(URI("https://music.youtube.com")) }
            }) { Text("Open YouTube Music in browser") }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = cookieText,
                onValueChange = { cookieText = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cookie header") },
                singleLine = true,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = {
                val trimmed = cookieText.trim()
                if ("SAPISID=" in trimmed) {
                    onSignIn(trimmed)
                } else {
                    error = "Cookie must contain SAPISID= — make sure you copied the full cookie header."
                }
            }) { Text("Sign in") }
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
            modifier =
                Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            if (enabled && query.isNotBlank()) onSearch()
                            true
                        } else {
                            false
                        }
                    },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Song or artist") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (enabled && query.isNotBlank()) onSearch() }),
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
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
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
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<SearchHit>,
    currentIndex: Int,
    nowPlayingId: String?,
    busyId: String?,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onClose: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    isFavorite: (SearchHit) -> Boolean,
    onToggleFavorite: (SearchHit) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.width(340.dp).fillMaxHeight(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("Close") }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeatMode == RepeatMode.One) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat: $repeatMode",
                        tint = if (repeatMode != RepeatMode.Off) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    itemsIndexed(queue, key = { index, hit -> "$index-${hit.videoId}" }) { index, hit ->
                        ResultRow(
                            hit = hit,
                            isActive = hit.videoId == nowPlayingId,
                            isBusy = busyId == hit.videoId,
                            onClick = { onPlayIndex(index) },
                            isFavorite = isFavorite(hit),
                            onToggleFavorite = { onToggleFavorite(hit) },
                        )
                    }
                }
            }
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
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    hasQueue: Boolean = false,
    onToggleQueue: (() -> Unit)? = null,
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
                if (onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite, enabled = nowPlaying != null) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    IconButton(onClick = onToggleQueue ?: {}, enabled = hasQueue && onToggleQueue != null) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Queue")
                    }
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

/** In-memory thumbnail cache (capped LRU) so lists don't re-download artwork on every recompose/scroll. */
private const val MAX_IMAGE_CACHE_ENTRIES = 200

private val imageCache: MutableMap<String, ImageBitmap> =
    Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(MAX_IMAGE_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
                size > MAX_IMAGE_CACHE_ENTRIES
        },
    )

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
