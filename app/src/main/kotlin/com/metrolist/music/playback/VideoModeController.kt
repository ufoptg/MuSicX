/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.ConnectivityManager
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.VideoPlaybackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Minimal abstraction over an Android playback player used by VideoModeController.
 *
 * Tracks the currently played position, whether playback is ready, and the timestamp of the last seek.
 */
interface VideoPlaybackPlayer {
    val currentPosition: Long
    var playWhenReady: Boolean
    var lastSeekPositionMs: Long
    fun seekTo(position: Long)
    fun prepare()

    /** Replace the player's media source with a video stream URI. Default no-op for test doubles. */
    fun setVideoSource(videoUri: String) {}
}

/**
 * Inline now-playing audio<->video mode state for Android.
 *
 * Video mode is off by default; a track change resets to audio mode. Enabling video mode
 * resolves a watchable counterpart for the current track before committing to video, and
 * falls back to an audio toast when no video is available.
 */
open class VideoModeController(
    onVideoUnavailable: (String) -> Unit = {},
    private val resolver: VideoPlaybackResolver = VideoPlaybackResolver(),
    open val player: VideoPlaybackPlayer? = null,
    private val scope: CoroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main),
    private val connectivityManager: ConnectivityManager? = null,
    private val audioQuality: AudioQuality = AudioQuality.AUTO,
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
            return
        }
        // Launch async resolution on the provided scope.
        scope.launch {
            resolveAndActivateVideoMode()
        }
    }

    private suspend fun resolveAndActivateVideoMode() {
        resolvingVideoMode.value = true
        // Try the async resolution first (production path).
        val target = if (connectivityManager != null) {
            resolver.resolveVideoStream(currentMediaMetadata, audioQuality, connectivityManager!!)
        } else {
            resolver.resolve(currentMediaMetadata)
        }

        // Only commit to video mode when a real playable stream URI is resolved.
        // A videoId without a mediaUri cannot be rendered by ExoPlayer (would show black).
        if (target != null && target.mediaUri != null) {
            // Capture position BEFORE switching source (setVideoSource resets the player).
            val currentPosition = player?.currentPosition ?: -1L
            val currentPlayWhenReady = player?.playWhenReady ?: true
            savedPosition = currentPosition
            savedPlayWhenReady = currentPlayWhenReady
            // Switch the player to the video stream so MusicVideoSurface renders video.
            player?.setVideoSource(target.mediaUri)
            // Restore the preserved playback position on the video source.
            player?.seekTo(currentPosition)
            player?.playWhenReady = currentPlayWhenReady
            currentVideoTarget.value = target
            activeVideoMode.value = true
            resolvingVideoMode.value = false
        } else {
            resolvingVideoMode.value = false
            toast(VIDEO_UNAVAILABLE_MESSAGE)
        }
    }

    fun requestVideoMode() {
        setVideoMode(true)
    }

    fun switchToVideo(target: VideoPlaybackTarget?) {
        setVideoMode(true)
        // Re-resolve after setting target so a valid target drives the actual switch.
        val resolvedTarget = if (isVideoAvailable(currentMediaId)) resolver.resolve(currentMediaMetadata) else null
        // Only switch when a real playable stream URI is resolved.
        if (resolvedTarget != null && resolvedTarget.mediaUri != null && resolvedTarget != target) {
            val currentPosition = player?.currentPosition ?: -1L
            val currentPlayWhenReady = player?.playWhenReady ?: true
            savedPosition = currentPosition
            savedPlayWhenReady = currentPlayWhenReady
            player?.setVideoSource(resolvedTarget.mediaUri)
            player?.seekTo(currentPosition)
            player?.playWhenReady = true
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