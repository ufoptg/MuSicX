package com.metrolist.music.desktop

import com.metrolist.music.desktop.lyrics.LrcLib

/**
 * Desktop lyrics provider. Fetches synced/plain lyrics from LRCLIB.
 */
object DesktopLyrics {
    suspend fun fetchLyrics(title: String, artist: String, durationSeconds: Int, album: String? = null): String? {
        return try {
            LrcLib.getLyrics(title, artist, durationSeconds, album).getOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
