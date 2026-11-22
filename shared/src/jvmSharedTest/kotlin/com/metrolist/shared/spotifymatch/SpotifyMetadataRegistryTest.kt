package com.metrolist.shared.spotifymatch

import meow.nyx.spotlib.TrackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SpotifyMetadataRegistryTest {
    @Test
    fun registerAndGet() {
        val track =
            TrackItem(
                uri = "spotify-uri-123",
                name = "Track Name",
                artists = listOf("Artist A", "Artist B"),
                album = "album-uri",
                durationMs = 12345L,
                trackNumber = 1,
                discNumber = 1,
                explicit = false,
                isLocal = false,
                addedAt = "now",
                addedBy = "user",
            )
        SpotifyMetadataRegistry.register("youtube-id-123", track)

        val result = SpotifyMetadataRegistry.get("youtube-id-123")
        assertNotNull(result)
        assertEquals(track.uri, result.uri)
        assertEquals(track.name, result.name)
        assertEquals(track.artists, result.artists)
    }

    @Test
    fun getNotFoundReturnsNull() {
        assertNull(SpotifyMetadataRegistry.get("non-existent-id"))
    }

    @Test
    fun invalidateRemovesEntry() {
        val track = TrackItem(uri = "spotify-uri-123", name = "Track", artists = listOf("Artist"), durationMs = 1000L)
        SpotifyMetadataRegistry.register("youtube-id-123", track)

        SpotifyMetadataRegistry.invalidate("youtube-id-123")
        assertNull(SpotifyMetadataRegistry.get("youtube-id-123"))
    }

    @Test
    fun clearAllRemovesAllEntries() {
        val track1 = TrackItem(uri = "uri-1", name = "t", artists = listOf("a"), durationMs = 1000L)
        val track2 = TrackItem(uri = "uri-2", name = "t", artists = listOf("b"), durationMs = 2000L)
        SpotifyMetadataRegistry.register("youtube-id-1", track1)
        SpotifyMetadataRegistry.register("youtube-id-2", track2)

        SpotifyMetadataRegistry.clearAll()
        assertNull(SpotifyMetadataRegistry.get("youtube-id-1"))
        assertNull(SpotifyMetadataRegistry.get("youtube-id-2"))
    }
}
