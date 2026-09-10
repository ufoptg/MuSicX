/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.innertubex.extraction.ExtractedStream
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams InnerTubeX audio via bundled LibVLC (vlcj-natives).
 * Handles WebM/Opus and AAC; no full-file download required.
 */
class DesktopAudioPlayer : AutoCloseable {
    private val discovered = AtomicBoolean(false)
    private val factory: MediaPlayerFactory by lazy {
        if (!NativeDiscovery().discover()) {
            error("LibVLC natives not found (vlcj-natives). Reinstall MuSicX Desktop.")
        }
        discovered.set(true)
        MediaPlayerFactory(
            "--no-video",
            "--intf",
            "dummy",
            "--no-video-title-show",
            "--quiet",
        )
    }
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

    private fun httpOptions(headers: Map<String, String>): Array<String> {
        val opts = mutableListOf(":no-video")
        headers["User-Agent"]?.let { opts += ":http-user-agent=$it" }
        headers["Referer"]?.let { opts += ":http-referrer=$it" }
        headers["Cookie"]?.let { opts += ":http-cookie=$it" }
        return opts.toTypedArray()
    }
}
