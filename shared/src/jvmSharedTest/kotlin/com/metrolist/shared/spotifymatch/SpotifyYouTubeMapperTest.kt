package com.metrolist.shared.spotifymatch

import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.SongItem
import meow.nyx.spotlib.TrackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotifyYouTubeMapperTest {
    @Test
    fun normalizeTitle_removesFeatAndFt() {
        assertEquals("song", SpotifyYouTubeMapper.normalizeTitle("Song (feat. Artist)"))
        assertEquals("song", SpotifyYouTubeMapper.normalizeTitle("Song (ft. Artist)"))
    }

    @Test
    fun normalizeTitle_removesRemasterRemix() {
        assertEquals("song", SpotifyYouTubeMapper.normalizeTitle("Song (Remastered)"))
        assertEquals("song", SpotifyYouTubeMapper.normalizeTitle("Song (Remaster 2020)"))
        assertEquals("song", SpotifyYouTubeMapper.normalizeTitle("Song (Remix)"))
        assertEquals("song", SpotifyYouTubeMapper.normalizeTitle("Song (Club Mix)"))
    }

    @Test
    fun normalizeTitle_removesBracketsAndNonAlnum() {
        assertEquals("song", SpotifyYouTubeMapper.normalizeTitle("[Official Video] Song!"))
        assertEquals("song official audio", SpotifyYouTubeMapper.normalizeTitle("Song (Official Audio)"))
        assertEquals("song artist", SpotifyYouTubeMapper.normalizeTitle("Song - Artist"))
    }

    @Test
    fun normalizeTitle_collapsesSpaces() {
        assertEquals("song artist", SpotifyYouTubeMapper.normalizeTitle("  Song    Artist  "))
    }

    @Test
    fun getBigrams_producesCorrectBigrams() {
        assertEquals(setOf(listOf("a", "b"), listOf("b", "c")), SpotifyYouTubeMapper.getBigrams("abc"))

        assertTrue(SpotifyYouTubeMapper.getBigrams("").isEmpty())

        assertTrue(SpotifyYouTubeMapper.getBigrams("a").isEmpty())
    }

    @Test
    fun bigramSimilarity_perfectMatch() {
        val a = setOf(listOf("a", "b"), listOf("b", "c"))
        val b = setOf(listOf("a", "b"), listOf("b", "c"))
        assertEquals(1.0, SpotifyYouTubeMapper.bigramSimilarity(a, b))
    }

    @Test
    fun bigramSimilarity_noOverlap() {
        val a = setOf(listOf("a", "b"))
        val b = setOf(listOf("c", "d"))
        assertEquals(0.0, SpotifyYouTubeMapper.bigramSimilarity(a, b))
    }

    @Test
    fun bigramSimilarity_partialOverlap() {
        val a = setOf(listOf("a", "b"), listOf("b", "c"))
        val b = setOf(listOf("b", "c"), listOf("c", "d"))
        assertEquals(0.5, SpotifyYouTubeMapper.bigramSimilarity(a, b))
    }

    @Test
    fun bigramSimilarity_emptySets() {
        assertEquals(0.0, SpotifyYouTubeMapper.bigramSimilarity(emptySet(), setOf(listOf("a", "b"))))
        assertEquals(0.0, SpotifyYouTubeMapper.bigramSimilarity(setOf(listOf("a", "b")), emptySet()))
    }

    @Test
    fun durationScore_perfectMatch() {
        assertEquals(1.0, SpotifyYouTubeMapper.durationScore(100.0, 100.0))
        assertEquals(1.0, SpotifyYouTubeMapper.durationScore(100.0, 101.0))
        assertEquals(1.0, SpotifyYouTubeMapper.durationScore(100.0, 98.0))
    }

    @Test
    fun durationScore_withinThresholds() {
        assertEquals(0.8, SpotifyYouTubeMapper.durationScore(100.0, 103.0))
        assertEquals(0.8, SpotifyYouTubeMapper.durationScore(100.0, 95.0))
        assertEquals(0.5, SpotifyYouTubeMapper.durationScore(100.0, 107.0))
        assertEquals(0.5, SpotifyYouTubeMapper.durationScore(100.0, 90.0))
        assertEquals(0.2, SpotifyYouTubeMapper.durationScore(100.0, 115.0))
        assertEquals(0.2, SpotifyYouTubeMapper.durationScore(100.0, 80.0))
    }

    @Test
    fun durationScore_zeroOrNegative() {
        assertEquals(0.5, SpotifyYouTubeMapper.durationScore(100.0, 0.0))
        assertEquals(0.5, SpotifyYouTubeMapper.durationScore(0.0, 100.0))
        assertEquals(0.5, SpotifyYouTubeMapper.durationScore(-10.0, 100.0))
    }

    @Test
    fun buildSearchQuery_combinesTitleAndArtists() {
        val track = TrackItem(uri = "spotify:track:123", name = "Song Title", artists = listOf("Artist One", "Artist Two"))
        assertEquals("Song Title Artist One, Artist Two", SpotifyYouTubeMapper.buildSearchQuery(track))
    }

    @Test
    fun buildSearchQuery_handlesEmptyArtists() {
        val track = TrackItem(uri = "spotify:track:123", name = "Song Title", artists = emptyList())
        assertEquals("Song Title", SpotifyYouTubeMapper.buildSearchQuery(track))
    }

    @Test
    fun buildSearchQuery_trims() {
        val track = TrackItem(uri = "spotify:track:123", name = "  Song  ", artists = listOf("  Artist  "))
        assertEquals("Song Artist", SpotifyYouTubeMapper.buildSearchQuery(track))
    }

    @Test
    fun matchScorePrecomputed_basic() {
        val pre =
            SpotifyYouTubeMapper.PrecomputedTrack(
                uri = "spotify:track:123",
                titleNorm = "song",
                artistNorm = "artist",
                durationMs = 200000L,
                titleBigrams = setOf(listOf("s", "o"), listOf("o", "n"), listOf("n", "g")),
                artistBigrams = setOf(listOf("a", "r"), listOf("r", "t"), listOf("t", "i"), listOf("i", "s"), listOf("s", "t")),
            )

        val candidate =
            SongItem(
                id = "youtube-id",
                title = "Song",
                thumbnail = null,
                artists = listOf(ArtistTiny(id = null, name = "Artist")),
                album = null,
                duration = 200L,
                isExplicit = false,
                endpointParams = null,
            )

        val score = SpotifyYouTubeMapper.matchScorePrecomputed(pre, candidate)
        assertTrue(score > 0.8)
    }

    @Test
    fun precompute_cachesResult() {
        val track = TrackItem(uri = "spotify:track:123", name = "Test Song", artists = listOf("Test Artist"), durationMs = 123456L)

        val first = SpotifyYouTubeMapper.precompute(track)
        assertEquals(track.uri, first.uri)
        assertEquals(123456L, first.durationMs)

        val second = SpotifyYouTubeMapper.precompute(track)
        assertEquals(first, second)
    }
}
