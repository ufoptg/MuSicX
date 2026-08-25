package dev.ufoptg.musicx.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import dev.ufoptg.musicx.wear.PhoneBridge
import dev.ufoptg.musicx.wear.model.MediaItem
import dev.ufoptg.musicx.wear.model.NowPlaying
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ROOT = "root"

private sealed class Screen {
    data object Home : Screen()
    data class Browse(val parentId: String, val title: String) : Screen()
    data object Search : Screen()
}

@Composable
fun WearApp(appContext: android.content.Context) {
    val bridge = remember { PhoneBridge(appContext) }
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val nowPlaying = remember { mutableStateOf(NowPlaying()) }

    // Release the Data Layer listener when the app leaves the composition.
    DisposableEffect(bridge) { onDispose { bridge.close() } }

    // Pop the navigation stack on hardware back instead of exiting the app.
    androidx.activity.compose.BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }

    // Poll now-playing state while the app is visible.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { nowPlaying.value = bridge.nowPlaying() }
            delay(1000)
        }
    }

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = {},
        ) {
            val top = stack.last()
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (top) {
                        Screen.Home -> BrowsePane(
                            bridge = bridge,
                            parentId = ROOT,
                            title = "Library",
                            onOpen = { stack.add(Screen.Browse(it.mediaId, it.title)) },
                            onPlay = { scope.launch { bridge.play(it.mediaId) } },
                        )
                        is Screen.Browse -> BrowsePane(
                            bridge = bridge,
                            parentId = top.parentId,
                            title = top.title,
                            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                            onOpen = { stack.add(Screen.Browse(it.mediaId, it.title)) },
                            onPlay = { scope.launch { bridge.play(it.mediaId) } },
                        )
                        Screen.Search -> SearchPane(
                            bridge = bridge,
                            scope = scope,
                            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                            onPlay = { scope.launch { bridge.play(it.mediaId) } },
                        )
                    }
                }
                NowPlayingBar(
                    np = nowPlaying.value,
                    onPlayPause = {
                        scope.launch {
                            if (nowPlaying.value.isPlaying) bridge.pause() else bridge.resume()
                        }
                    },
                    onNext = { scope.launch { bridge.next() } },
                    onOpenSearch = { if (stack.last() !is Screen.Search) stack.add(Screen.Search) },
                )
            }
        }
    }
}

@Composable
private fun BrowsePane(
    bridge: PhoneBridge,
    parentId: String,
    title: String,
    onBack: () -> Unit = {},
    onOpen: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    var items by remember(parentId) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember(parentId) { mutableStateOf(true) }
    var error by remember(parentId) { mutableStateOf<String?>(null) }

    LaunchedEffect(parentId) {
        loading = true
        error = null
        runCatching { bridge.browse(parentId) }
            .onSuccess { items = it; loading = false }
            .onFailure { error = it.message ?: "Failed to load"; loading = false }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text(title, style = MaterialTheme.typography.title2) }
        when {
            loading -> item { Text("Loading…") }
            error != null -> item { Text(error ?: "") }
            items.isEmpty() -> item { Text("Nothing here yet") }
            else -> items(items) { item -> MediaRow(item = item, onOpen = onOpen, onPlay = onPlay) }
        }
    }
}

@Composable
private fun SearchPane(
    bridge: PhoneBridge,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .padding(vertical = 4.dp),
                textStyle = MaterialTheme.typography.body1
                    .copy(color = MaterialTheme.colors.onBackground),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = {
                    val q = query.trim()
                    if (q.isNotEmpty()) {
                        loading = true
                        results = emptyList()
                        scope.launch {
                            runCatching { bridge.search(q) }
                                .onSuccess { results = it; loading = false }
                                .onFailure { loading = false }
                        }
                    }
                }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().padding(8.dp)) {
                        if (query.isEmpty()) Text("Search songs…")
                        inner()
                    }
                },
            )
        }
        if (loading) item { Text("Searching…") }
        items(results) { item -> MediaRow(item = item, onOpen = {}, onPlay = onPlay) }
    }
}

@Composable
private fun MediaRow(item: MediaItem, onOpen: (MediaItem) -> Unit, onPlay: (MediaItem) -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        onClick = { if (item.browsable) onOpen(item) else onPlay(item) },
        label = {
            Column {
                Text(item.title, style = MaterialTheme.typography.body1, maxLines = 1)
                item.subtitle?.let { Text(it, style = MaterialTheme.typography.caption2, maxLines = 1) }
            }
        },
    )
}

@Composable
private fun NowPlayingBar(
    np: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onPlayPause) { Text(if (np.isPlaying) "❚❚" else "▶") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(np.title ?: "MuSicX", style = MaterialTheme.typography.caption1, maxLines = 1)
            np.artist?.let { Text(it, style = MaterialTheme.typography.caption2, maxLines = 1) }
        }
        Button(onClick = onNext) { Text("⏭") }
        Button(onClick = onOpenSearch) { Text("🔍") }
    }
}
