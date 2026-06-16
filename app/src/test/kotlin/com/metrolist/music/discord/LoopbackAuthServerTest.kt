package com.metrolist.music.discord

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LoopbackAuthServerTest {

    private lateinit var server: LoopbackAuthServer

    @Before
    fun setup() {
        server = LoopbackAuthServer(expectedState = "test-state")
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun start_returnsPositivePort() = runBlocking {
        val port = server.start()
        assertTrue("Port must be positive", port > 0)
        assertEquals(port, server.actualPort)
    }

    @Test
    fun callback_withValidCodeAndState_returnsSuccessAndCompletesDeferred() = runBlocking {
        val port = server.start()
        val url = URL("http://127.0.0.1:$port/callback?code=test-code&state=test-state")
        val body = url.openHttpConnection().getResponseText()
        assertTrue("Success page should be returned", body.contains("Authorization Complete"))
        val result = server.awaitCode(timeoutMs = 5_000L)
        assertEquals("test-code", result.code)
        assertEquals("test-state", result.state)
    }

    @Test
    fun callback_withMissingCode_returnsErrorPage() = runBlocking {
        val port = server.start()
        val url = URL("http://127.0.0.1:$port/callback?state=test-state")
        val body = url.openHttpConnection().getResponseText()
        assertTrue("Error page should mention missing code", body.contains("Missing authorization code"))
    }

    @Test
    fun callback_withMismatchedState_returnsErrorPage() = runBlocking {
        val port = server.start()
        val url = URL("http://127.0.0.1:$port/callback?code=test-code&state=wrong-state")
        val body = url.openHttpConnection().getResponseText()
        assertTrue("Error page should mention state validation", body.contains("State validation failed"))
    }

    @Test
    fun awaitCode_afterCancel_throws() {
        runBlocking {
            server.start()
            server.cancel()
            try {
                server.awaitCode(timeoutMs = 5_000L)
                fail("Expected awaitCode to throw after cancel")
            } catch (e: Exception) {
                assertTrue("Expected cancellation cause", e is kotlinx.coroutines.CancellationException || e.cause is kotlinx.coroutines.CancellationException)
            }
        }
    }

    private fun URL.openHttpConnection(): HttpURLConnection = openConnection() as HttpURLConnection

    private fun HttpURLConnection.getResponseText(): String {
        requestMethod = "GET"
        return inputStream.bufferedReader().use { it.readText() }
    }
}
