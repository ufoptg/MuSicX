/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    var currentIndex by remember { mutableStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()

    val nowPlaying = results.getOrNull(currentIndex)

    fun playIndex(index: Int) {
        val hit = results.getOrNull(index) ?: return
        if (busyId != null) return
        scope.launch {
            busyId = hit.videoId
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
        if (currentIndex + 1 < results.size) playIndex(currentIndex + 1)
    }

    fun playPrevious() {
        if (currentIndex - 1 >= 0) playIndex(currentIndex - 1)
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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp)) {
            Header()
            SearchBar(query = query, onQueryChange = { query = it }, enabled = !loading, onSearch = ::runSearch)

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
                            isActive = hit.videoId == nowPlaying?.videoId,
                            isBusy = busyId == hit.videoId,
                            onClick = { playIndex(index) },
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
            hasNext = currentIndex + 1 < results.size,
            hasPrevious = currentIndex > 0,
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
        )
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(
            text = "MuSicX",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Search YouTube Music — tap a song to play",
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
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
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
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
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
            }

            val fraction =
                seekPreview ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = formatTime((seekPreview?.let { (it * durationMs).toLong() } ?: positionMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = fraction,
                    onValueChange = onSeekChange,
                    onValueChangeFinished = { onSeekCommit(seekPreview ?: fraction) },
                    enabled = nowPlaying != null && durationMs > 0,
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
    }
}

@Composable
private fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = null
        if (url.isNullOrBlank()) return@LaunchedEffect
        bitmap =
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = URL(url).openStream().use { it.readBytes() }
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
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
