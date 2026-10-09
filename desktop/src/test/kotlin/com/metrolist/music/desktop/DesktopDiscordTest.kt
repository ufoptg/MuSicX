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

    @Test
    fun headerIsLittleEndian() {
        val frame = encodeFrame(1, "{}")
        assertEquals(1, frame[0].toInt())
        assertEquals(0, frame[1].toInt())
        assertEquals(0, frame[2].toInt())
        assertEquals(0, frame[3].toInt())
        assertEquals(2, frame[4].toInt()) // length of "{}"
        assertEquals(0, frame[7].toInt())
    }

    private class FakeTransport(
        private val connectResult: Boolean = true,
        private val failWritesAfter: Int = Int.MAX_VALUE,
    ) : IpcTransport {
        val writes = mutableListOf<ByteArray>()
        var closed = false
        private val ready = "{\"evt\":\"READY\"}"

        override fun connect() = connectResult

        override fun write(bytes: ByteArray) {
            if (writes.size >= failWritesAfter) error("write failed")
            writes.add(bytes)
        }

        override fun readExact(length: Int): ByteArray? =
            if (length == 8) {
                java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(ready.toByteArray().size).array()
            } else {
                ready.toByteArray()
            }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun startConnectsAndHandshakes() {
        val fake = FakeTransport()
        val discord = DesktopDiscord("12345", transport = fake)
        discord.start()
        assertTrue(discord.connected)
        val (op, json) = decodeFrame(fake.writes.first())
        assertEquals(0, op)
        assertTrue(json.contains("\"client_id\":\"12345\""))
    }

    @Test
    fun updateSendsSetActivityFrame() {
        val fake = FakeTransport()
        val discord = DesktopDiscord("12345", transport = fake)
        discord.start()
        discord.update("Song", "Artist")
        val (op, json) = decodeFrame(fake.writes.last())
        assertEquals(1, op)
        assertTrue(json.contains("SET_ACTIVITY"))
        assertTrue(json.contains("\"details\":\"Song\""))
        assertTrue(json.contains("\"state\":\"Artist\""))
    }

    @Test
    fun stopClearsAndCloses() {
        val fake = FakeTransport()
        val discord = DesktopDiscord("12345", transport = fake)
        discord.start()
        discord.stop()
        assertTrue(!discord.connected)
        assertTrue(fake.closed)
        assertTrue(decodeFrame(fake.writes.last()).second.contains("null"))
    }

    @Test
    fun noOpWhenDisconnected() {
        val fake = FakeTransport(connectResult = false)
        val discord = DesktopDiscord("12345", transport = fake)
        discord.start()
        assertTrue(!discord.connected)
        discord.update("Song", "Artist")
        assertTrue(fake.writes.isEmpty())
    }

    @Test
    fun sendFailureMarksDisconnected() {
        val fake = FakeTransport(failWritesAfter = 1) // handshake ok, activity write fails
        val discord = DesktopDiscord("12345", transport = fake)
        discord.start()
        assertTrue(discord.connected)
        discord.update("Song", "Artist")
        assertTrue(!discord.connected)
    }
}
