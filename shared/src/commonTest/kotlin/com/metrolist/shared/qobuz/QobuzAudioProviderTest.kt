package com.metrolist.shared.qobuz

import com.metrolist.shared.qobuz.QobuzAudioProvider.QobuzAudioQuality
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
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

    private val squidHost = Url(QobuzAudioProvider.SQUID_BASE_URL).host
    private val jumoHost = Url(QobuzAudioProvider.JUMO_BASE_URL).host
    private val kennyHost = Url(QobuzAudioProvider.KENNY_BASE_URL).host

    @AfterTest
    fun tearDown() = QobuzAudioProvider.resetForTests()

    @Test
    fun resolveReturnsNullInsteadOfThrowingWhenEveryBackendFails() =
        runBlocking {
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
        runBlocking {
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
        runBlocking {
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
        runBlocking {
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
        runBlocking {
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

    @Test
    fun qualityCodeForMapsEachSettingToItsLadderRung() {
        assertEquals(5, QobuzAudioProvider.qualityCodeFor(QobuzAudioQuality.AAC_320))
        assertEquals(6, QobuzAudioProvider.qualityCodeFor(QobuzAudioQuality.CD_QUALITY))
        assertEquals(27, QobuzAudioProvider.qualityCodeFor(QobuzAudioQuality.HI_RES_LOSSLESS))
    }

    @Test
    fun configuredCdQualityStartsAtRungSix() =
        runBlocking {
            val requestedQualities = mutableListOf<Int>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        requestedQualities += request.url.parameters["quality"]?.toInt() ?: -1
                        respond("""{"success":true,"data":{"url":"https://stream.example/cd.flac","bit_depth":16,"sampling_rate":44.1}}""")
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)

            val resolved =
                QobuzAudioProvider.resolve(
                    directQuery.copy(
                        qualityCode = QobuzAudioProvider.qualityCodeFor(QobuzAudioQuality.CD_QUALITY),
                    ),
                )
            assertNotNull(resolved)
            assertEquals(listOf(6), requestedQualities)
            assertEquals("audio/flac", resolved.mimeType)

            client.close()
        }

    @Test
    fun unknownQualityCodeTriesOnlyThatRungPerBackend() =
        runBlocking {
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond("""{"success":false,"error":"unsupported quality"}""")
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)

            assertNull(QobuzAudioProvider.resolve(directQuery.copy(qualityCode = 99)))
            // One request per backend, no ladder walk from an unknown rung.
            assertEquals(4, calls)

            client.close()
        }

    @Test
    fun preferredBackendFailureFallsThroughToTheNextBackend() =
        runBlocking {
            val hosts = mutableListOf<String>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        hosts += request.url.host
                        if (request.url.host == squidHost) {
                            respond("""{"success":false,"error":"upstream unavailable"}""")
                        } else {
                            respond("""{"success":true,"data":{"url":"https://stream.example/file.flac"}}""")
                        }
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)

            val resolved = QobuzAudioProvider.resolve(directQuery)
            assertNotNull(resolved)
            assertEquals("12345678", resolved.trackId)
            // Squid walks all four rungs before Monokenny resolves at the top rung.
            assertEquals(listOf(squidHost, squidHost, squidHost, squidHost, kennyHost), hosts)

            client.close()
        }

    @Test
    fun captchaCooldownSkipsOnlyTheFailingBackend() =
        runBlocking {
            var fakeNow = 0L
            var squidCalls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.url.host == squidHost) {
                            squidCalls++
                            respond("""{"success":false,"error":"Captcha required."}""")
                        } else {
                            respond("""{"success":true,"data":{"url":"https://stream.example/file.flac"}}""")
                        }
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)
            QobuzAudioProvider.nowMs = { fakeNow }

            // Squid is captcha-blocked mid-ladder; the next backend still resolves.
            assertNotNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(1, squidCalls)

            // A minute later the cooldown still skips Squid for a new track.
            fakeNow += 60_000L
            assertNotNull(QobuzAudioProvider.resolve(directQuery.copy(mediaId = "87654321")))
            assertEquals(1, squidCalls)

            client.close()
        }

    @Test
    fun previewAbortsTheLadderForThatBackendOnly() =
        runBlocking {
            var jumoCalls = 0
            var calls = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        calls++
                        if (request.url.host == jumoHost) {
                            jumoCalls++
                            respond("""{"previewDetected":true}""")
                        } else {
                            respond("""{"success":true,"data":{"url":"https://stream.example/file.flac"}}""")
                        }
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)

            val resolved =
                QobuzAudioProvider.resolve(
                    directQuery.copy(backend = QobuzAudioProvider.ResolverBackend.JUMO),
                )
            assertNotNull(resolved)
            // Preview stops the ladder after one rung, then Monokenny resolves.
            assertEquals(1, jumoCalls)
            assertEquals(2, calls)

            client.close()
        }

    @Test
    fun streamCacheExpiresAfterFiveMinutes() =
        runBlocking {
            var fakeNow = 0L
            var calls = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond("""{"success":true,"data":{"url":"https://stream.example/file.flac"}}""")
                    },
                )
            QobuzAudioProvider.resetForTests(client = client)
            QobuzAudioProvider.nowMs = { fakeNow }

            assertNotNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(1, calls)

            fakeNow += 4 * 60 * 1000L
            assertNotNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(1, calls)

            fakeNow += 60 * 1000L + 1_000L
            assertNotNull(QobuzAudioProvider.resolve(directQuery))
            assertEquals(2, calls)

            client.close()
        }
}
