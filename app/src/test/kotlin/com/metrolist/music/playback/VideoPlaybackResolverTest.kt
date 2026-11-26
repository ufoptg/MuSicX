/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.innertube.models.SongItem
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
    fun `default lookup resolves video songs to their own video id`() {
        val resolver = VideoPlaybackResolver()

        val videoSong = videoMetadata(id = "abc123", musicVideoType = "MUSIC_VIDEO_TYPE_OMV")
        assertEquals("abc123", resolver.resolve(videoSong)?.videoId)
        assertNull(resolver.resolve(videoSong)?.mediaUri)

        // Audio tracks need an async counterpart lookup and cannot be resolved synchronously.
        assertNull(resolver.resolve(videoMetadata(id = "audio1", musicVideoType = null)))
    }

    @Test
    fun `playlist set video id is never treated as a youtube video id`() {
        val resolver = VideoPlaybackResolver()
        val audioSongWithPlaylistToken =
            MediaMetadata(
                id = "audio1",
                title = "Track",
                artists = emptyList(),
                duration = 210,
                setVideoId = "playlist-entry-token",
            )

        assertNull(resolver.resolve(audioSongWithPlaylistToken))
    }

    @Test
    fun `counterpart lookup picks matching official music video`() {
        val metadata =
            MediaMetadata(
                id = "audio1",
                title = "Track Name",
                artists = listOf(MediaMetadata.Artist(id = null, name = "Artist Name")),
                duration = 210,
            )

        val videoId =
            findCounterpartVideoId(
                metadata = metadata,
                items =
                    listOf(
                        songItem(
                            id = "wrong1",
                            title = "Unrelated Song",
                            artist = "Other Artist",
                            duration = 210,
                        ),
                        songItem(
                            id = "audioOnly",
                            title = "Track Name",
                            artist = "Artist Name",
                            duration = 210,
                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
                        ),
                        songItem(
                            id = "mv1",
                            title = "Track Name (Official Music Video)",
                            artist = "Artist Name",
                            duration = 212,
                            musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
                        ),
                    ),
            )

        assertEquals("mv1", videoId)
    }

    @Test
    fun `counterpart lookup rejects mismatched title and artist`() {
        val metadata =
            MediaMetadata(
                id = "audio1",
                title = "Track Name",
                artists = listOf(MediaMetadata.Artist(id = null, name = "Artist Name")),
                duration = 210,
            )

        val videoId =
            findCounterpartVideoId(
                metadata = metadata,
                items =
                    listOf(
                        songItem(
                            id = "wrong1",
                            title = "Completely Different",
                            artist = "Other Artist",
                            duration = 210,
                            musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
                        ),
                    ),
            )

        assertNull(videoId)
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

private fun songItem(
    id: String,
    title: String,
    artist: String,
    duration: Int?,
    musicVideoType: String? = "MUSIC_VIDEO_TYPE_OMV",
): SongItem =
    SongItem(
        id = id,
        title = title,
        artists = listOf(com.metrolist.innertube.models.Artist(id = null, name = artist)),
        duration = duration,
        musicVideoType = musicVideoType,
        thumbnail = "",
    )

private class FakeLookup(
    private val videoId: String?,
    private val mediaUri: String?,
) : VideoLookup {
    override fun lookup(metadata: MediaMetadata): VideoLookupResult? = VideoLookupResult(videoId = videoId, mediaUri = mediaUri)
}
