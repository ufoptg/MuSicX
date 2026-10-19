package com.metrolist.music.desktop

import com.metrolist.spotify.models.SpotifySimpleArtist
import com.metrolist.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopSpotifyMatcherTest {
    private val track =
        SpotifyTrack(
            name = "Bohemian Rhapsody",
            artists = listOf(SpotifySimpleArtist(name = "Queen")),
            durationMs = 354000,
        )

    @Test
    fun picksBestScoringCandidate() {
        val candidates =
            listOf(
                SearchHit("a", "Some Other Song", "Nobody"),
                SearchHit("b", "Bohemian Rhapsody", "Queen"),
                SearchHit("c", "Bohemian Rhapsody (Remix)", "Queen"),
            )
        assertEquals("b", bestMatch(track, candidates)?.videoId)
    }

    @Test
    fun rejectsUnrelatedCandidates() {
        assertNull(bestMatch(track, listOf(SearchHit("x", "Completely Different", "Another Artist"))))
    }

    @Test
    fun emptyCandidatesReturnNull() {
        assertNull(bestMatch(track, emptyList()))
    }
}
