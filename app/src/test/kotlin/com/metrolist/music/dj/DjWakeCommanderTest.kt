/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjWakeCommanderTest {
    @Test
    fun `grammar is a json list of phrases ending in unk`() {
        assertEquals(
            "[\"hey dj six\",\"skip\",\"[unk]\"]",
            DjWakeCommander.buildGrammar(listOf("hey dj six", "skip")),
        )
    }

    @Test
    fun `wake grammar has the wake phrase, a one-shot, and unk`() {
        assertTrue(DjWakeCommander.WAKE_GRAMMAR.contains("\"hey dj six\""))
        assertTrue(DjWakeCommander.WAKE_GRAMMAR.contains("\"dj six\""))
        assertTrue(DjWakeCommander.WAKE_GRAMMAR.contains("\"hey dj six skip\""))
        assertTrue(DjWakeCommander.WAKE_GRAMMAR.endsWith(",\"[unk]\"]"))
    }

    @Test
    fun `command grammar has bare commands but never the wake phrase`() {
        assertTrue(DjWakeCommander.COMMAND_GRAMMAR.contains("\"skip\""))
        assertTrue(DjWakeCommander.COMMAND_GRAMMAR.contains("\"pause\""))
        assertTrue(!DjWakeCommander.COMMAND_GRAMMAR.contains("hey dj"))
    }
}
