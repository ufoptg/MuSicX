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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Downloads InnerTubeX audio with required headers, then plays via bundled LibVLC.
 * Filtered VLC builds lack HTTP access plugins, so we feed a local file instead of streaming.
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

    private val factory: MediaPlayerFactory by lazy { createFactory() }
    private val mediaPlayer: MediaPlayer by lazy {
        factory.mediaPlayers().newMediaPlayer().also { player ->
            player.events().addMediaPlayerEventListener(
                object : MediaPlayerEventAdapter() {
                    override fun error(mediaPlayer: MediaPlayer) {
                        lastError.set("VLC playback error")
                        isPlaying = false
                    }

                    override fun finished(mediaPlayer: MediaPlayer) {
                        isPlaying = false
                    }
                },
            )
        }
    }

    private val lastError = AtomicReference<String?>(null)
    private var tempFile: File? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    suspend fun play(stream: ExtractedStream) {
        check(stream.sabrBootstrap == null) { "SABR streams are not supported yet" }
        lastError.set(null)

        val file =
            withContext(Dispatchers.IO) {
                val ext =
                    when {
                        stream.mimeType.orEmpty().contains("webm", ignoreCase = true) -> ".webm"
                        else -> ".m4a"
                    }
                val out = File.createTempFile("musicx-", ext)
                out.deleteOnExit()
                downloadToFile(stream, out)
                if (out.length() < 1024L) {
                    error("Downloaded audio too small (${out.length()} bytes)")
                }
                out
            }

        withContext(Dispatchers.IO) {
            stopInternal()
            tempFile = file
            // MRL must be a file URL; Windows paths need proper URI form
            val mrl = file.toURI().toASCIIString()
            val started = mediaPlayer.media().play(mrl, ":no-video")
            if (!started) {
                error("VLC could not open ${file.name} (itag ${stream.itag}, ${stream.mimeType})")
            }
            mediaPlayer.audio().setVolume(100)
            mediaPlayer.audio().setMute(false)

            // Wait briefly for decode/output to start; surface silent failures
            repeat(20) {
                delay(100)
                lastError.get()?.let { error(it) }
                when (mediaPlayer.status().state()) {
                    State.PLAYING, State.BUFFERING -> {
                        isPlaying = true
                        return@withContext
                    }
                    State.ERROR -> error("VLC entered ERROR state")
                    else -> Unit
                }
            }
            if (!mediaPlayer.status().isPlaying) {
                val state = mediaPlayer.status().state()
                error(
                    "No audio after start (state=$state, itag=${stream.itag}, mime=${stream.mimeType}). " +
                        "Bundled codecs may be incomplete — try another track.",
                )
            }
            isPlaying = true
        }
    }

    fun togglePause() {
        when (mediaPlayer.status().state()) {
            State.PLAYING -> {
                mediaPlayer.controls().pause()
                isPlaying = false
            }
            State.PAUSED -> {
                mediaPlayer.controls().play()
                isPlaying = true
            }
            else -> {
                mediaPlayer.controls().play()
                isPlaying = mediaPlayer.status().isPlaying
            }
        }
    }

    fun stop() {
        stopInternal()
    }

    override fun close() {
        stopInternal()
        runCatching { mediaPlayer.release() }
        runCatching { factory.release() }
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
                    val end = minOf(start + chunkSize - 1, length - 1)
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

    private fun ensureOk(
        response: HttpResponse,
        label: String,
    ) {
        if (!response.status.isSuccess() && response.status.value != 206) {
            error("Audio download failed ($label): HTTP ${response.status.value}")
        }
    }

    private fun stopInternal() {
        runCatching { mediaPlayer.controls().stop() }
        isPlaying = false
        tempFile?.delete()
        tempFile = null
    }

    private fun createFactory(): MediaPlayerFactory {
        val vlcDir = resolveBundledVlcDir()
        val pluginsDir = File(vlcDir, "plugins")
        require(File(vlcDir, "libvlc.dll").isFile || File(vlcDir, "libvlc.so").isFile) {
            "Bundled LibVLC not found at ${vlcDir.absolutePath}"
        }
        System.setProperty("jna.library.path", vlcDir.absolutePath)

        val discovery = NativeDiscovery(BundledVlcDiscoveryStrategy(vlcDir))
        if (!discovery.discover()) {
            error("Failed to discover bundled LibVLC at ${vlcDir.absolutePath}")
        }

        val args =
            buildList {
                add("--plugin-path=${pluginsDir.absolutePath}")
                add("--no-plugins-cache")
                add("--aout=directsound")
                add("--no-video")
                add("--intf")
                add("dummy")
                add("--no-video-title-show")
                add("--quiet")
            }

        return MediaPlayerFactory(discovery, *args.toTypedArray())
    }

    companion object {
        private fun resolveBundledVlcDir(): File {
            val resourcesDir = System.getProperty("compose.application.resources.dir")
            if (!resourcesDir.isNullOrBlank()) {
                val bundled = File(resourcesDir, "vlc")
                if (File(bundled, "libvlc.dll").isFile || File(bundled, "libvlc.so").isFile) {
                    return bundled
                }
                if (File(resourcesDir, "libvlc.dll").isFile || File(resourcesDir, "libvlc.so").isFile) {
                    return File(resourcesDir)
                }
            }
            val osDir =
                when {
                    System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true) -> "windows"
                    System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true) -> "macos"
                    else -> "linux"
                }
            for (candidate in listOf(
                File("desktop/appResources/$osDir/vlc"),
                File("appResources/$osDir/vlc"),
            )) {
                if (File(candidate, "libvlc.dll").isFile || File(candidate, "libvlc.so").isFile) {
                    return candidate
                }
            }
            return File(resourcesDir ?: ".", "vlc")
        }
    }
}

/**
 * Properly loads libvlc DLLs and sets VLC_PLUGIN_PATH (unlike a bare NativeDiscoveryStrategy).
 */
private class BundledVlcDiscoveryStrategy(
    private val vlcDir: File,
) : BaseNativeDiscoveryStrategy(
        arrayOf("libvlc\\.dll", "libvlccore\\.dll", "libvlc\\.so", "libvlccore\\.so"),
        arrayOf("%s\\plugins", "%s/plugins"),
    ) {
    override fun supported(): Boolean = vlcDir.isDirectory

    override fun discoveryDirectories(): List<String> = listOf(vlcDir.absolutePath)

    override fun setPluginPath(pluginPath: String): Boolean =
        LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$pluginPath") == 0
}
