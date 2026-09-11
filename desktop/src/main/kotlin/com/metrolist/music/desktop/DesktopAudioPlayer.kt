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
 * Plays InnerTubeX audio via bundled LibVLC.
 *
 * The bundled VLC ships the full plugin set (see desktop/build.gradle.kts vlcSetup
 * shouldIncludeAllVlcFiles), so HTTP/HTTPS access + TLS are available. We stream the
 * googlevideo URL directly and let VLC buffer ahead — exactly like ExoPlayer does on
 * Android. googlevideo delivers progressive streams at ~real-time rate, so downloading
 * the whole file up front used to blow past the request timeout; progressive playback
 * only needs real-time throughput. A local-file download stays as a resilient fallback.
 */
class DesktopAudioPlayer : AutoCloseable {
    private val downloadClient =
        HttpClient(OkHttp) {
            expectSuccess = false
            // No total-request timeout: a slow-but-progressing download must not be killed.
            // Only guard against a truly stalled socket.
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
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

        // Primary: stream the URL directly through VLC (progressive, like Android/ExoPlayer).
        val streamedDirectly =
            withContext(Dispatchers.IO) {
                runCatching { startDirectStream(stream) }.getOrDefault(false)
            }
        if (streamedDirectly) {
            isPlaying = true
            return
        }

        // Fallback: download to a temp file, then play locally.
        withContext(Dispatchers.IO) { downloadThenPlay(stream) }
    }

    /**
     * Feeds the remote URL straight to VLC with the required HTTP headers and waits
     * briefly for decoding/output to begin. Returns true only when audio is actually
     * playing.
     */
    private suspend fun startDirectStream(stream: ExtractedStream): Boolean {
        stopInternal()
        lastError.set(null)

        val options = buildList {
            add(":no-video")
            add(":network-caching=5000")
            stream.headers["User-Agent"]?.takeIf { it.isNotBlank() }?.let { add(":http-user-agent=$it") }
            stream.headers["Referer"]?.takeIf { it.isNotBlank() }?.let { add(":http-referrer=$it") }
        }

        val started = mediaPlayer.media().play(stream.audioUrl, *options.toTypedArray())
        if (!started) return false

        mediaPlayer.audio().setVolume(100)
        mediaPlayer.audio().setMute(false)

        // Give a streamed source more time to connect + buffer than a local file.
        repeat(60) {
            delay(100)
            lastError.get()?.let {
                stopInternal()
                return false
            }
            when (mediaPlayer.status().state()) {
                State.PLAYING, State.BUFFERING -> return true
                State.ERROR, State.ENDED -> {
                    stopInternal()
                    return false
                }
                else -> Unit
            }
        }
        // Never reached PLAYING within the window — treat as a failure and fall back.
        stopInternal()
        return false
    }

    private suspend fun downloadThenPlay(stream: ExtractedStream) {
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
            val mrl = file.toURI().toASCIIString()
            val started = mediaPlayer.media().play(mrl, ":no-video")
            if (!started) {
                error("VLC could not open ${file.name} (itag ${stream.itag}, ${stream.mimeType})")
            }
            mediaPlayer.audio().setVolume(100)
            mediaPlayer.audio().setMute(false)

            repeat(30) {
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
 * BaseNativeDiscoveryStrategy.find() requires *every* filename pattern to match in the
 * directory, so the pattern list must be OS-specific. Passing both the Windows (.dll) and
 * Linux (.so) patterns together makes discovery impossible — on Windows the .so patterns
 * never match, so it can never satisfy all four. Only pass patterns for the current OS.
 */
private fun bundledVlcLibraryPatterns(): Array<String> {
    val os = System.getProperty("os.name").orEmpty()
    return when {
        os.contains("win", ignoreCase = true) -> arrayOf("libvlc\\.dll", "libvlccore\\.dll")
        os.contains("mac", ignoreCase = true) -> arrayOf("libvlc\\.dylib", "libvlccore\\.dylib")
        else -> arrayOf("libvlc\\.so.*", "libvlccore\\.so.*")
    }
}

/**
 * Properly loads libvlc DLLs and sets VLC_PLUGIN_PATH (unlike a bare NativeDiscoveryStrategy).
 */
private class BundledVlcDiscoveryStrategy(
    private val vlcDir: File,
) : BaseNativeDiscoveryStrategy(
        bundledVlcLibraryPatterns(),
        arrayOf("%s\\plugins", "%s/plugins"),
    ) {
    override fun supported(): Boolean = vlcDir.isDirectory

    override fun discoveryDirectories(): List<String> = listOf(vlcDir.absolutePath)

    override fun setPluginPath(pluginPath: String): Boolean =
        LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$pluginPath") == 0
}
