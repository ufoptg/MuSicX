/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VideoModeControllerTest {
    @Test
    fun `track change resets video mode to audio`() {
        val controller = VideoModeControllerForTest()
        controller.setVideoModeActiveForTest(true)

        controller.onMediaItemChanged("new-media-id")

        assertFalse(controller.isVideoModeActive.value)
        assertFalse(controller.isVideoModeResolving.value)
    }

    @Test
    fun `unavailable video reports toast and stays audio`() {
        val toasts = mutableListOf<String>()
        val controller = VideoModeControllerForTest(onToast = { toasts += it })
        controller.setResolvableVideoForTest(null)

        controller.requestVideoMode()

        assertFalse(controller.isVideoModeActive.value)
        assertEquals(listOf("Video unavailable for this track"), toasts)
    }
}

class VideoModeControllerForTest(
    onToast: (String) -> Unit = {},
) : VideoModeController(onVideoUnavailable = onToast) {
    var canResolveVideo: Boolean = true

    override fun isVideoAvailable(mediaId: String?): Boolean = canResolveVideo

    fun setVideoModeActiveForTest(enabled: Boolean) {
        activeVideoMode.value = enabled
        resolvingVideoMode.value = false
    }

    fun setResolvableVideoForTest(resolvable: Boolean?) {
        canResolveVideo = resolvable ?: false
    }
}
