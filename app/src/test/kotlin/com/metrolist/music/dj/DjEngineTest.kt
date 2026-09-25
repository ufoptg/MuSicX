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
}
