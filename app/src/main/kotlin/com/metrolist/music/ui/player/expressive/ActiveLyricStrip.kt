/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0
 *
 * Phase 6 of the Expressive Player redesign — a one-line "active lyric"
 * strip that sits directly under the song title / artist on Now Playing.
 *
 * It reuses the existing lyric plumbing rather than adding new state:
 *   * `PlayerConnection.currentLyrics` gives the LyricsEntity for the track.
 *   * `LyricsUtils.parseLyrics` turns the raw LRC into timestamped entries.
 *   * `LyricsUtils.findCurrentLineIndex(lines, position)` is the picker —
 *     it returns the last line whose timestamp is <= the playback position.
 *
 * Only synced (LRC-timestamped) lyrics can drive this; unsynced lyrics or a
 * "not found" result collapse the strip to nothing. Guarded behind the
 * Expressive player flag at the call site so the classic view is unchanged.
 */
package com.metrolist.music.ui.player.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Cheap detector for LRC-style timestamps ([mm:ss...]). Mirrors the check the
// LyricsViewModel uses so we only bother parsing when the strip can be driven.
private val SYNCED_TIMESTAMP_REGEX = Regex("\\[\\d{1,2}:\\d{2}")

@Composable
fun ActiveLyricStrip(
    positionProvider: () -> Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val lyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    val rawLyrics = lyricsEntity?.lyrics?.trim()

    // Parse off the main thread; recomputes only when the lyrics string changes.
    val lines by produceState<List<LyricsEntry>>(initialValue = emptyList(), rawLyrics) {
        val lyrics = rawLyrics
        value = if (lyrics.isNullOrBlank() ||
            lyrics == LYRICS_NOT_FOUND ||
            !SYNCED_TIMESTAMP_REGEX.containsMatchIn(lyrics)
        ) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) { LyricsUtils.parseLyrics(lyrics) }
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        while (true) {
            positionMs = positionProvider()
            delay(250)
        }
    }

    val activeText = remember(lines, positionMs) {
        if (lines.isEmpty()) {
            null
        } else {
            lines.getOrNull(LyricsUtils.findCurrentLineIndex(lines, positionMs))
                ?.text
                ?.takeIf { it.isNotBlank() }
        }
    }

    AnimatedContent(
        targetState = activeText,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "active-lyric-strip",
        modifier = modifier,
    ) { text ->
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    iterations = 1,
                    initialDelayMillis = 2000,
                    velocity = 30.dp,
                ),
            )
        } else {
            // No synced line for the current position — take up no space.
            Box(Modifier)
        }
    }
}
