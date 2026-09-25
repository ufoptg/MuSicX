/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjEngineTest {
    @Test
    fun `parseDjResponse reads banter and tracks`() {
        val result =
            DjEngine.parseDjResponse(
                """{"banter":"Up next, a classic.","tracks":[{"title":"Born to Die","artist":"Lana Del Rey"}]}""",
            )
        assertEquals("Up next, a classic.", result.banter)
        assertEquals(1, result.tracks.size)
        assertEquals("Born to Die", result.tracks[0].title)
        assertEquals("Lana Del Rey", result.tracks[0].artist)
    }

    @Test
    fun `parseDjResponse strips fenced json`() {
        val result =
            DjEngine.parseDjResponse(
                """```json
{"banter":"","tracks":[{"title":"A","artist":"B"}]}
```""",
            )
        assertEquals("", result.banter)
        assertEquals("A", result.tracks[0].title)
    }

    @Test
    fun `extractJsonObject finds object in prose`() {
        val raw = DjEngine.extractJsonObject("""Sure! {"banter":"hi","tracks":[]} done""")
        assertTrue(raw!!.startsWith("{"))
        assertTrue(raw.endsWith("}"))
    }

    @Test
    fun `buildHostLine announces previous and next`() {
        val line =
            DjEngine.buildHostLine(
                isIntro = false,
                previous = "Born to Die — Lana Del Rey",
                nextTitle = "Video Games",
                nextArtist = "Lana Del Rey",
                flavor = "Keeping the late-night vibe",
            )
        assertTrue(line.contains("That was Born to Die — Lana Del Rey"))
        assertTrue(line.contains("Coming up: Video Games by Lana Del Rey"))
        assertTrue(line.contains("Keeping the late-night vibe"))
    }

    @Test
    fun `buildHostLine intro names DJ 6 and next track`() {
        val line =
            DjEngine.buildHostLine(
                isIntro = true,
                previous = null,
                nextTitle = "Summertime Sadness",
                nextArtist = "Lana Del Rey",
                flavor = "Let's ride",
            )
        assertTrue(line.contains("DJ 6"))
        assertTrue(line.contains("Up next: Summertime Sadness by Lana Del Rey"))
    }

    @Test
    fun `speechEndpoint maps chat completions url`() {
        assertEquals(
            "https://openrouter.ai/api/v1/audio/speech",
            DjHostTts.speechEndpoint("https://openrouter.ai/api/v1/chat/completions"),
        )
    }
}
