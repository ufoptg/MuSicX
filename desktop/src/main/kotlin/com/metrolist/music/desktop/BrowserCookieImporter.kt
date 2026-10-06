/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

/**
 * Launches a dedicated Chrome/Edge profile at the Google sign-in page and reads
 * the YouTube cookies directly from the browser over the DevTools protocol.
 *
 * Rationale: Google shares cookies across Google/YouTube when the user signs in.
 * The browser exposes them through the DevTools protocol while it's alive, so we
 * don't need decrypted-cookie-store parsing. As soon as we see a SAPISID cookie,
 * we close the launched browser and return the cookie header.
 *
 * Requires Chrome or Edge installed. Sign-in inside the launched browser is the
 * usual flow (you'll sign in with your Google account, land on YouTube, and the
 * app picks it up from there).
 */
object BrowserCookieImporter {
    const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/"

    fun isWindows(): Boolean =
        (System.getProperty("os.name") ?: "").lowercase().contains("win")

    private fun browserExe(): File? {
        if (!isWindows()) return null
        val localAppData = System.getenv("LOCALAPPDATA") ?: ""
        val programFiles = System.getenv("PROGRAMFILES") ?: ""
        val programFilesX86 = System.getenv("PROGRAMFILES(X86)") ?: ""
        val candidates =
            listOf(
                File(localAppData, "Google\\Chrome\\Application\\chrome.exe"),
                File(programFiles, "Google\\Chrome\\Application\\chrome.exe"),
                File(localAppData, "Microsoft\\Edge\\Application\\msedge.exe"),
                File(programFiles, "Microsoft\\Edge\\Application\\msedge.exe"),
                File(programFilesX86, "Microsoft\\Edge\\Application\\msedge.exe"),
            )
        return candidates.firstOrNull { it.isFile }
    }

    private fun profileDir(): File {
        val base =
            (System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home"))
                .let { File(it, "MuSicX") }
        val dir = File(base, "browser-profile")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Opens the temp-profile browser at the Google sign-in page and returns the
     * youtube.com cookie header as soon as SAPISID is visible there. The browser
     * is terminated afterwards. Throws with a user-readable message on failure.
     */
    suspend fun import(): String =
        withContext(Dispatchers.IO) {
            val exe =
                browserExe()
                    ?: error("Chrome or Edge not found. Sign in via the system browser and paste the cookie below.")
            val profile = profileDir()
            val port = ServerSocket(0).use { it.localPort }
            DesktopLog.log("BrowserCookieImporter: launching ${exe.name} with temp profile ${profile.absolutePath} (CDP port $port)")
            val process =
                ProcessBuilder(
                    exe.absolutePath,
                    "--user-data-dir=${profile.absolutePath}",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--remote-debugging-port=$port",
                    SIGN_IN_URL,
                ).start()
            try {
                val cookies =
                    runCatching { pollUntilSapisid(port, process) }
                        .getOrElse { error(it.message ?: it::class.simpleName ?: "Unknown error") }
                DesktopLog.log("BrowserCookieImporter: SAPISID found, terminating helper browser")
                runCatching { process.destroy() }
                runCatching { process.waitFor(10, TimeUnit.SECONDS) }
                cookies
            } catch (t: Throwable) {
                runCatching { process.destroy() }
                throw t
            }
        }

    /**
     * Polls CDP for youtube.com cookies; returns as soon as SAPISID shows up.
     * `null` whenever the browser process ends first.
     */
    private suspend fun pollUntilSapisid(port: Int, process: Process): String {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15)
        var attempt = 0
        while (true) {
            if (!process.isAlive) {
                DesktopLog.log("BrowserCookieImporter: helper browser exited before SAPISID was seen")
                error("The browser was closed before a YouTube sign-in cookie was detected. Sign in (so you land on music.youtube.com), wait, then close the window.")
            }
            val list = httpJson("http://127.0.0.1:$port/json/list")
            if (list != null && list.startsWith('[')) {
                runCatching {
                    val hit = cookiesFromWsTargets(list)
                    if (hit != null) return hit
                }.onFailure { DesktopLog.log("BrowserCookieImporter: ws probe failed: ${it.message}") }
            }
            if (System.nanoTime() > deadline) {
                error("Timed out waiting for YouTube sign-in cookies. Please try again.")
            }
            attempt++
            delay(1000)
        }
    }

    private fun cookiesFromWsTargets(listJson: String): String? {
        val elements = Json.parseToJsonElement(listJson).jsonArray
        for (element in elements) {
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            val url = obj["url"]?.jsonPrimitive?.contentOrNull
            val wsUrl = obj["webSocketDebuggerUrl"]?.jsonPrimitive?.contentOrNull
            if (type == "page" && wsUrl != null && (url == null || url.contains("youtube.com") || url.contains("google.com"))) {
                val cookies = wsGetAllCookies(wsUrl)
                if (cookies != null && cookies.any { it.first == "SAPISID" })
                    return cookies.joinToString("; ") { "${it.first}=${it.second}" }
            }
        }
        return null
    }

    private fun wsGetAllCookies(wsUrl: String): List<Pair<String, String>>? {
        val client = HttpClient.newHttpClient()
        val listener = Listener()
        val ws =
            client.newWebSocketBuilder().buildAsync(URI.create(wsUrl), listener).get(10, TimeUnit.SECONDS)
        try {
            ws.sendText("{\"id\":1,\"method\":\"Network.getAllCookies\"}", true).get(10, TimeUnit.SECONDS)
            val raw = listener.take(10, TimeUnit.SECONDS) ?: return null
            val message = Json.parseToJsonElement(raw).jsonObject
            val cookies =
                message["result"]?.jsonObject?.get("cookies")?.jsonArray ?: return null
            val list = mutableListOf<Pair<String, String>>()
            for (cookie in cookies) {
                val obj = cookie.jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: continue
                val domain = obj["domain"]?.jsonPrimitive?.contentOrNull ?: continue
                if (domain == "youtube.com" || domain.endsWith(".youtube.com")) {
                    list += name to value
                }
            }
            return list.takeIf { it.isNotEmpty() }
        } finally {
            runCatching { ws.abort() }
            client.close()
        }
    }

    private fun httpJson(url: String): String? =
        runCatching {
            val client = HttpClient.newHttpClient()
            try {
                val response =
                    client
                        .send(
                            HttpRequest
                                .newBuilder(URI.create(url))
                                .GET()
                                .build(),
                            HttpResponse.BodyHandlers.ofString(),
                        )
                if (response.statusCode() in 200..299) response.body() else null
            } finally {
                client.close()
            }
        }.getOrNull()

    /** Compacts ws responses to the single response message we care about. */
    private class Listener : WebSocket.Listener {
        private val buffer = StringBuilder()
        private var result: CompletableFuture<String> = CompletableFuture()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*> {
            buffer.append(data)
            if (last) {
                result.complete(buffer.toString())
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            result.completeExceptionally(error)
        }

        fun take(timeout: Long, unit: TimeUnit): String? =
            try {
                result.get(timeout, unit)
            } catch (_: Exception) {
                null
            }
    }
}
