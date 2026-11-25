/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopVideoModeControllerTest {
    @Test
    fun trackChange_resetsToAudioMode() {
        val controller = DesktopVideoModeControllerForTest()

        controller.setVideoModeActiveForTest(true)

        controller.onMediaItemChanged("new-id")

        assertFalse(controller.isVideoModeActive.value)
    }

    @Test
    fun unavailableVideo_emitsToast() {
        val toasts = mutableListOf<String>()
        val controller = DesktopVideoModeControllerForTest(onToast = { toasts += it }, hasVideo = false)

        controller.toggleVideoMode()

        assertEquals(listOf("Video unavailable for this track"), toasts)
    }
}

class DesktopVideoModeControllerForTest(
    onToast: (String) -> Unit = {},
    val hasVideo: Boolean = true,
) : DesktopVideoModeController(onVideoUnavailable = onToast) {
    override fun isVideoAvailable(mediaId: String?): Boolean = hasVideo

    fun setVideoModeActiveForTest(enabled: Boolean) {
        activeVideoMode.value = enabled
        resolvingVideoMode.value = false
    }
}