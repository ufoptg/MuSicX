/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import com.metrolist.innertube.models.SongItem

/**
 * One recommended (Enhance) track is inserted after every [RECOMMENDATION_INTERVAL] songs.
 * Shared by both [LocalPlaylistScreen] and [OnlinePlaylistScreen] so the interleave behaviour
 * stays identical across screens and can't drift.
 */
const val RECOMMENDATION_INTERVAL = 3

/**
 * A single row rendered inside a playlist list. [T] is the concrete song model of the screen
 * (local uses `PlaylistSong`, online uses `SongItem`) so both screens reuse [buildPlaylistRows].
 */
sealed interface PlaylistRow<out T> {
    /**
     * @param index the song's index within the *songs* list (NOT the mixed-row index) so callers
     * can start playback queues at the correct position even when recommendations are interleaved.
     */
    data class SongEntry<out T>(val index: Int, val song: T) : PlaylistRow<T>

    data class Recommendation(val track: SongItem) : PlaylistRow<Nothing>
}

/**
 * Interleaves [recommendations] through [songEntries]. When [interleave] is on (the Enhance
 * toggle), one recommendation is inserted after every [RECOMMENDATION_INTERVAL] displayed songs
 * instead of dumping them at the bottom. Any leftover recommendations beyond the last interval
 * are appended at the end. When [interleave] is off (or there are no recommendations), the songs
 * are returned unchanged.
 *
 * The interval is based on the *display position* of each entry, while [PlaylistRow.SongEntry.index]
 * carries the original songs-list index used for queue start positions.
 */
fun <T> buildPlaylistRows(
    songEntries: List<PlaylistRow.SongEntry<T>>,
    recommendations: List<SongItem>,
    interleave: Boolean,
): List<PlaylistRow<T>> {
    if (!interleave || recommendations.isEmpty()) return songEntries

    val rows = ArrayList<PlaylistRow<T>>(songEntries.size + recommendations.size)
    var recPtr = 0
    songEntries.forEachIndexed { position, entry ->
        rows.add(entry)
        if ((position + 1) % RECOMMENDATION_INTERVAL == 0 && recPtr < recommendations.size) {
            rows.add(PlaylistRow.Recommendation(recommendations[recPtr++]))
        }
    }
    while (recPtr < recommendations.size) {
        rows.add(PlaylistRow.Recommendation(recommendations[recPtr++]))
    }
    return rows
}
