/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Desktop Qobuz hi-res resolver. Ports the core of the Android [QobuzAudioProvider] (search +
 * stream resolution over third-party resolver backends) to the JVM using Ktor + kotlinx JSON.
 * Best-effort: returns null on any failure so callers fall back to YouTube.
 */

package com.metrolist.music.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class DesktopQobuzQuality(val code: Int, val label: String) {
    AAC_320(5, "MP3 320"),
    CD_QUALITY(6, "CD FLAC"),
    HI_RES_LOSSLESS(27, "Hi-Res FLAC"),
    ;

    companion object {
        fun fromName(name: String): DesktopQobuzQuality =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HI_RES_LOSSLESS
    }
}

enum class DesktopQobuzBackend(val baseUrl: String, val label: String) {
    SQUID("https://qobuz.squid.wtf", "Squid"),
    MONOKENNY("https://qobuz.kennyy.com.br", "Monokenny"),
    ;

    companion object {
        fun fromName(name: String): DesktopQobuzBackend =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SQUID
    }
}

class DesktopQobuz : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val client =
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    connectTimeout(12, TimeUnit.SECONDS)
                    readTimeout(25, TimeUnit.SECONDS)
                }
            }
            install(HttpTimeout) { requestTimeoutMillis = 25_000 }
        }

    private val userAgent = "Mozilla/5.0"

    /**
     * Resolves a playable Qobuz stream URL for the given track, or null when not found.
     * Tries the requested quality then lower ones (a common catalog tier workaround).
     */
    suspend fun resolve(
        title: String,
        artists: List<String>,
        album: String?,
        isrc: String?,
        durationMs: Long?,
        quality: DesktopQobuzQuality,
        backend: DesktopQobuzBackend,
        countryCode: String,
    ): String? {
        val trackId = searchTrackId(title, artists, album, backend, countryCode) ?: return null
        for (code in qualityLadder(quality.code)) {
            streamUrl(trackId, code, backend, countryCode)?.let { return it }
        }
        return null
    }

    private suspend fun searchTrackId(
        title: String,
        artists: List<String>,
        album: String?,
        backend: DesktopQobuzBackend,
        countryCode: String,
    ): String? {
        val terms =
            linkedSetOf(
                listOf(title, artists.firstOrNull().orEmpty(), album.orEmpty()).filter { it.isNotBlank() }.joinToString(" "),
                listOf(title, artists.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" "),
                title,
            ).filter { it.isNotBlank() }
        val wantTitle = title.normalized()
        val wantArtists = artists.map { it.normalized() }.filter { it.isNotBlank() }
        for (term in terms) {
            val items = search(term, backend, countryCode) ?: continue
            val match =
                items.firstOrNull { element ->
                    val obj = element.jsonObjectOrNull() ?: return@firstOrNull false
                    val ct = obj.str("title").normalized()
                    val artistsOk = wantArtists.isEmpty() || wantedArtistMatches(wantArtists, obj)
                    (ct == wantTitle || ct.contains(wantTitle) || wantTitle.contains(ct)) && artistsOk
                }
            match?.jsonObjectOrNull()?.str("id")?.let { return it }
        }
        return null
    }

    private fun wantedArtistMatches(wantArtists: List<String>, obj: JsonObject): Boolean {
        val names = mutableListOf<String>()
        obj["performer"]?.jsonObjectOrNull()?.str("name")?.let(names::add)
        obj["album"]?.jsonObjectOrNull()?.let { album ->
            album["artist"]?.jsonObjectOrNull()?.str("name")?.let(names::add)
            album["artists"]?.jsonArrayOrNull()?.forEach { it.jsonObjectOrNull()?.str("name")?.let(names::add) }
        }
        val candidates = names.map { it.normalized() }.filter { it.isNotBlank() }
        return wantArtists.any { want -> candidates.any { candidate -> candidate == want || candidate.contains(want) || want.contains(candidate) } }
    }

    private suspend fun search(
        term: String,
        backend: DesktopQobuzBackend,
        countryCode: String,
    ): JsonArray? =
        runCatching {
            val text =
                client
                    .get("${backend.baseUrl}/api/get-music") {
                        url { parameters.append("q", term); parameters.append("offset", "0") }
                        header("Accept", "application/json")
                        header("Referer", "${backend.baseUrl}/")
                        header("User-Agent", userAgent)
                        header("Token-Country", countryCode.ifBlank { "US" })
                    }.bodyAsText()
            val root = json.parseToJsonElement(text).jsonObject
            if (root["success"]?.jsonPrimitive?.contentOrNull == "false") return null
            root["data"]?.jsonObjectOrNull()?.get("tracks")?.jsonObjectOrNull()?.get("items")?.jsonArrayOrNull()
        }.getOrNull()

    private suspend fun streamUrl(
        trackId: String,
        qualityCode: Int,
        backend: DesktopQobuzBackend,
        countryCode: String,
    ): String? =
        runCatching {
            val text =
                client
                    .get("${backend.baseUrl}/api/download-music") {
                        url { parameters.append("track_id", trackId); parameters.append("quality", qualityCode.toString()) }
                        header("Accept", "application/json")
                        header("Referer", "${backend.baseUrl}/")
                        header("User-Agent", userAgent)
                        header("Token-Country", countryCode.ifBlank { "US" })
                    }.bodyAsText()
            val root = json.parseToJsonElement(text).jsonObject
            if (root["success"]?.jsonPrimitive?.contentOrNull == "false") return null
            root["data"]?.jsonObjectOrNull()?.str("url")?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun qualityLadder(code: Int): List<Int> {
        val ladder = listOf(27, 6, 5)
        val start = ladder.indexOf(code)
        return if (start >= 0) ladder.drop(start) else listOf(code)
    }

    override fun close() = client.close()
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun String.normalized(): String {
    val ascii = Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
    return ascii
        .lowercase(Locale.US)
        .replace("&", " and ")
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")
}
