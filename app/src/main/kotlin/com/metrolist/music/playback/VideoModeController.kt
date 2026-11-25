/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

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
    initialVideoMode: Boolean = false,
) {
    private val activeVideoMode = MutableStateFlow(initialVideoMode)
    private val resolvingVideoMode = MutableStateFlow(false)

    val isVideoModeActive: StateFlow<Boolean> = activeVideoMode
    val isVideoModeResolving: StateFlow<Boolean> = resolvingVideoMode

    private var toast: (String) -> Unit = onVideoUnavailable
    private var currentMediaId: String? = null

    fun onMediaItemChanged(mediaId: String?) {
        currentMediaId = mediaId
        // Each new track starts in audio mode.
        activeVideoMode.value = false
        resolvingVideoMode.value = false
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
            return
        }
        resolvingVideoMode.value = true
        if (isVideoAvailable(currentMediaId)) {
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
