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
            DjEngine.composeHostLine(
                kind = DjEngine.TalkKind.ANNOUNCE,
                isIntro = false,
                previous = "Born to Die — Lana Del Rey",
                nextTitle = "Video Games",
                nextArtist = "Lana Del Rey",
                flavor = "Keeping the late-night vibe",
            )
        assertTrue(line.contains("That was Born to Die — Lana Del Rey."))
        assertTrue(line.contains("Up next, Video Games by Lana Del Rey."))
        assertTrue(line.contains("Keeping the late-night vibe"))
    }

    @Test
    fun `buildHostLine intro names DJ 6 and next track`() {
        val line =
            DjEngine.composeHostLine(
                kind = DjEngine.TalkKind.BANTER,
                isIntro = true,
                previous = null,
                nextTitle = "Summertime Sadness",
                nextArtist = "Lana Del Rey",
                flavor = "Let's ride",
                userRequest = "DJ 6 play Summertime Sadness by Lana Del Rey",
            )
        assertTrue(line.contains("DJ 6"))
        assertTrue(line.contains("Summertime Sadness"))
        assertTrue(line.contains("coming right up"))
    }

    @Test
    fun `parsePlayQuery strips dj wake phrase`() {
        assertEquals(
            "God Mode by Eminem",
            DjStartRequest.parsePlayQuery("DJ 6 play God Mode by Eminem"),
        )
        assertEquals(
            "God Mode by Eminem",
            DjStartRequest.parsePlayQuery("hey dj6, play God Mode by Eminem"),
        )
        assertEquals("", DjStartRequest.parsePlayQuery("   "))
    }

    @Test
    fun `pickTalkKind intro is banter`() {
        assertEquals(DjEngine.TalkKind.BANTER, DjEngine.pickTalkKind(isIntro = true))
    }

    @Test
    fun `banter kind does not always announce next`() {
        val line =
            DjEngine.composeHostLine(
                kind = DjEngine.TalkKind.BANTER,
                isIntro = false,
                previous = "A — B",
                nextTitle = "C",
                nextArtist = "D",
                flavor = "Keep the energy up",
            )
        assertEquals("Keep the energy up.", line)
        assertTrue(!line.contains("Coming up"))
    }

    @Test
    fun `speechEndpoint maps chat completions url`() {
        assertEquals(
            "https://openrouter.ai/api/v1/audio/speech",
            DjHostTts.speechEndpoint("https://openrouter.ai/api/v1/chat/completions"),
        )
    }

    @Test
    fun `normalizeTtsModel migrates openai defaults to flux`() {
        assertEquals(
            "deepgram/flux-tts:free",
            DjHostTts.normalizeTtsModel("openai/gpt-4o-mini-tts"),
        )
        assertEquals(
            "deepgram/flux-tts:free",
            DjHostTts.normalizeTtsModel("deepgram/flux-tts:free"),
        )
    }

    @Test
    fun `command parser handles skip previous and play`() {
        assertEquals(DjCommand.Skip, DjCommandParser.parse("DJ 6 skip song"))
        assertEquals(DjCommand.Skip, DjCommandParser.parse("DJ 6, Skip song"))
        assertEquals(DjCommand.Skip, DjCommandParser.parse("skip"))
        assertEquals(DjCommand.Skip, DjCommandParser.parse("skip song"))
        assertEquals(DjCommand.Previous, DjCommandParser.parse("dj6 previous"))
        assertEquals(DjCommand.Previous, DjCommandParser.parse("previous"))
        assertEquals(DjCommand.Pause, DjCommandParser.parse("DJ 6 pause"))
        assertEquals(DjCommand.Resume, DjCommandParser.parse("hey DJ 6 resume"))
        val play = DjCommandParser.parse("DJ 6 play God Mode by Eminem") as DjCommand.Play
        assertEquals("God Mode by Eminem", play.query)
        val barePlay = DjCommandParser.parse("play God Mode by Eminem") as DjCommand.Play
        assertEquals("God Mode by Eminem", barePlay.query)
        assertEquals(null, DjCommandParser.parse("just some lyrics without a command"))
        assertEquals(
            null,
            DjCommandParser.parse("just some lyrics without a command", requireWake = true),
        )
        assertEquals(null, DjCommandParser.parse("skip", requireWake = true))
    }
}
