package meow.nyx.spotlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyHashProviderTest {
    @Test
    fun `updateHashes replaces current hash when remote differs`() {
        val remote = mapOf("home" to RemoteHashEntry("rotate-home-hash"))

        val result = SpotifyHashProvider.updateHashes(remote, HashSource.REMOTE)

        assertEquals(1, result.updated)
        assertEquals(0, result.unchanged)
        assertEquals("rotate-home-hash", SpotifyHashProvider.getHash("home"))
        assertNull(SpotifyHashProvider.getPreviousHash("home"))
    }

    @Test
    fun `updateHashes leaves identical hardcoded entries untouched`() {
        val hardcodedLibraryV3 = "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455"
        val remote = mapOf("libraryV3" to RemoteHashEntry(hardcodedLibraryV3))

        val result = SpotifyHashProvider.updateHashes(remote, HashSource.REMOTE)

        assertEquals(0, result.updated)
        assertEquals(1, result.unchanged)
        assertEquals(hardcodedLibraryV3, SpotifyHashProvider.getHash("libraryV3"))
    }

    @Test
    fun `getPreviousHash returns recorded fallback after rotation`() {
        val remote = mapOf("getAlbum" to RemoteHashEntry("rotated-album-hash", "previous-album-hash"))

        SpotifyHashProvider.updateHashes(remote, HashSource.REMOTE)

        assertEquals("previous-album-hash", SpotifyHashProvider.getPreviousHash("getAlbum"))
    }

    @Test
    fun `updateHashes records previous hash when remote supplies one`() {
        val remote = mapOf("queryArtistOverview" to RemoteHashEntry("new-overview-hash", "old-overview-hash"))

        SpotifyHashProvider.updateHashes(remote, HashSource.REMOTE)

        assertEquals("new-overview-hash", SpotifyHashProvider.getHash("queryArtistOverview"))
        assertEquals("old-overview-hash", SpotifyHashProvider.getPreviousHash("queryArtistOverview"))
    }
}