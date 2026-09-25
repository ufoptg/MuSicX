/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DjStartRequest {
    /**
     * Normalize spoken/typed requests like "DJ 6 play God Mode by Eminem" → "God Mode by Eminem".
     */
    fun parsePlayQuery(raw: String): String {
        var q = raw.trim()
        if (q.isBlank()) return ""
        q =
            q
                .replace(Regex("""(?i)^\s*(hey\s+)?dj\s*6\b[,:]?\s*"""), "")
                .replace(Regex("""(?i)^\s*(please\s+)?(can you\s+)?(play|put on|queue|start)\b\s+"""), "")
                .replace(Regex("""(?i)^\s*some\s+"""), "")
                .trim()
                .trim('"', '\'', '.', '!', '?')
        return q
    }

    suspend fun resolveSeed(
        context: Context,
        request: String?,
        fallback: MediaMetadata?,
    ): MediaMetadata? =
        withContext(Dispatchers.IO) {
            val query = parsePlayQuery(request.orEmpty())
            if (query.isNotBlank()) {
                val song =
                    YouTube
                        .search(query, YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()
                        ?.items
                        ?.filterIsInstance<SongItem>()
                        ?.firstOrNull { it.id.isNotEmpty() }
                if (song != null) return@withContext song.toMediaMetadata()
                // Fall through to fallback if search misses
            }
            fallback
        }
}
