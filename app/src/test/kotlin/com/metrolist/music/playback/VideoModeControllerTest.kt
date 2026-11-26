/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoModeControllerTest {
    private val videoTargetNullUri = VideoPlaybackTarget(videoId = "abc123", mediaUri = null)
    private val videoTargetWithUri = VideoPlaybackTarget(videoId = "abc123", mediaUri = "https://example.com/video.mp4")

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
        controller.canResolveVideo = false

        controller.requestVideoMode()

        assertFalse(controller.isVideoModeActive.value)
        assertEquals(listOf("Video unavailable for this track"), toasts)
    }

    @Test
    fun `switchToVideo_preservesPositionAndRequestsPlay`() {
        val player = FakePlaybackPlayer(positionMs = 42_000, playWhenReady = true)
        val controller = VideoModeControllerForTest(player = player, resolver = FakeResolver(videoTargetNullUri))

        controller.requestVideoMode()

        assertEquals(42_000, player.lastSeekPositionMs)
        assertTrue(controller.isVideoModeActive.value)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `toggleWhenUnavailable_emitsUnavailableToast`() {
        val toasts = mutableListOf<String>()
        val controller = VideoModeControllerForTest(onToast = { toasts += it })
        controller.canResolveVideo = false

        controller.toggleVideoMode()

        assertEquals(listOf("Video unavailable for this track"), toasts)
    }

    @Test
    fun `videoAvailable_activatesVideoMode`() {
        val controller = VideoModeControllerForTest(resolver = FakeResolver(videoTargetWithUri))

        controller.requestVideoMode()

        assertTrue(controller.isVideoModeActive.value)
        assertFalse(controller.isVideoModeResolving.value)
    }

    @Test
    fun `videoNotAvailable_showsToast_andStaysAudio`() {
        val toasts = mutableListOf<String>()
        val controller = VideoModeControllerForTest(onToast = { toasts += it }, resolver = FakeResolver(videoTargetNullUri))

        controller.requestVideoMode()

        assertFalse(controller.isVideoModeActive.value)
        assertEquals(listOf("Video unavailable for this track"), toasts)
    }

    class FakePlaybackPlayer(
        positionMs: Long = 0,
        playWhenReady: Boolean = true,
    ) : VideoPlaybackPlayer {
        override var currentPosition: Long = positionMs
        override var playWhenReady: Boolean = playWhenReady
        override var lastSeekPositionMs: Long = positionMs

        override fun seekTo(position: Long) {
            lastSeekPositionMs = position
            currentPosition = position
        }

        override fun prepare() {}
    }

    class FakeResolver(val target: VideoPlaybackTarget?): VideoPlaybackResolver() {
        override fun resolve(mediaMetadata: MediaMetadata?): VideoPlaybackTarget? = target
    }

    class VideoModeControllerForTest(
        onToast: (String) -> Unit = {},
        override val player: VideoPlaybackPlayer? = null,
        resolver: VideoPlaybackResolver = VideoPlaybackResolver(),
    ) : VideoModeController(onVideoUnavailable = onToast, resolver = resolver, player = player) {
        var canResolveVideo: Boolean = true

        override fun isVideoAvailable(mediaId: String?): Boolean = canResolveVideo

        fun setVideoModeActiveForTest(enabled: Boolean) {
            activeVideoMode.value = enabled
            resolvingVideoMode.value = false
        }
    }
}