package com.metrolist.music.discord

import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException

data class AuthCodeResult(val code: String, val state: String)

class LoopbackAuthServer(private val expectedState: String) {

    class PortInUseException(port: Int) : IOException("Port $port is already in use")

    private companion object {
        const val TAG = "DiscordSvc"
    }

    private val deferred = CompletableDeferred<AuthCodeResult>()

    private var server: EmbeddedServer<*, *>? = null

    suspend fun start() {
        Timber.tag(TAG).i("loopback: starting on 127.0.0.1:9384")
        server = embeddedServer(CIO, port = 9384, host = "127.0.0.1") {
            routing {
                get("/callback") {
                    val code = call.request.queryParameters["code"]
                    val state = call.request.queryParameters["state"]

                    if (code == null) {
                        Timber.tag(TAG).w("loopback: callback received with missing code")
                        call.respondText(
                            "<html><body><h1>Authorization Failed</h1><p>Missing authorization code.</p></body></html>",
                            ContentType.Text.Html,
                        )
                        return@get
                    }

                    if (state != expectedState) {
                        Timber.tag(TAG).w(
                            "loopback: state mismatch (expected=%s, got=%s)",
                            expectedState.take(8),
                            state?.take(8),
                        )
                        call.respondText(
                            "<html><body><h1>Authorization Failed</h1><p>State validation failed.</p></body></html>",
                            ContentType.Text.Html,
                        )
                        return@get
                    }

                    Timber.tag(TAG).i("loopback: callback received with valid code (length=%d)", code.length)
                    deferred.complete(AuthCodeResult(code = code, state = state))
                    call.respondText(
                        "<html><body><h1>Authorization Complete</h1><p>You can close this tab and return to Metrolist.</p></body></html>",
                        ContentType.Text.Html,
                    )
                }
            }
        }.start(wait = false)
        Timber.tag(TAG).i("loopback: started")
    }

    suspend fun awaitCode(timeoutMs: Long = 120_000L): AuthCodeResult {
        return withTimeout(timeoutMs) { deferred.await() }
    }

    fun stop() {
        Timber.tag(TAG).i("loopback: stopping")
        server?.stop(1000L, 2000L)
        server = null
    }
}
