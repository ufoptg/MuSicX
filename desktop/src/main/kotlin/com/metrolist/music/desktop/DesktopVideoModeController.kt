/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.VideoPlaybackResolver
import com.metrolist.music.playback.VideoPlaybackTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Desktop video mode controller — mirrors the Android VideoModeController.
 *
 * Tracks whether inline video is active/resolving for the current track. Audio-only mode
 * retains VLCJ's `:no-video`; video mode enables video rendering. Defaults to audio mode;
 * a track change resets to audio mode.
 */
open class DesktopVideoModeController(
    private val resolver: VideoPlaybackResolver = VideoPlaybackResolver(),
    val player: DesktopAudioPlayer? = null,
    onVideoUnavailable: (String) -> Unit = {},
) {
    internal val activeVideoMode = MutableStateFlow(false)
    internal val resolvingVideoMode = MutableStateFlow(false)
    internal val currentVideoTarget = MutableStateFlow<VideoPlaybackTarget?>(null)

    val isVideoModeActive: StateFlow<Boolean> = activeVideoMode
    val isVideoModeResolving: StateFlow<Boolean> = resolvingVideoMode
    val currentVideoTargetFlow: StateFlow<VideoPlaybackTarget?> = currentVideoTarget

    private val toast: (String) -> Unit = onVideoUnavailable

    private var currentMediaId: String? = null
    private var currentMediaMetadata: MediaMetadata? = null

    private var savedPosition: Long = -1L
    private var savedPlayWhenReady: Boolean = true

    fun onMediaItemChanged(mediaId: String?, mediaMetadata: MediaMetadata? = null) {
        currentMediaId = mediaId
        currentMediaMetadata = mediaMetadata
        // Each new track starts in audio mode.
        activeVideoMode.value = false
        resolvingVideoMode.value = false
        currentVideoTarget.value = null
    }

    fun toggleVideoMode() {
        if (activeVideoMode.value) {
            setVideoMode(false)
        } else {
            setVideoMode(true)
        }
    }

    fun setVideoMode(enabled: Boolean) {
        if (activeVideoMode.value == enabled) return
        if (!enabled) {
            activeVideoMode.value = false
            resolvingVideoMode.value = false
            currentVideoTarget.value = null
            player?.let { seekAndRestore(it, savedPosition, savedPlayWhenReady) }
            return
        }
        resolvingVideoMode.value = true
        val target = if (isVideoAvailable(currentMediaId)) resolver.resolve(currentMediaMetadata) else null
        if (target != null) {
            // Preserve current playback position and request playback at that point.
            val currentPosition = player?.positionMs() ?: -1L
            val currentPlayWhenReady = player?.isPlaying ?: true
            savedPosition = currentPosition
            savedPlayWhenReady = currentPlayWhenReady
            player?.let { p ->
                p.seekToFraction(0f)
                if (p.isPlaying != currentPlayWhenReady) p.togglePause()
            }
            currentVideoTarget.value = target
            activeVideoMode.value = true
            resolvingVideoMode.value = false
        } else {
            resolvingVideoMode.value = false
            toast("Video unavailable for this track")
        }
    }

    fun requestVideoMode() {
        setVideoMode(true)
    }

    fun switchToVideo(target: VideoPlaybackTarget?) {
        setVideoMode(true)
        // Re-resolve after setting target so a valid target drives the actual switch.
        val resolvedTarget = if (isVideoAvailable(currentMediaId)) resolver.resolve(currentMediaMetadata) else null
        if (resolvedTarget != target) {
            val currentPosition = player?.positionMs() ?: -1L
            val currentPlayWhenReady = player?.isPlaying ?: true
            savedPosition = currentPosition
            savedPlayWhenReady = currentPlayWhenReady
            player?.let { p ->
                p.seekToFraction(0f)
                if (p.isPlaying != currentPlayWhenReady) p.togglePause()
            }
            currentVideoTarget.value = resolvedTarget
            activeVideoMode.value = true
            resolvingVideoMode.value = false
        }
    }

    fun switchToAudio() {
        setVideoMode(false)
    }

    protected open fun isVideoAvailable(mediaId: String?): Boolean = true

    companion object {
        const val VIDEO_UNAVAILABLE_MESSAGE = "Video unavailable for this track"
    }
}

/**
 * Adapts DesktopAudioPlayer into the minimal API used by DesktopVideoModeController so that
 * playback position / play state can be preserved across audio↔video switches.
 */
class DesktopPlaybackPlayer(private val audio: DesktopAudioPlayer) {
    val currentPosition: Long get() = audio.positionMs()
    var playWhenReady: Boolean
        get() = audio.isPlaying
        set(value) { if (value != audio.isPlaying) audio.togglePause() }

    fun seekToFraction(positionFraction: Float) {
        audio.seekToFraction(positionFraction)
    }

    fun prepare() {}
}

fun seekAndRestore(
    audio: DesktopAudioPlayer,
    position: Long,
    playWhenReady: Boolean,
) {
    if (position >= 0 && audio.durationMs() > 0) {
        audio.seekToFraction((position.toFloat() / audio.durationMs()).coerceIn(0f, 1f))
    }
    if (audio.isPlaying != playWhenReady) {
        audio.togglePause()
    }
}
