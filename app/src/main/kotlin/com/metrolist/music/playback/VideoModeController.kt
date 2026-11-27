/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.net.ConnectivityManager
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

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

    /** Replace the player's media source with the resolved video stream. Default no-op for test doubles. */
    fun setVideoSource(target: VideoPlaybackTarget) {}

    /** Restore the original audio media item (queue included) and seek to [positionMs]. */
    fun restoreAudioSource(positionMs: Long) {}
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
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
    private var videoModeGeneration: Long = 0L

    fun onMediaItemChanged(
        mediaId: String?,
        mediaMetadata: MediaMetadata? = null,
    ) {
        val sameMedia = mediaId != null && mediaId == currentMediaId
        currentMediaId = mediaId
        // A source swap within video mode keeps the same media id; preserve the current target.
        if (sameMedia) {
            currentMediaMetadata = mediaMetadata ?: currentMediaMetadata
            return
        }
        currentMediaMetadata = mediaMetadata
        // Each new track starts in audio mode.
        videoModeGeneration++
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
        if (enabled) {
            if (activeVideoMode.value || resolvingVideoMode.value) return
            val generation = ++videoModeGeneration
            resolvingVideoMode.value = true
            scope.launch {
                resolveAndActivateVideoMode(generation)
            }
        } else {
            videoModeGeneration++
            if (activeVideoMode.value) {
                // Capture the video position before restoring the audio media item.
                val position = player?.currentPosition ?: -1L
                val playWhenReady = player?.playWhenReady ?: true
                player?.restoreAudioSource(position)
                player?.playWhenReady = playWhenReady
            }
            activeVideoMode.value = false
            resolvingVideoMode.value = false
            currentVideoTarget.value = null
        }
    }

    private suspend fun resolveAndActivateVideoMode(generation: Long) {
        if (!isVideoAvailable(currentMediaId)) {
            finishUnavailable(generation)
            return
        }

        val target =
            if (connectivityManager != null) {
                resolver.resolveVideoStream(currentMediaMetadata, audioQuality, connectivityManager)
            } else {
                resolver.resolve(currentMediaMetadata)
            }

        if (generation != videoModeGeneration) return

        // Only commit to video mode when a real playable video stream URI is resolved.
        // A videoId without a mediaUri (or an audio-only stream) cannot be rendered by ExoPlayer.
        if (target != null && !target.mediaUri.isNullOrBlank()) {
            // Capture position BEFORE switching source (setVideoSource replaces the media item).
            val currentPosition = player?.currentPosition ?: -1L
            val currentPlayWhenReady = player?.playWhenReady ?: true
            savedPosition = currentPosition
            savedPlayWhenReady = currentPlayWhenReady
            player?.setVideoSource(target)
            player?.seekTo(currentPosition)
            player?.playWhenReady = currentPlayWhenReady
            currentVideoTarget.value = target
            activeVideoMode.value = true
            resolvingVideoMode.value = false
            Timber.tag(TAG).d("Video mode active for %s (stream=%s)", target.videoId, target.mediaUri)
        } else {
            finishUnavailable(generation)
        }
    }

    private fun finishUnavailable(generation: Long) {
        if (generation != videoModeGeneration) return
        resolvingVideoMode.value = false
        toast(VIDEO_UNAVAILABLE_MESSAGE)
        Timber.tag(TAG).d("Video unavailable for %s", currentMediaId)
    }

    fun requestVideoMode() {
        setVideoMode(true)
    }

    fun switchToVideo(target: VideoPlaybackTarget?) {
        if (target != null) {
            currentVideoTarget.value = target
            activeVideoMode.value = true
            resolvingVideoMode.value = false
        } else {
            setVideoMode(true)
        }
    }

    fun switchToAudio() {
        setVideoMode(false)
    }

    protected open fun isVideoAvailable(mediaId: String?): Boolean = true

    companion object {
        private const val TAG = "VideoModeController"
        const val VIDEO_UNAVAILABLE_MESSAGE = "Video unavailable for this track"
    }
}
