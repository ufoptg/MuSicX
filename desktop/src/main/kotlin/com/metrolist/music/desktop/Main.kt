/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch

fun main() = application {
    val client = remember { DesktopInnerTube() }
    val player = remember { DesktopAudioPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            player.close()
            client.close()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "MuSicX",
        icon = painterResource("ic_launcher.png"),
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SearchScreen(client, player)
            }
        }
    }
}

@Composable
private fun SearchScreen(
    client: DesktopInnerTube,
    player: DesktopAudioPlayer,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nowPlaying by remember { mutableStateOf<SearchHit?>(null) }
    var playbackBusy by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || loading) return
        scope.launch {
            loading = true
            error = null
            try {
                results = client.searchSongs(q)
                if (results.isEmpty()) {
                    error = "No songs found"
                }
            } catch (t: Throwable) {
                results = emptyList()
                error = t.message ?: t::class.simpleName ?: "Search failed"
            } finally {
                loading = false
            }
        }
    }

    fun playHit(hit: SearchHit) {
        if (playbackBusy) return
        scope.launch {
            playbackBusy = true
            error = null
            try {
                val stream = client.resolveAudioStream(hit.videoId)
                check(stream.sabrBootstrap == null) { "SABR streams are not supported yet" }
                player.play(stream)
                nowPlaying = hit
                playing = true
            } catch (t: Throwable) {
                error = t.message ?: t::class.simpleName ?: "Playback failed"
                playing = false
            } finally {
                playbackBusy = false
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        Text(
            text = "MuSicX",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Search YouTube Music",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Song or artist") },
                enabled = !loading,
            )
            IconButton(onClick = ::runSearch, enabled = !loading && query.isNotBlank()) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        when {
            loading -> {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(top = 48.dp)
                            .align(Alignment.CenterHorizontally),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(top = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(results, key = { it.videoId }) { hit ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !playbackBusy) { playHit(hit) }
                                    .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = hit.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (!hit.subtitle.isNullOrBlank()) {
                                Text(
                                    text = hit.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        nowPlaying?.let { hit ->
            HorizontalDivider()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (playbackBusy) "Loading…" else "Now playing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
                if (playbackBusy) {
                    CircularProgressIndicator()
                } else {
                    IconButton(
                        onClick = {
                            player.togglePause()
                            playing = player.isPlaying
                        },
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                        )
                    }
                    IconButton(
                        onClick = {
                            player.stop()
                            playing = false
                            nowPlaying = null
                        },
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
            }
        }
    }
}
