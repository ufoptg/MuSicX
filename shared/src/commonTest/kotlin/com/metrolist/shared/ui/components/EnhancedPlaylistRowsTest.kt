package com.metrolist.shared.ui.components

import com.metrolist.shared.model.AlbumTiny
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedPlaylistRowsTest {
    private fun song(id: String) =
        SongItem(
            id = id,
            title = id,
            thumbnail = null,
            artists = listOf(ArtistTiny(null, "Artist")),
            album = AlbumTiny(null, "Album"),
        )

    private fun songs(count: Int) = List(count) { song("song$it") }

    private fun recommendations(count: Int) = List(count) { song("rec$it") }

    @Test
    fun enhancementDisabledReturnsOnlySongEntries() {
        val rows = buildEnhancedPlaylistRows(songs(4), recommendations(3), enableEnhancement = false)
        assertEquals(4, rows.size)
        assertTrue(rows.all { it is PlaylistRow.SongEntry })
        assertEquals(listOf(0, 1, 2, 3), rows.map { (it as PlaylistRow.SongEntry).queueIndex })
    }

    @Test
    fun oneRecommendationIsInsertedAfterEveryThreeSongs() {
        val rows = buildEnhancedPlaylistRows(songs(7), recommendations(5), enableEnhancement = true)
        val sequence =
            rows.map { row ->
                when (row) {
                    is PlaylistRow.SongEntry -> row.song.id
                    is PlaylistRow.Recommendation -> row.song.id
                }
            }
        // One recommendation after song2 and song5; the remaining three follow at the end.
        assertEquals(
            listOf("song0", "song1", "song2", "rec0", "song3", "song4", "song5", "rec1", "song6", "rec2", "rec3", "rec4"),
            sequence,
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), rows.filterIsInstance<PlaylistRow.SongEntry>().map { it.queueIndex })
    }

    @Test
    fun recommendationsAreCappedAtTwoHundred() {
        val rows = buildEnhancedPlaylistRows(songs(4), recommendations(MAX_ENHANCE_RECOMMENDATIONS + 25), enableEnhancement = true)
        val recs = rows.filterIsInstance<PlaylistRow.Recommendation>()
        assertEquals(MAX_ENHANCE_RECOMMENDATIONS, recs.size)
        assertEquals(List(MAX_ENHANCE_RECOMMENDATIONS) { it }, recs.map { it.index })
        // The cap is applied before interleaving so no more than 200 recommendations are emitted.
        assertEquals(MAX_ENHANCE_RECOMMENDATIONS + 4, rows.size)
    }

    @Test
    fun enhancementConstantsMatchTheInterleaveContract() {
        assertEquals(3, RECOMMENDATION_INTERVAL)
        assertEquals(200, MAX_ENHANCE_RECOMMENDATIONS)
    }
}
