/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.innertubex.extraction.ExtractedStream
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Downloads InnerTubeX progressive audio (AAC/MP4) with required headers, then plays via OpenJFX.
 * ponytail: full/chunked download before play — OpenJFX on Windows cannot play WebM/Opus.
 */
class DesktopAudioPlayer : AutoCloseable {
    private val downloadClient =
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
    private val fxStarted = AtomicBoolean(false)

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var tempFile: File? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    suspend fun play(stream: ExtractedStream) {
        val mime = stream.mimeType.orEmpty()
        if (mime.contains("webm", ignoreCase = true) ||
            stream.codecs.orEmpty().contains("opus", ignoreCase = true)
        ) {
            error(
                "Stream is ${mime.ifBlank { "webm/opus" }} (itag ${stream.itag}); " +
                    "OpenJFX on Windows needs AAC/MP4. Try another track or update the resolver.",
            )
        }

        ensureFx()
        awaitFx { stopInternal() }

        val file =
            withContext(Dispatchers.IO) {
                val out = File.createTempFile("musicx-", ".m4a")
                out.deleteOnExit()
                downloadToFile(stream, out)
                if (out.length() < 1024) {
                    error("Downloaded audio too small (${out.length()} bytes) — URL likely expired or blocked")
                }
                out
            }

        awaitMediaReady(file)
    }

    fun togglePause() {
        val player = mediaPlayer ?: return
        runFx {
            if (player.status == MediaPlayer.Status.PLAYING) {
                player.pause()
                isPlaying = false
            } else {
                player.play()
                isPlaying = true
            }
        }
    }

    fun stop() {
        runFx { stopInternal() }
    }

    override fun close() {
        runFx { stopInternal() }
        downloadClient.close()
    }

    private suspend fun downloadToFile(
        stream: ExtractedStream,
        out: File,
    ) {
        if (stream.requireBoundedRange || stream.useRangeChunks) {
            val length =
                stream.contentLengthBytes
                    ?: error("Bounded-range stream missing content length")
            val chunkSize = stream.rangeChunkSizeBytes.coerceAtLeast(256 * 1024L)
            out.outputStream().use { os ->
                var start = 0L
                while (start < length) {
                    val end = min(start + chunkSize - 1, length - 1)
                    val response =
                        downloadClient.get(stream.audioUrl) {
                            stream.headers.forEach { (name, value) -> header(name, value) }
                            header(io.ktor.http.HttpHeaders.Range, "bytes=$start-$end")
                        }
                    ensureOk(response, "range $start-$end")
                    os.write(response.body<ByteArray>())
                    start = end + 1
                }
            }
        } else {
            val response =
                downloadClient.get(stream.audioUrl) {
                    stream.headers.forEach { (name, value) -> header(name, value) }
                }
            ensureOk(response, "full body")
            out.writeBytes(response.body<ByteArray>())
        }
    }

    private suspend fun ensureOk(
        response: HttpResponse,
        label: String,
    ) {
        if (!response.status.isSuccess() && response.status.value != 206) {
            error("Audio download failed ($label): HTTP ${response.status.value}")
        }
    }

    private suspend fun awaitMediaReady(file: File) {
        suspendCancellableCoroutine { cont ->
            ensureFx()
            Platform.runLater {
                try {
                    tempFile = file
                    val player =
                        MediaPlayer(Media(file.toURI().toString())).also { mediaPlayer = it }
                    player.setOnReady {
                        player.play()
                        isPlaying = true
                        if (cont.isActive) cont.resume(Unit)
                    }
                    player.setOnEndOfMedia {
                        isPlaying = false
                    }
                    player.setOnError {
                        isPlaying = false
                        val detail =
                            player.error?.message
                                ?: player.error?.toString()
                                ?: "unknown MediaPlayer error"
                        if (cont.isActive) {
                            cont.resumeWithException(IllegalStateException("Playback error: $detail"))
                        }
                    }
                    cont.invokeOnCancellation {
                        runFx { stopInternal() }
                    }
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }
        }
    }

    private fun stopInternal() {
        mediaPlayer?.stop()
        mediaPlayer?.dispose()
        mediaPlayer = null
        isPlaying = false
        tempFile?.delete()
        tempFile = null
    }

    private fun ensureFx() {
        if (fxStarted.compareAndSet(false, true)) {
            try {
                Platform.startup {}
            } catch (_: IllegalStateException) {
                // Toolkit already running
            }
        }
    }

    private fun runFx(block: () -> Unit) {
        ensureFx()
        if (Platform.isFxApplicationThread()) {
            block()
        } else {
            Platform.runLater(block)
        }
    }

    private suspend fun awaitFx(block: () -> Unit) {
        ensureFx()
        if (Platform.isFxApplicationThread()) {
            block()
            return
        }
        suspendCancellableCoroutine { cont ->
            Platform.runLater {
                try {
                    block()
                    cont.resume(Unit)
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }
        }
    }
}
