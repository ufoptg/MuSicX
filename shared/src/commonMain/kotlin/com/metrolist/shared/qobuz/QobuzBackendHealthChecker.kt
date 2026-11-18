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
        val tokenCountry: String? = null,
    )

    data class Result(
        val target: Target,
        val status: Status,
        val latencyMs: Long?,
        val message: String,
    )

    val targets: List<Target> =
        listOf(
            Target(
                backend = QobuzAudioProvider.ResolverBackend.TRYPT,
                name = "TrypT HiFi",
                endpoint = "${QobuzAudioProvider.TRYPT_BASE_URL}/api/get-music?q=test&offset=0",
                tokenCountry = "US",
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
                tokenCountry = "US",
            ),
        )

    private const val CHECK_TIMEOUT_MS = 10_000L

    private val httpClient: HttpClient by lazy { createHttpClient() }

    suspend fun checkAll(): List<Result> = coroutineScope {
        targets.map { target ->
            async { check(target) }
        }.awaitAll()
    }

    suspend fun check(target: Target): Result {
        val startedMs = Clock.System.now().toEpochMilliseconds()
        val response =
            withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                try {
                    httpClient.get(target.endpoint) {
                        header("Accept", "application/json,text/html,*/*")
                        header("User-Agent", QobuzAudioProvider.BROWSER_USER_AGENT)
                        target.tokenCountry?.let { header("Token-Country", it) }
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
                message = "Backend did not answer within ${CHECK_TIMEOUT_MS / 1000}s",
            )
        }
        return Result(
            target = target,
            status = response.status.value.toHealthStatus(),
            latencyMs = elapsedMs,
            message = response.status.value.toHealthMessage(),
        )
    }

    private fun Int.toHealthStatus(): Status =
        when (this) {
            in 200..299 -> Status.ONLINE
            in 300..499 -> Status.REACHABLE
            else -> Status.OFFLINE
        }

    private fun Int.toHealthMessage(): String =
        when (this) {
            in 200..299 -> "HTTP $this"
            401, 403 -> "HTTP $this, auth required"
            404, 405 -> "HTTP $this, endpoint answered"
            429 -> "HTTP 429, rate limited"
            in 300..499 -> "HTTP $this, reachable"
            in 500..599 -> "HTTP $this, server error"
            else -> "HTTP $this"
        }
}
