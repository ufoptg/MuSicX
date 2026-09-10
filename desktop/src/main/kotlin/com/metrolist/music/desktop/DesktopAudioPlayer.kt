/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.innertubex.extraction.ExtractedStream
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import java.io.File

/**
 * Streams InnerTubeX audio via LibVLC bundled into the Windows .exe (vlc-setup + appResources).
 */
class DesktopAudioPlayer : AutoCloseable {
    private val factory: MediaPlayerFactory by lazy { createFactory() }
    private val mediaPlayer: MediaPlayer by lazy { factory.mediaPlayers().newMediaPlayer() }

    @Volatile
    var isPlaying: Boolean = false
        private set

    fun play(stream: ExtractedStream) {
        check(stream.sabrBootstrap == null) { "SABR streams are not supported yet" }
        val options = httpOptions(stream.headers)
        val started = mediaPlayer.media().play(stream.audioUrl, *options)
        if (!started) {
            error("VLC could not start stream (itag ${stream.itag}, ${stream.mimeType})")
        }
        isPlaying = true
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
        mediaPlayer.controls().stop()
        isPlaying = false
    }

    override fun close() {
        runCatching { mediaPlayer.release() }
        runCatching { factory.release() }
        isPlaying = false
    }

    private fun createFactory(): MediaPlayerFactory {
        val vlcDir = resolveBundledVlcDir()
        System.setProperty("jna.library.path", vlcDir.absolutePath)
        val discovery = NativeDiscovery(BundledVlcDiscoveryStrategy(vlcDir))
        if (!discovery.discover()) {
            error(
                "Bundled LibVLC not found at ${vlcDir.absolutePath}. " +
                    "Reinstall MuSicX Desktop (resources/vlc).",
            )
        }
        return MediaPlayerFactory(
            discovery,
            "--no-video",
            "--intf",
            "dummy",
            "--no-video-title-show",
            "--quiet",
        )
    }

    private fun httpOptions(headers: Map<String, String>): Array<String> {
        val opts = mutableListOf(":no-video")
        headers["User-Agent"]?.let { opts += ":http-user-agent=$it" }
        headers["Referer"]?.let { opts += ":http-referrer=$it" }
        headers["Cookie"]?.let { opts += ":http-cookie=$it" }
        return opts.toTypedArray()
    }

    companion object {
        private fun resolveBundledVlcDir(): File {
            val resourcesDir = System.getProperty("compose.application.resources.dir")
            if (!resourcesDir.isNullOrBlank()) {
                val bundled = File(resourcesDir, "vlc")
                if (bundled.isDirectory) return bundled
                // Some packagers flatten appResources/<os>/ contents into resources.dir
                if (File(resourcesDir, "libvlc.dll").isFile || File(resourcesDir, "libvlc.so").isFile) {
                    return File(resourcesDir)
                }
            }
            // Dev / unpackaged fallback: desktop/appResources/<os>/vlc
            val osDir =
                when {
                    System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true) -> "windows"
                    System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true) -> "macos"
                    else -> "linux"
                }
            val local = File("desktop/appResources/$osDir/vlc")
            if (local.isDirectory) return local
            val cwd = File("appResources/$osDir/vlc")
            if (cwd.isDirectory) return cwd
            return File(resourcesDir ?: ".", "vlc")
        }
    }
}

private class BundledVlcDiscoveryStrategy(
    private val vlcDir: File,
) : NativeDiscoveryStrategy {
    override fun supported(): Boolean = vlcDir.isDirectory

    override fun discover(): String? =
        vlcDir.takeIf { it.isDirectory }?.absolutePath

    override fun onFound(path: String): Boolean = true

    override fun onSetPluginPath(path: String): Boolean = true
}
