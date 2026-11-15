package dev.musicx.shared.spotify

import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.spotify.SpotifyYouTubeMapper.buildSearchQuery
import kotlinx.coroutines.runBlocking
import meow.nyx.spotlib.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyYouTubeMapperTest {

    @Test
    fun `buildSearchQuery constructs proper query`() {
        val track = TrackItem(
            uri = "spotify:track:123",
            name = "Song Title",
            artists = listOf("Artist Name"),
        )
        val query = buildSearchQuery(track)
        assertEquals("Artist Name Song Title", query)
    }

    @Test
    fun `buildSearchQuery handles missing artist`() {
        val track = TrackItem(
            uri = "spotify:track:123",
            name = "Song Title",
            artists = emptyList(),
        )
        val query = buildSearchQuery(track)
        assertEquals("Song Title", query)
    }

    @Test
    fun `mapToYouTube caches results in memory`() {
        val track = TrackItem(
            uri = "spotify:track:4iV5W9uYEdYUVa79Axb7Rh",
            name = "Test Song",
            artists = listOf("Test Artist"),
            durationMs = 200000L,
        )

        val fakeSong = SongItem(
            id = "youtube123",
            title = "Test Song",
            thumbnail = "https://example.com/thumb.jpg",
            artists = listOf(ArtistTiny("artist1", "Test Artist")),
            duration = 200000L,
        )

        val mapper = createMapperWithResult(fakeSong)

        // First call - hits YouTube
        val result1 = runBlocking { mapper.mapToYouTube(track) }
        assertNotNull(result1)
        assertEquals("youtube123", result1.id)

        // Second call - hits memory cache
        val result2 = runBlocking { mapper.mapToYouTube(track) }
        assertNotNull(result2)
        assertEquals("youtube123", result2.id)
    }

    @Test
    fun `mapToYouTube returns null when no match found`() {
        val track = TrackItem(
            uri = "spotify:track:123",
            name = "Nonexistent Song",
            artists = listOf("Unknown Artist"),
            durationMs = 200000L,
        )

        val mapper = createMapperWithEmptyResults()

        val result = runBlocking { mapper.mapToYouTube(track) }
        assertNull(result)
    }

    private fun createMapperWithEmptyResults(): SpotifyYouTubeMapper {
        return SpotifyYouTubeMapper { _ -> emptyList() }
    }

    private fun createMapperWithResult(song: SongItem): SpotifyYouTubeMapper {
        val repo = FakeYouTubeRepository(listOf(song))
        return SpotifyYouTubeMapper { query -> repo.searchResults }
    }

    private class FakeYouTubeRepository(
        private val results: List<SongItem>,
    ) {
        suspend fun searchSongs(query: String): List<SongItem> = results
    }
}