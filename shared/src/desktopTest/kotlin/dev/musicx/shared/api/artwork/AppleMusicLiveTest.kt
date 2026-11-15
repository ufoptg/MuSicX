package dev.musicx.shared.api.artwork

import com.metrolist.innertubex.models.YouTubeLocale
import dev.musicx.shared.api.lyrics.LyricsUtils
import dev.musicx.shared.api.lyrics.PaxsenixLyricsProvider
import dev.musicx.shared.network.createHttpClient
import dev.musicx.shared.network.readTextLimited
import dev.musicx.shared.playlistimport.AppleMusicPlaylistImporter
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.GlobalYouTube
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.prepareGet
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppleMusicLiveTest {
    @Test
    fun resolvesSquareAndTallCoversThroughTheSharedService() =
        runBlocking {
            withLiveApple { client, _ ->
                val service = AnimatedArtworkService(client)
                try {
                    val cover = assertNotNull(service.fetchAnimatedCover("Espresso", "Sabrina Carpenter", "Short n' Sweet"))
                    for (url in listOf(cover.url, assertNotNull(cover.tallUrl))) {
                        val playlist = client.prepareGet(url).readTextLimited(1024 * 1024)
                        assertTrue(playlist.status.isSuccess())
                        assertTrue(assertNotNull(playlist.body).startsWith("#EXTM3U"))
                    }
                    assertEquals(cover, service.fetchAnimatedCover("Espresso", "Sabrina Carpenter", "Short n' Sweet"))
                } finally {
                    service.dispose()
                }
            }
        }

    @Test
    fun resolvesArtistImageAndVideoThroughTheSharedService() =
        runBlocking {
            withLiveApple { client, _ ->
                val service = AnimatedArtworkService(client)
                try {
                    val result = service.fetchArtistArtwork("Billie Eilish") as? ArtistArtworkFetchResult.Found
                    val artwork = assertNotNull(result).artwork
                    assertNotNull(artwork.imageUrl)
                    assertNotNull(artwork.videoUrl)
                } finally {
                    service.dispose()
                }
            }
        }

    @Test
    fun importsPublicAppleAlbumThroughTheSharedImporter() =
        runBlocking {
            withLiveApple { client, country ->
                AppleMusicPlaylistImporter.resetClientForTests(client)
                try {
                    val album =
                        AppleMusicPlaylistImporter
                            .loadPlaylist("https://music.apple.com/$country/album/1750307020")
                            .getOrThrow()
                    assertEquals("Short n' Sweet", album.title)
                    assertTrue(album.tracks.any { it.title == "Espresso" && it.artist == "Sabrina Carpenter" })
                } finally {
                    AppleMusicPlaylistImporter.resetClientForTests()
                }
            }
        }

    @Test
    fun searchesAppleCatalogAndFetchesPaxsenixLyrics() =
        runBlocking {
            withLiveApple { client, _ ->
                val lyrics =
                    assertNotNull(PaxsenixLyricsProvider(client).getLyrics("Espresso", "Sabrina Carpenter", 175_000, "Short n' Sweet"))
                assertTrue(assertNotNull(LyricsUtils.parseMlxDocument(lyrics)).lines.any { it.text.isNotBlank() })
            }
        }

    private suspend fun withLiveApple(block: suspend (HttpClient, String) -> Unit) {
        assumeTrue(
            "Set RUN_LIVE_APPLE_MUSIC_TESTS=true to contact Apple and Paxsenix",
            System.getenv("RUN_LIVE_APPLE_MUSIC_TESTS") == "true",
        )
        val locale = Locale.getDefault()
        val country = (System.getenv("APPLE_MUSIC_TEST_COUNTRY") ?: locale.country).lowercase(Locale.ROOT)
        val language = System.getenv("APPLE_MUSIC_TEST_LANGUAGE") ?: locale.language
        require(country.matches(Regex("[a-z]{2}"))) { "Set APPLE_MUSIC_TEST_COUNTRY to the storefront to probe" }
        require(language.isNotBlank()) { "Set APPLE_MUSIC_TEST_LANGUAGE to the content language to probe" }
        val originalLocale = GlobalYouTube.innerTube.locale
        Settings.replaceSharedForTest(
            Settings(MapSettings()).apply {
                enableAnimatedCovers = true
                enableAppleMusicArtistImages = true
                enableAppleMusicArtistVideos = true
                lastResolvedContentCountry = country.uppercase(Locale.ROOT)
                lastResolvedContentLanguage = language
            },
        )
        GlobalYouTube.innerTube.locale = YouTubeLocale(gl = country.uppercase(Locale.ROOT), hl = language)
        try {
            createHttpClient().use { base ->
                base
                    .config {
                        install(
                            createClientPlugin("AppleProbeStatus") {
                                onResponse { response ->
                                    val url = response.call.request.url
                                    println("Apple probe ${url.host}${url.encodedPath}: HTTP ${response.status.value}")
                                }
                            },
                        )
                    }.use { client -> block(client, country) }
            }
        } finally {
            GlobalYouTube.innerTube.locale = originalLocale
            Settings.replaceSharedForTest(null)
        }
    }
}
