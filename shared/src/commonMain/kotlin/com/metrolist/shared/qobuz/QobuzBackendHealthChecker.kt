/**
 * Pings the Qobuz resolver backends and reports live reachability so users
 * can see which proxy is up before playback fails on them. Designed to be
 * triggered on-demand from the settings UI — no internal polling.
 */
package com.metrolist.shared.qobuz

import com.metrolist.shared.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

object QobuzBackendHealthChecker {
    enum class Status { ONLINE, REACHABLE, OFFLINE }

    data class Target(
        val backend: QobuzAudioProvider.ResolverBackend,
        val name: String,
        val endpoint: String,
        val usesTokenCountry: Boolean = false,
    )

    data class Result(
        val target: Target,
        val status: Status,
        val latencyMs: Long?,
        val httpStatusCode: Int? = null,
        val timedOut: Boolean = false,
    )

    val targets: List<Target> =
        listOf(
            Target(
                backend = QobuzAudioProvider.ResolverBackend.TRYPT,
                name = "TrypT HiFi",
                endpoint = "${QobuzAudioProvider.TRYPT_BASE_URL}/api/get-music?q=test&offset=0",
                usesTokenCountry = true,
            ),
            Target(
                backend = QobuzAudioProvider.ResolverBackend.JUMO,
                name = "Jumo",
                endpoint = "${QobuzAudioProvider.JUMO_BASE_URL}/",
            ),
            Target(
                backend = QobuzAudioProvider.ResolverBackend.MONOKENNY,
                name = "Monokenny",
                endpoint = "${QobuzAudioProvider.KENNY_BASE_URL}/api/get-music?q=test&offset=0",
            ),
            Target(
                backend = QobuzAudioProvider.ResolverBackend.SQUID,
                name = "Squid",
                endpoint = "${QobuzAudioProvider.SQUID_BASE_URL}/api/get-music?q=test&offset=0",
                usesTokenCountry = true,
            ),
        )

    private const val CHECK_TIMEOUT_MS = 10_000L

    val timeoutSeconds: Int get() = (CHECK_TIMEOUT_MS / 1000L).toInt()

    private val httpClient: HttpClient by lazy { createHttpClient() }

    suspend fun checkAll(countryCode: String): List<Result> =
        coroutineScope {
            targets
                .map { target ->
                    async { check(target, countryCode) }
                }.awaitAll()
        }

    suspend fun check(
        target: Target,
        countryCode: String,
    ): Result {
        val startedMs = Clock.System.now().toEpochMilliseconds()
        val response =
            withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                try {
                    httpClient.get(target.endpoint) {
                        header("Accept", "application/json,text/html,*/*")
                        header("User-Agent", QobuzAudioProvider.BROWSER_USER_AGENT)
                        if (target.usesTokenCountry && countryCode.isNotBlank()) {
                            header("Token-Country", countryCode)
                        }
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    null
                }
            }
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedMs
        if (response == null) {
            return Result(
                target = target,
                status = Status.OFFLINE,
                latencyMs = null,
                timedOut = true,
            )
        }
        return Result(
            target = target,
            status = response.status.value.toHealthStatus(),
            latencyMs = elapsedMs,
            httpStatusCode = response.status.value,
        )
    }

    private fun Int.toHealthStatus(): Status =
        when (this) {
            in 200..299 -> Status.ONLINE
            in 300..499 -> Status.REACHABLE
            else -> Status.OFFLINE
        }
}
