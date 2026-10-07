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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
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
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
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

@Composable
private fun MuSicXTheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    content: @Composable () -> Unit,
) {
    val baseColorScheme =
        rememberDynamicColorScheme(
            seedColor = MuSicXRed,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot,
        )
    val colorScheme =
        remember(baseColorScheme, pureBlack, darkTheme) {
            if (darkTheme && pureBlack) {
                baseColorScheme.copy(surface = Color.Black, background = Color.Black)
            } else {
                baseColorScheme
            }
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

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
    History("History", Icons.Default.History),
    Stats("Stats", Icons.Default.Insights),
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

/**
 * Detail screen type for the current route.
 */
enum class DetailType { Album, Artist, Playlist, Podcast }

/**
 * Holds the current detail screen state.
 */
data class DetailState(
    val type: DetailType,
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    val tracks: List<SearchHit>,
    val loading: Boolean,
)

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

    var prefs by remember { mutableStateOf(DesktopPrefsStore.load()) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "MuSicX",
        icon = painterResource("ic_launcher.png"),
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        val systemDark = isSystemInDarkTheme()
        val darkTheme =
            when (prefs.darkMode.uppercase()) {
                "ON" -> true
                "OFF" -> false
                else -> systemDark
            }
        MuSicXTheme(darkTheme = darkTheme, pureBlack = prefs.pureBlack) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MuSicXApp(client, player, prefs) { prefs = it }
            }
        }
    }
}

@Composable
private fun MuSicXApp(
    client: DesktopInnerTube,
    player: DesktopAudioPlayer,
    prefs: DesktopPrefs,
    onPrefsChange: (DesktopPrefs) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var songResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var artistResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<PlaylistHit>>(emptyList()) }
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
    var downloads by remember { mutableStateOf<List<DownloadInfo>>(emptyList()) }
    var downloadingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val downloader = remember { DesktopDownloads(client, player) }
    val scope = rememberCoroutineScope()

    // Restore persisted favorites/history, then keep them on disk.
    LaunchedEffect(Unit) {
        val data = withContext(Dispatchers.IO) { DesktopLibraryStore.load() }
        favorites = data.favorites
        history = data.history
        downloads = data.downloads
    }

    fun persistLibrary() {
        val snapshot = DesktopLibraryData(favorites = favorites, history = history, downloads = downloads)
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

    fun isDownloaded(hit: SearchHit?): Boolean = hit != null && downloads.any { it.videoId == hit.videoId }

    fun toggleDownload(hit: SearchHit) {
        if (hit.videoId in downloadingIds) return
        val existing = downloads.firstOrNull { it.videoId == hit.videoId }
        if (existing != null) {
            downloads = downloads.filterNot { it.videoId == hit.videoId }
            persistLibrary()
            scope.launch(Dispatchers.IO) { downloader.delete(existing) }
            return
        }
        scope.launch {
            downloadingIds = downloadingIds + hit.videoId
            try {
                val info = downloader.download(hit)
                downloads = listOf(info) + downloads.filterNot { it.videoId == hit.videoId }
                persistLibrary()
            } catch (t: Throwable) {
                DesktopLog.log("download failed for ${hit.videoId}", t)
                error = "Download failed: ${t.message ?: t::class.simpleName}"
            } finally {
                downloadingIds = downloadingIds - hit.videoId
            }
        }
    }

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
    var openDetail by remember { mutableStateOf<DetailState?>(null) }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var settingsSubScreen by remember { mutableStateOf<String?>(null) }
    var spotifyHome by remember { mutableStateOf(false) }

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
                val local = downloader.localFile(hit.videoId, downloads)
                if (local != null) {
                    DesktopLog.log("playHit: playing downloaded file ${local.name}")
                    player.playFile(local)
                } else {
                    DesktopLog.log("playHit: resolving ${hit.videoId} (${hit.title})")
                    val resolveStart = System.currentTimeMillis()
                    val stream = withContext(Dispatchers.IO) { client.resolveAudioStream(hit.videoId) }
                    DesktopLog.log("playHit: resolved in ${System.currentTimeMillis() - resolveStart} ms")
                    player.play(stream)
                }
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
                songResults = client.searchSongs(q)
                albumResults = client.searchAlbums(q)
                artistResults = client.searchArtists(q)
                playlistResults = client.searchPlaylists(q)
                if (songResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty() && playlistResults.isEmpty()) {
                    error = "No results found"
                }
            } catch (t: Throwable) {
                songResults = emptyList()
                albumResults = emptyList()
                artistResults = emptyList()
                playlistResults = emptyList()
                error = t.message ?: t::class.simpleName ?: "Search failed"
            } finally {
                loading = false
            }
        }
    }

    fun loadHome() {
        if (homeLoading || homeRows.isNotEmpty() || spotifyHome) return
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
                                    songResults = songResults,
                                    albumResults = albumResults,
                                    artistResults = artistResults,
                                    playlistResults = playlistResults,
                                    nowPlayingId = nowPlaying?.videoId,
                                    busyId = busyId,
                                    onSearch = ::runSearch,
                                    onPlayIndex = { index -> playFrom(songResults, index) },
                                    isFavorite = ::isFavorite,
                                    onToggleFavorite = ::toggleFavorite,
                                    onOpenPlaylist = { hit -> openDetail = DetailState(DetailType.Playlist, hit.id, hit.title, hit.subtitle, hit.thumbnailUrl, emptyList(), true); scope.launch(Dispatchers.IO) { val tracks = runCatching { client.playlistTracks(hit.id) }.getOrDefault(emptyList()); openDetail = openDetail?.copy(tracks = tracks, loading = false) } },
                                )
                            Destination.History ->
                                HistoryScreen(
                                    history = history,
                                    nowPlayingId = nowPlaying?.videoId,
                                    busyId = busyId,
                                    onPlay = { index -> playFrom(history, index) },
                                    onClearAll = {
                                        history = emptyList()
                                        persistLibrary()
                                    },
                                    isFavorite = ::isFavorite,
                                    onToggleFavorite = ::toggleFavorite,
                                )
                            Destination.Stats ->
                                StatsScreen(
                                    history = history,
                                    favorites = favorites,
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
                                    settingsSubScreen == "eq_wizard" ->
                                        SettingsEqWizardScreen(onBack = { settingsSubScreen = null })
                                    settingsSection == SettingsSection.Appearance ->
                                        SettingsAppearanceScreen(
                                            onBack = { settingsSection = null },
                                            prefs = prefs,
                                            onPrefsChange = onPrefsChange,
                                        )
                                    settingsSection == SettingsSection.Content ->
                                        SettingsContentScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.AI ->
                                        SettingsAiScreen(onBack = { settingsSection = null })
                                    settingsSection == SettingsSection.Player ->
                                        SettingsPlayerScreen(
                                            onBack = { settingsSection = null },
                                            onOpenSub = { settingsSubScreen = it },
                                            prefs = prefs,
                                            onPrefsChange = onPrefsChange,
                                        )
                                    settingsSection == SettingsSection.Storage ->
                                        SettingsStorageScreen(
                                            onBack = { settingsSection = null },
                                            downloads = downloads,
                                            downloadsDir = downloader.dir,
                                            onClearDownloads = {
                                                downloads = emptyList()
                                                persistLibrary()
                                                scope.launch(Dispatchers.IO) { downloader.clearAll() }
                                            },
                                        )
                                    settingsSection == SettingsSection.Privacy ->
                                        SettingsPrivacyScreen(
                                            onBack = { settingsSection = null },
                                            prefs = prefs,
                                        )
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
                                    openDetail = openDetail,
                                    downloads = downloads,
                                    onPlayDownloads = { index -> playFrom(downloads.map { it.toHit() }, index) },
                                    onRemoveDownload = { info -> toggleDownload(info.toHit()) },
                                    onOpenPlaylist = { item ->
                                        openDetail = DetailState(
                                            type = DetailType.Playlist,
                                            id = item.id,
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            thumbnailUrl = item.thumbnailUrl,
                                            tracks = emptyList(),
                                            loading = true,
                                        )
                                        scope.launch(Dispatchers.IO) {
                                            val tracks =
                                                runCatching { client.playlistTracks(item.id) }
                                                    .getOrDefault(emptyList())
                                            openDetail = openDetail?.copy(tracks = tracks, loading = false)
                                        }
                                    },
                                    onClosePlaylist = {
                                        openDetail = null
                                    },
                                    onPlayLiked = { index -> playFrom(likedSongs, index) },
                                    onPlayPlaylistTracks = { index -> playFrom(openDetail?.tracks ?: emptyList(), index) },
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
                isDownloaded = isDownloaded(nowPlaying),
                isDownloading = nowPlaying?.videoId in downloadingIds,
                onToggleDownload = { nowPlaying?.let(::toggleDownload) },
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
                isDownloaded = ::isDownloaded,
                isDownloading = { it.videoId in downloadingIds },
                onToggleDownload = ::toggleDownload,
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
private fun HistoryScreen(
    history: List<SearchHit>,
    nowPlayingId: String?,
    busyId: String?,
    onPlay: (Int) -> Unit,
    onClearAll: () -> Unit,
    isFavorite: (SearchHit) -> Boolean,
    onToggleFavorite: (SearchHit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        ScreenTitle(title = "History", subtitle = "Recently played")
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No history yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    TextButton(onClick = onClearAll) { Text("Clear all") }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(history, key = { it.videoId }) { hit ->
                        val index = history.indexOf(hit)
                        ResultRow(
                            hit = hit,
                            isActive = hit.videoId == nowPlayingId,
                            isBusy = busyId == hit.videoId,
                            onClick = { onPlay(index) },
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
private fun StatsScreen(
    history: List<SearchHit>,
    favorites: List<SearchHit>,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        ScreenTitle(title = "Stats", subtitle = "Your listening summary")
        Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Total plays: ${history.size}", style = MaterialTheme.typography.titleMedium)
            Text("Favorites: ${favorites.size}", style = MaterialTheme.typography.titleMedium)
            val topArtists = history.groupBy { it.subtitle ?: "Unknown" }.mapValues { it.value.size }.toList().sortedByDescending { it.second }.take(5)
            Text("Top artists:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            topArtists.forEach { (artist, count) ->
                Text("  $artist: $count plays", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    songResults: List<SearchHit>,
    albumResults: List<SearchHit>,
    artistResults: List<SearchHit>,
    playlistResults: List<PlaylistHit>,
    nowPlayingId: String?,
    busyId: String?,
    onSearch: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    isFavorite: (SearchHit) -> Boolean,
    onToggleFavorite: (SearchHit) -> Unit,
    onOpenPlaylist: (PlaylistHit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        ScreenTitle(title = "Search", subtitle = "Songs, albums, artists, playlists")
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
                if (songResults.isNotEmpty()) {
                    item { Text("Songs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                    items(songResults, key = { it.videoId }) { hit ->
                        val index = songResults.indexOf(hit)
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

                if (albumResults.isNotEmpty()) {
                    item { Text("Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                    items(albumResults, key = { it.videoId }) { hit ->
                        val index = albumResults.indexOf(hit)
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

                if (artistResults.isNotEmpty()) {
                    item { Text("Artists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                    items(artistResults, key = { it.videoId }) { hit ->
                        val index = artistResults.indexOf(hit)
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

                if (playlistResults.isNotEmpty()) {
                    item { Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                    items(playlistResults, key = { it.id }) { hit ->
                        PlaylistRow(item = hit, onClick = { onOpenPlaylist(hit) })
                    }
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
    openDetail: DetailState?,
    onOpenPlaylist: (PlaylistHit) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlayLiked: (Int) -> Unit,
    onPlayPlaylistTracks: (Int) -> Unit,
    downloads: List<DownloadInfo>,
    onPlayDownloads: (Int) -> Unit,
    onRemoveDownload: (DownloadInfo) -> Unit,
) {
    var tab by remember { mutableStateOf(0) } // 0 Favorites, 1 History, 2 Playlists, 3 Liked, 4 Downloads
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
            FilterChip(selected = tab == 4, onClick = { tab = 4 }, label = { Text("Downloads") },
                leadingIcon = { Icon(Icons.Default.Download, null, Modifier.size(FilterChipDefaults.IconSize)) })
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
                } else if (openDetail != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onClosePlaylist) { Text("← Back") }
                            Text(
                                openDetail.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        when {
                            openDetail.loading -> {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                                }
                            }
                            openDetail.tracks.isEmpty() -> {
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
                                    items(openDetail.tracks, key = { it.videoId }) { hit ->
                                        val index = openDetail.tracks.indexOf(hit)
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
            4 ->
                if (downloads.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            "No downloads yet. Tap the download icon on a song in the queue or player.",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                        items(downloads, key = { it.videoId }) { info ->
                            val hit = info.toHit()
                            ResultRow(
                                hit = hit,
                                isActive = hit.videoId == nowPlayingId,
                                isBusy = busyId == hit.videoId,
                                onClick = { onPlayDownloads(downloads.indexOf(info)) },
                                isFavorite = isFavorite(hit),
                                onToggleFavorite = { onToggleFavorite(hit) },
                                isDownloaded = true,
                                onToggleDownload = { onRemoveDownload(info) },
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
    onCheckedChange: (Boolean) -> Unit = {},
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
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
private fun SettingsAppearanceScreen(
    onBack: () -> Unit,
    prefs: DesktopPrefs,
    onPrefsChange: (DesktopPrefs) -> Unit,
) {
    val update: (DesktopPrefs) -> Unit = { updated ->
        DesktopPrefsStore.save(updated)
        onPrefsChange(updated)
    }
    val darkChecked = prefs.darkMode.uppercase() != "OFF"
    SettingsScaffold(title = "Appearance", subtitle = "Theme and visual options", onBack = onBack) {
        SettingsToggleItem(
            "Dark mode",
            subtitle = "Current: ${prefs.darkMode.uppercase()}",
            checked = darkChecked,
            enabled = true,
            onCheckedChange = { enabled -> update(prefs.copy(darkMode = if (enabled) "ON" else "OFF")) },
        )
        SettingsRowItem(
            "Follow system theme",
            if (prefs.darkMode.uppercase() == "AUTO") "On" else "Off",
            onClick = {
                update(
                    prefs.copy(
                        darkMode = if (prefs.darkMode.uppercase() == "AUTO") "ON" else "AUTO",
                    ),
                )
            },
        )
        SettingsToggleItem("Dynamic colors", subtitle = "Coral seed color", checked = true, enabled = false)
        SettingsToggleItem(
            "Pure black theme",
            checked = prefs.pureBlack,
            enabled = true,
            onCheckedChange = { enabled -> update(prefs.copy(pureBlack = enabled)) },
        )
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
private fun SettingsPlayerScreen(onBack: () -> Unit, onOpenSub: (String) -> Unit, prefs: DesktopPrefs, onPrefsChange: (DesktopPrefs) -> Unit) {
    val update: (DesktopPrefs) -> Unit = { updated ->
        DesktopPrefsStore.save(updated)
        onPrefsChange(updated)
    }
    SettingsScaffold(title = "Player", subtitle = "Playback and audio", onBack = onBack) {
        SettingsRowItem("Audio quality", "Not available on desktop")
        SettingsRowItem("Loudness level", "Not available on desktop")
        SettingsToggleItem("Crossfade", checked = prefs.playerCrossfade, onCheckedChange = { enabled -> update(prefs.copy(playerCrossfade = enabled)) })
        SettingsSliderItem("Crossfade duration")
        SettingsToggleItem("Gapless playback", checked = prefs.playerGapless, onCheckedChange = { enabled -> update(prefs.copy(playerGapless = enabled)) })
        SettingsRowItem("Sleep timer", when (prefs.sleepTimerMinutes) { 0 -> "Off"; 1 -> "15 min"; 2 -> "30 min"; 3 -> "45 min"; 4 -> "60 min"; 5 -> "End of track"; else -> "Off" }, onClick = {
            // Simple cycle for now; a real UI would use a dialog
            update(prefs.copy(sleepTimerMinutes = (prefs.sleepTimerMinutes + 1) % 6))
        })
        SettingsRowItem("SponsorBlock", "Skip segments", onClick = { onOpenSub("sponsorblock") })
    }
}

@Composable
private fun SettingsStorageScreen(
    onBack: () -> Unit,
    downloads: List<DownloadInfo>,
    downloadsDir: java.io.File,
    onClearDownloads: () -> Unit,
) {
    var refresh by remember { mutableStateOf(0) }
    var downloadBytes by remember { mutableStateOf(0L) }
    var songCacheBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(downloads, refresh) {
        withContext(Dispatchers.IO) {
            downloadBytes = DesktopDownloads.dirSize(downloadsDir)
            songCacheBytes = DesktopDownloads.songCacheBytes()
        }
    }
    SettingsScaffold(title = "Storage", subtitle = "Downloads and cache", onBack = onBack) {
        SettingsRowItem(
            "Downloaded songs",
            "${downloads.size} songs · ${DesktopDownloads.formatBytes(downloadBytes)}",
        )
        SettingsRowItem(
            "Clear all downloads",
            "Delete every downloaded song",
            onClick = if (downloads.isEmpty()) null else onClearDownloads,
        )
        SettingsToggleItem("Enable song cache", subtitle = "Not available on desktop")
        SettingsSliderItem("Max song cache size")
        SettingsRowItem(
            "Clear song cache",
            DesktopDownloads.formatBytes(songCacheBytes),
            onClick = {
                DesktopDownloads.clearSongCache()
                refresh++
            },
        )
        SettingsRowItem(
            "Clear image cache",
            "${imageCache.size} images in memory",
            onClick = {
                imageCache.clear()
                refresh++
            },
        )
    }
}

@Composable
private fun SettingsPrivacyScreen(onBack: () -> Unit, prefs: DesktopPrefs) {
    val update: (DesktopPrefs) -> Unit = { updated ->
        DesktopPrefsStore.save(updated)
    }
    SettingsScaffold(title = "Privacy", subtitle = "History and data", onBack = onBack) {
        SettingsToggleItem("Pause listen history", checked = prefs.pauseHistory, onCheckedChange = { enabled -> update(prefs.copy(pauseHistory = enabled)) })
        SettingsRowItem("Clear listen history", if (prefs.pauseHistory) "History stored in memory" else "Not available on desktop")
        SettingsToggleItem("Pause search history", checked = prefs.clearCacheOnExit, onCheckedChange = { enabled -> update(prefs.copy(clearCacheOnExit = enabled)) })
        SettingsRowItem("Clear search history", if (prefs.clearCacheOnExit) "Cache cleared on exit" else "Not available on desktop")
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
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
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
        if (onToggleDownload != null) {
            DownloadButton(isDownloaded, isDownloading, onToggleDownload)
        }
    }
}

@Composable
private fun DownloadButton(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle, enabled = !isDownloading) {
        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                contentDescription = if (isDownloaded) "Remove download" else "Download",
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    isDownloaded: (SearchHit) -> Boolean,
    isDownloading: (SearchHit) -> Boolean,
    onToggleDownload: (SearchHit) -> Unit,
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
                            isDownloaded = isDownloaded(hit),
                            isDownloading = isDownloading(hit),
                            onToggleDownload = { onToggleDownload(hit) },
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
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onToggleDownload: () -> Unit,
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
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                DownloadButton(isDownloaded, isDownloading, onToggleDownload)
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
