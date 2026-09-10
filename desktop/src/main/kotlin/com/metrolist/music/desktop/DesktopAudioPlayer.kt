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
import io.ktor.client.request.get
import io.ktor.client.request.header
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

/**
 * Downloads an InnerTubeX progressive audio URL (with required headers), then plays via OpenJFX.
 * ponytail: full download before play; swap to streaming engine when we need seek/queue parity.
 */
class DesktopAudioPlayer : AutoCloseable {
    private val downloadClient = HttpClient(OkHttp)
    private val fxStarted = AtomicBoolean(false)

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var tempFile: File? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    suspend fun play(stream: ExtractedStream) {
        ensureFx()
        awaitFx { stopInternal() }

        val file =
            withContext(Dispatchers.IO) {
                val ext =
                    when {
                        stream.mimeType?.contains("webm", ignoreCase = true) == true -> ".webm"
                        else -> ".m4a"
                    }
                val out = File.createTempFile("musicx-", ext)
                out.deleteOnExit()
                val bytes =
                    downloadClient.get(stream.audioUrl) {
                        stream.headers.forEach { (name, value) -> header(name, value) }
                    }.body<ByteArray>()
                out.writeBytes(bytes)
                out
            }

        awaitFx {
            tempFile = file
            val player =
                MediaPlayer(Media(file.toURI().toString())).also { mediaPlayer = it }
            player.setOnEndOfMedia {
                isPlaying = false
            }
            player.setOnError {
                isPlaying = false
            }
            player.play()
            isPlaying = true
        }
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
