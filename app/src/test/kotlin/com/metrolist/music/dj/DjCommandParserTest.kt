/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjCommandParserTest {
    @Test
    fun `one-shot wake command parses when wake is required`() {
        assertEquals(DjCommand.Skip, DjCommandParser.parse("Hey DJ 6, skip", requireWake = true))
    }

    @Test
    fun `spoken six is normalised and still wakes`() {
        assertTrue(DjCommandParser.containsWake("hey dj six"))
        assertTrue(DjCommandParser.containsWake("dee jay six"))
        assertEquals(DjCommand.Skip, DjCommandParser.parse("hey dj six next", requireWake = true))
    }

    @Test
    fun `all fixed commands parse after the wake phrase`() {
        assertEquals(DjCommand.Previous, DjCommandParser.parse("hey dj six previous", requireWake = true))
        assertEquals(DjCommand.Pause, DjCommandParser.parse("hey dj six pause", requireWake = true))
        assertEquals(DjCommand.Resume, DjCommandParser.parse("hey dj six resume", requireWake = true))
    }

    @Test
    fun `bare command is rejected when wake is required`() {
        assertEquals(null, DjCommandParser.parse("skip", requireWake = true))
    }

    @Test
    fun `bare command accepted in the post-wake command window`() {
        assertEquals(DjCommand.Skip, DjCommandParser.parse("skip", requireWake = false))
        assertEquals(DjCommand.Pause, DjCommandParser.parse("pause", requireWake = false))
    }

    @Test
    fun `plain dj without the six does not wake`() {
        assertTrue(!DjCommandParser.containsWake("hey dj"))
    }

    @Test
    fun `play with a free-form query still works`() {
        assertEquals("taylor swift", (DjCommandParser.parse("play taylor swift", requireWake = false) as DjCommand.Play).query)
        assertEquals("born to die", (DjCommandParser.parse("hey dj 6 play born to die", requireWake = true) as DjCommand.Play).query)
    }
}
