/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        resolvingVideoMode.value = true
        val target = if (isVideoAvailable(currentMediaId)) resolver.resolve(currentMediaMetadata) else null
        if (target != null) {
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

    protected open fun isVideoAvailable(mediaId: String?): Boolean = true

    companion object {
        const val VIDEO_UNAVAILABLE_MESSAGE = "Video unavailable for this track"
    }
}
