package dev.musicx.shared.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyMapperTest {

    @Test
    fun `normalizeTitle strips feat/ft/remaster/remix and non-alnum`() {
        val normalized = SpotifyMapper.normalizeTitle("Song (feat. Artist) [Remastered] - Remix")
        assertEquals("song artist remastered remix", normalized)
    }

    @Test
    fun `normalizeTitle preserves alphanumeric and spaces`() {
        assertEquals("hello world", SpotifyMapper.normalizeTitle("Hello World"))
        assertEquals("dont stop", SpotifyMapper.normalizeTitle("Don't Stop"))
        assertEquals("track 1", SpotifyMapper.normalizeTitle("Track #1"))
    }

    @Test
    fun `precompute generates expected structure`() {
        val pre = SpotifyMapper.precompute("Song Title", "Artist Name", 180000)
        assertEquals("song title", pre.normalizedTitle)
        assertEquals("artist name", pre.normalizedArtist)
        assertEquals(180000, pre.durationMs)
        assertTrue(pre.titleBigrams.isNotEmpty())
        assertTrue(pre.artistBigrams.isNotEmpty())
    }

    @Test
    fun `matchScorePrecomputed exact match returns 1.0`() {
        val pre = SpotifyMapper.precompute("Song", "Artist", 200000)
        val score = SpotifyMapper.matchScorePrecomputed(pre, "Song", "Artist", 200)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `matchScorePrecomputed close match scores high`() {
        val pre = SpotifyMapper.precompute("Song Title", "Artist Name", 200000)
        val score = SpotifyMapper.matchScorePrecomputed(pre, "Song Title", "Artist Name", 200)
        assertTrue("expected high score for identical track, got $score", score > 0.9)
    }

    @Test
    fun `matchScorePrecomputed wrong artist lowers score`() {
        val pre = SpotifyMapper.precompute("Song", "Correct Artist", 200000)
        val score = SpotifyMapper.matchScorePrecomputed(pre, "Song", "Wrong Artist", 200)
        assertTrue("expected lower score for wrong artist, got $score", score < 0.7)
    }

    @Test
    fun `matchScorePrecomputed duration mismatch penalizes`() {
        val pre = SpotifyMapper.precompute("Song", "Artist", 200000)
        // Duration off by 30 seconds
        val score = SpotifyMapper.matchScorePrecomputed(pre, "Song", "Artist", 170)
        assertTrue("expected penalty for 30s diff, got $score", score < 0.5)
    }

    @Test
    fun `earlyExitThreshold is constant`() {
        assertEquals(0.95, SpotifyMapper.earlyExitThreshold(), 0.001)
    }

    @Test
    fun `normalizeTitle handles empty and unicode`() {
        assertEquals("", SpotifyMapper.normalizeTitle(""))
        assertEquals("cafe", SpotifyMapper.normalizeTitle("Café"))
        assertEquals("nina", SpotifyMapper.normalizeTitle("Niña"))
    }

    @Test
    fun `bigramSimilarity identical strings is 1.0`() {
        val pre = SpotifyMapper.precompute("song", "artist", 1000)
        val s = SpotifyMapper.matchScorePrecomputed(pre, "song", "artist", 1)
        assertEquals(1.0, s, 0.001)
    }

    @Test
    fun `bigramSimilarity disjoint strings is 0.0`() {
        val pre = SpotifyMapper.precompute("abc", "xyz", 1000)
        val s = SpotifyMapper.matchScorePrecomputed(pre, "def", "uvw", 1)
        assertEquals(0.0, s, 0.001)
    }
}