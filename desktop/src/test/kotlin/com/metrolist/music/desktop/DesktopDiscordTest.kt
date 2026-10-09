package com.metrolist.music.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDiscordTest {
    @Test
    fun frameRoundTripsAscii() {
        val frame = encodeFrame(1, "{\"cmd\":\"SET_ACTIVITY\"}")
        val (op, json) = decodeFrame(frame)
        assertEquals(1, op)
        assertEquals("{\"cmd\":\"SET_ACTIVITY\"}", json)
    }

    @Test
    fun frameRoundTripsMultiByte() {
        val payload = "{\"details\":\"café — 曲\"}"
        val (op, json) = decodeFrame(encodeFrame(0, payload))
        assertEquals(0, op)
        assertEquals(payload, json)
    }

    @Test
    fun handshakeCarriesClientIdAndVersion() {
        val json = handshakePayload("12345")
        assertTrue(json.contains("\"client_id\":\"12345\""))
        assertTrue(json.contains("\"v\":1"))
    }

    @Test
    fun activityPayloadOmitsStateWhenNull() {
        assertTrue(setActivityPayload("n1", 42L, "Song", null).contains("\"details\":\"Song\""))
        assertTrue(!setActivityPayload("n1", 42L, "Song", null).contains("\"state\""))
        assertTrue(setActivityPayload("n1", 42L, "Song", "Artist").contains("\"state\":\"Artist\""))
    }
}
