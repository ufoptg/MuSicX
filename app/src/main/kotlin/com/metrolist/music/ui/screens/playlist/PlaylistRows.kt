/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

/**
 * One recommended (Enhance) track is inserted after every [RECOMMENDATION_INTERVAL] songs.
 * Shared by the Local, Online (YouTube) and Spotify playlist screens so the interleave behaviour
 * stays identical across screens and can't drift.
 */
const val RECOMMENDATION_INTERVAL = 3

/**
 * A single row rendered inside a playlist list. [S] is the concrete song model of the screen
 * (Local `PlaylistSong`, Online `SongItem`, Spotify `SpotifyPlaylistTrack`) and [R] is the
 * recommendation model (`SongItem` for Local/Online, `SpotifyTrack` for Spotify) so every screen
 * reuses [buildPlaylistRows].
 */
sealed interface PlaylistRow<out S, out R> {
    /**
     * @param index the song's index within the *songs* list (NOT the mixed-row index) so callers
     * can start playback queues at the correct position even when recommendations are interleaved.
     */
    data class SongEntry<out S>(val index: Int, val song: S) : PlaylistRow<S, Nothing>

    data class Recommendation<out R>(val track: R) : PlaylistRow<Nothing, R>
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
fun <S, R> buildPlaylistRows(
    songEntries: List<PlaylistRow.SongEntry<S>>,
    recommendations: List<R>,
    interleave: Boolean,
): List<PlaylistRow<S, R>> {
    if (!interleave || recommendations.isEmpty()) return songEntries

    val rows = ArrayList<PlaylistRow<S, R>>(songEntries.size + recommendations.size)
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
