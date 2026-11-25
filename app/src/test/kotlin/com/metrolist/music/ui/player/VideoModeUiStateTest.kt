/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoModeUiStateTest {
    @Test
    fun shouldShowVideoSurface_onlyWhenVideoModeActiveAndPlayerHasVideo() {
        assertTrue(shouldShowVideoSurface(isVideoModeActive = true, hasVideo = true))
        assertFalse(shouldShowVideoSurface(isVideoModeActive = true, hasVideo = false))
        assertFalse(shouldShowVideoSurface(isVideoModeActive = false, hasVideo = true))
    }
}