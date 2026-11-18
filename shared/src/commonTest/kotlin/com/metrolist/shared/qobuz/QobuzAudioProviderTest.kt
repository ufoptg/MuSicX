package com.metrolist.shared.qobuz

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QobuzAudioProviderTest {
    private val directQuery =
        QobuzAudioProvider.Query(
            mediaId = "12345678",
            title = "Song",
            artists = emptyList(),
            album = null,
            isrc = null,
            durationMs = null,
            countryCode = "US",
            backend = QobuzAudioProvider.ResolverBackend.SQUID,
        )

    private val fuzzyQuery =
        directQuery.copy(
            mediaId = "yt:fuzzy-id",
            title = "Alpha",
            artists = listOf("Artist"),
        )

    @AfterTest
    fun tearDown() = QobuzAudioProvider.resetForTests()

    @Test
    fun resolveReturnsNullInsteadOfThrowingWhenEveryBackendFails() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond("<html>boom</html>", HttpStatusCode.InternalServerError)
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)

            // Direct track id path: 4 backends x 4 ladder rungs.
            assertNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(16, calls)

            // Fuzzy search path: search itself fails, so no stream requests.
            assertNull(QobuzAudioProvider.resolve(fuzzyQuery))

            // refresh follows the same never-throw contract.
            assertNull(QobuzAudioProvider.refresh(directQuery))

            client.close()
        }

    @Test
    fun resolveWalksTheQualityLadderDownAndCachesTheStreamForFiveMinutes() =
        runTest {
            val requestedQualities = mutableListOf<Int>()
            var calls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        calls++
                        val quality = request.url.parameters["quality"]?.toInt()
                        requestedQualities += quality ?: -1
                        if (quality == 5) {
                            respond("""{"success":true,"data":{"url":"https://stream.example/file.flac","bitrate":320}}""")
                        } else {
                            respond("""{"success":false,"error":"rejected"}""")
                        }
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)

            val resolved = QobuzAudioProvider.resolve(directQuery)
            assertNotNull(resolved)
            // Ladder is exactly 27 -> 7 -> 6 -> 5 on the preferred backend.
            assertEquals(listOf(27, 7, 6, 5), requestedQualities)
            assertEquals("https://stream.example/file.flac", resolved.mediaUri)
            // Quality 5 maps to the MP3 branch of the format resolver.
            assertEquals("audio/mpeg", resolved.mimeType)
            assertEquals(320_000, resolved.bitrate)
            assertEquals("12345678", resolved.trackId)

            // Second resolve hits the 5-minute stream cache: no new requests.
            val cached = QobuzAudioProvider.resolve(directQuery)
            assertNotNull(cached)
            assertEquals(resolved.mediaUri, cached.mediaUri)
            assertEquals(4, calls)

            client.close()
        }

    @Test
    fun captchaResponseCoolsTheBackendDownUntilExpiry() =
        runTest {
            var fakeNow = 0L
            var captcha = true
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        if (captcha) {
                            respond("""{"success":false,"error":"Captcha required."}""")
                        } else {
                            respond("""{"success":true,"data":{"url":"https://stream.example/file.flac"}}""")
                        }
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)
            QobuzAudioProvider.nowMs = { fakeNow }

            assertNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(4, calls)

            // Backends recover, but the captcha cooldown still skips them all.
            captcha = false
            fakeNow += 60_000L
            assertNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(4, calls)

            // After the 5-minute cooldown expires the preferred backend resolves again.
            fakeNow += 5 * 60 * 1000L + 1_000L
            val resolved = QobuzAudioProvider.resolve(directQuery)
            assertNotNull(resolved)
            assertEquals("https://stream.example/file.flac", resolved.mediaUri)
            assertEquals(5, calls)

            client.close()
        }

    @Test
    fun refreshBypassesTheStreamCache() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond("""{"success":true,"data":{"url":"https://stream.example/file.flac"}}""")
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)

            assertNotNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(1, calls)

            // Plain resolve is served from cache.
            assertNotNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(1, calls)

            // refresh drops the cache entry and re-resolves.
            assertNotNull(QobuzAudioProvider.refresh(directQuery))
            assertEquals(2, calls)

            client.close()
        }

    @Test
    fun searchCandidatesReturnsDeduplicatedMetadata() =
        runTest {
            val body =
                """
                {"success":true,"data":{"tracks":{"items":[
                    {"id":"111","title":"Alpha","performer":{"name":"Artist"},"album":{"title":"LP"},"duration":200,"hires":true,"maximum_bit_depth":24,"maximum_sampling_rate":96.0},
                    {"id":"111","title":"Alpha duplicate"},
                    {"id":"222","title":"Beta"}
                ]}}}
                """.trimIndent()
            val client = HttpClient(MockEngine { respond(body) })
            QobuzAudioProvider.resetForTests(client = client)

            val candidates = QobuzAudioProvider.searchCandidates(fuzzyQuery, limit = 12)
            assertEquals(listOf("111", "222"), candidates.map { it.trackId })
            assertEquals("Alpha", candidates[0].title)
            assertEquals("Artist", candidates[0].artist)
            assertEquals("LP", candidates[0].album)
            assertEquals(200_000L, candidates[0].durationMs)
            assertEquals(true, candidates[0].hires)
            assertEquals(24, candidates[0].bitDepth)
            assertEquals(96.0, candidates[0].samplingRateKhz)

            client.close()
        }
}
