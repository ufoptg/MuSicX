/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPlaybackResolverTest {

    @Test
    fun `resolves existing video song to itself`() {
        val resolver = VideoPlaybackResolver(lookup = FakeLookup(videoId = "abc123", mediaUri = "https://example/video"))
        val metadata = videoMetadata(id = "abc123", musicVideoType = "MUSIC_VIDEO_TYPE_OMV")

        val target = resolver.resolve(metadata)

        assertEquals("abc123", target?.videoId)
        assertEquals("https://example/video", target?.mediaUri)
    }

    @Test
    fun `returnsNullWhenNoCounterpartFound`() {
        val resolver = VideoPlaybackResolver(lookup = FakeLookup(videoId = null, mediaUri = null))
        val metadata = videoMetadata(id = "audio1", musicVideoType = null)

        assertNull(resolver.resolve(metadata))
    }

    @Test
    fun `default lookup resolves video songs to their own video and audio counterparts`() {
        val resolver = VideoPlaybackResolver()

        val videoSong = videoMetadata(id = "abc123", musicVideoType = "MUSIC_VIDEO_TYPE_OMV")
        assertEquals("abc123", resolver.resolve(videoSong)?.videoId)
        assertNull(resolver.resolve(videoSong)?.mediaUri)

        val audioSongWithoutCounterpart = videoMetadata(id = "audio1", musicVideoType = null)
        assertNull(resolver.resolve(audioSongWithoutCounterpart))

        val audioSongWithCounterpart =
            MediaMetadata(
                id = "audio1",
                title = "Track",
                artists = emptyList(),
                duration = 210,
                setVideoId = "vid789",
            )
        assertEquals("vid789", resolver.resolve(audioSongWithCounterpart)?.videoId)
    }

    @Test
    fun `resolve returns null for unknown media`() {
        assertNull(VideoPlaybackResolver().resolve(null))
    }
}

private fun videoMetadata(id: String, musicVideoType: String?): MediaMetadata =
    MediaMetadata(
        id = id,
        title = "Title",
        artists = listOf(MediaMetadata.Artist(id = null, name = "Artist")),
        duration = 210,
        musicVideoType = musicVideoType,
    )

private class FakeLookup(
    private val videoId: String?,
    private val mediaUri: String?,
) : VideoLookup {
    override fun lookup(metadata: MediaMetadata): VideoLookupResult? = VideoLookupResult(videoId = videoId, mediaUri = mediaUri)
}
