package com.metrolist.shared.spotifyimport

import com.metrolist.shared.playlistimport.PlaylistSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import meow.nyx.spotlib.LogLevel
import meow.nyx.spotlib.PlaylistMeta
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.SpotifyConfig
import meow.nyx.spotlib.SpotifyHomeFeed
import meow.nyx.spotlib.SpotifyOfflineCache
import meow.nyx.spotlib.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class SpotifyImportOfflineCacheTest {

    private fun newManager(): SpotifyImportManager =
        SpotifyImportManager(
            clientFactory = { spotifyClient() },
            loadStoredSession = { "" },
            saveStoredSession = { },
        )

    private fun spotifyClient(): SpotifyClient =
        SpotifyClient.create(
            SpotifyConfig(
                engine = MockEngine { request ->
                    when (request.url.host) {
                        "open.spotify.com" -> respond(
                            """{"accessToken":"test-token","accessTokenExpirationTimestampMs":9223372036854775807,"isAnonymous":false}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        "clienttoken.spotify.com" -> respond(
                            """{"granted_token":{"token":"test-client-token"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        "api-partner.spotify.com" -> respond("{}", HttpStatusCode.InternalServerError)
                        else -> error("Unexpected host ${request.url.host}")
                    }
                },
                logLevel = LogLevel.NONE,
            ),
        )

    @Before
    fun clearCache() {
        SpotifyOfflineCache.clearAll()
    }

    @Test
    fun `loadHome returns cached feed when api fails`() = runBlocking {
        val cachedHomeFeed = SpotifyHomeFeed(greeting = "Hello", sections = emptyList())
        SpotifyOfflineCache.cacheHomeFeed(cachedHomeFeed)

        val manager = newManager()
        manager.authenticate("cookie")

        val result = manager.loadHome()
        assertEquals(cachedHomeFeed.greeting, result.greeting)
        assertEquals(0, result.sections.size)
    }

    @Test
    fun `loadHome fails when no cache exists`() = runBlocking {
        val manager = newManager()
        manager.authenticate("cookie")

        assertFailsWith<Exception> { manager.loadHome() }
    }

    @Test
    fun `loadPlaylists returns cached playlists when api fails`() = runBlocking {
        val cachedPlaylists =
            listOf(
                PlaylistMeta(uri = "uri-1", name = "Playlist 1", source = PlaylistSource.PLAYLIST, trackCount = 10),
                PlaylistMeta(uri = "uri-2", name = "Playlist 2", source = PlaylistSource.PLAYLIST, trackCount = 5),
            )
        SpotifyOfflineCache.cachePlaylists(cachedPlaylists)

        val manager = newManager()
        manager.authenticate("cookie")

        val result = manager.loadPlaylists()
        assertEquals(cachedPlaylists.size, result.size)
        assertEquals(cachedPlaylists.map { it.uri }, result.map { it.uri })
    }

    @Test
    fun `loadPlaylist returns cached tracks when api fails`() = runBlocking {
        val cachedTracks =
            listOf(
                TrackItem(uri = "spotify:track:one", name = "Track One", artists = listOf("Artist One")),
            )
        SpotifyOfflineCache.cachePlaylistTracks("spotify:playlist:cache-test", cachedTracks)

        val manager = newManager()
        manager.authenticate("cookie")

        val result =
            manager.loadPlaylist(
                PlaylistMeta(uri = "spotify:playlist:cache-test", name = "Cached", source = PlaylistSource.PLAYLIST),
            )
        assertEquals(1, result.tracks.size)
        assertEquals("Track One", result.tracks.first().title)
    }

    @Test
    fun `offline cache stores and retrieves home feed`() = runBlocking {
        val feed = SpotifyHomeFeed(greeting = "Hi", sections = emptyList())
        SpotifyOfflineCache.cacheHomeFeed(feed)

        val got = SpotifyOfflineCache.getCachedHomeFeed()
        assertEquals(feed.greeting, got?.greeting)
    }

    @Test
    fun `offline cache stores and retrieves playlists`() = runBlocking {
        val playlists =
            listOf(
                PlaylistMeta(uri = "uri-1", name = "Playlist 1", source = PlaylistSource.PLAYLIST),
            )
        SpotifyOfflineCache.cachePlaylists(playlists)

        val got = SpotifyOfflineCache.getCachedPlaylists()
        assertEquals(listOf("uri-1"), got?.map { it.uri })
    }

    @Test
    fun `offline cache returns null when nothing cached`() {
        assertNull(SpotifyOfflineCache.getCachedHomeFeed())
        assertNull(SpotifyOfflineCache.getCachedPlaylists())
    }

    @Test
    fun `offline cache stores and retrieves playlist tracks`() = runBlocking {
        val uri = "spotify:playlist:cache-test"
        val tracks =
            listOf(
                TrackItem(uri = "spotify:track:one", name = "Track One", artists = listOf("Artist One")),
            )
        SpotifyOfflineCache.cachePlaylistTracks(uri, tracks)

        val cached = SpotifyOfflineCache.getCachedPlaylistTracks(uri)
        assertEquals(1, cached?.size)
    }

    @Test
    fun `offline cache clears all entries`() = runBlocking {
        SpotifyOfflineCache.cacheHomeFeed(SpotifyHomeFeed(greeting = "Hello", sections = emptyList()))
        SpotifyOfflineCache.cachePlaylists(
            listOf(
                PlaylistMeta(uri = "uri-1", name = "Playlist 1", source = PlaylistSource.PLAYLIST),
            ),
        )

        SpotifyOfflineCache.clearAll()

        assertNull(SpotifyOfflineCache.getCachedHomeFeed())
        assertNull(SpotifyOfflineCache.getCachedPlaylists())
    }

    @Test
    fun `offline cache persists home feed between instances`() = runBlocking {
        SpotifyOfflineCache.cacheHomeFeed(SpotifyHomeFeed(greeting = "Hello", sections = emptyList()))

        val manager = newManager()
        manager.authenticate("cookie")

        val result = manager.loadHome()
        assertEquals("Hello", result.greeting)
    }
}
