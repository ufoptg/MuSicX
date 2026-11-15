package dev.musicx.shared.spotify

import dev.musicx.shared.model.SongItem
import meow.nyx.spotlib.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyMetadataRegistryTest {

    @Test
    fun `register and get works`() {
        SpotifyMetadataRegistry.clearAll()
        val track = SongItem(
            id = "youtube123",
            title = "Test Song",
            thumbnail = "https://example.com/thumb.jpg",
            artists = listOf(dev.musicx.shared.model.ArtistTiny("artist1", "Test Artist")),
            duration = 200000L,
        )

        SpotifyMetadataRegistry.register("youtube123", track)

        val result = SpotifyMetadataRegistry.get("youtube123")
        assertEquals("Test Song", result?.title)
    }

    @Test
    fun `get returns null for unknown key`() {
        SpotifyMetadataRegistry.clearAll()
        assertNull(SpotifyMetadataRegistry.get("unknown"))
    }

    @Test
    fun `invalidate removes entry`() {
        SpotifyMetadataRegistry.clearAll()
        val track = SongItem(
            id = "youtube123",
            title = "Test Song",
            thumbnail = "https://example.com/thumb.jpg",
            artists = listOf(dev.musicx.shared.model.ArtistTiny("artist1", "Test Artist")),
            duration = 200000L,
        )

        SpotifyMetadataRegistry.register("youtube123", track)
        assertTrue(SpotifyMetadataRegistry.contains("youtube123"))

        SpotifyMetadataRegistry.invalidate("youtube123")
        assertFalse(SpotifyMetadataRegistry.contains("youtube123"))
        assertNull(SpotifyMetadataRegistry.get("youtube123"))
    }

    @Test
    fun `clearAll removes all entries`() {
        SpotifyMetadataRegistry.clearAll()
        val track = SongItem(
            id = "youtube123",
            title = "Test Song",
            thumbnail = "https://example.com/thumb.jpg",
            artists = listOf(dev.musicx.shared.model.ArtistTiny("artist1", "Test Artist")),
            duration = 200000L,
        )

        SpotifyMetadataRegistry.register("youtube123", track)
        SpotifyMetadataRegistry.register("youtube456", track)

        assertEquals(2, SpotifyMetadataRegistry.size())
        SpotifyMetadataRegistry.clearAll()
        assertEquals(0, SpotifyMetadataRegistry.size())
    }
}