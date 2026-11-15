package dev.musicx.android.recognition

import dev.musicx.shared.model.ArtistTiny
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.recognition.RecognitionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionMatchTest {
    private val result = RecognitionResult("track", "Don't Stop", "Artist")
    private val song = SongItem("video", "DON’T STOP", null, listOf(ArtistTiny("artist", "Artist")), null)

    @Test
    fun matchesPunctuationAndCaseButRejectsOtherArtistsAndRecordings() {
        assertTrue(song.matchesRecognition(result))
        assertFalse(song.copy(title = "Don't Stop (Live)").matchesRecognition(result))
        assertFalse(song.copy(title = "Don't Stop (Remix)").matchesRecognition(result))
        assertFalse(song.copy(artists = listOf(ArtistTiny("cover", "Cover Artist"))).matchesRecognition(result))
        assertFalse(song.copy(artists = emptyList()).matchesRecognition(result))
        assertFalse(song.matchesRecognition(result.copy(artist = "")))
        assertFalse(song.matchesRecognition(result.copy(title = "")))
    }
}
