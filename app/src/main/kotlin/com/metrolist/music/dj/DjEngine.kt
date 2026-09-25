/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.OpenRouterDefaultBaseUrl
import com.metrolist.music.constants.OpenRouterDefaultModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

data class DjTrackPick(
    val title: String,
    val artist: String,
)

data class DjLlmResult(
    val banter: String,
    val tracks: List<DjTrackPick>,
)

object DjEngine {
    private const val TAG = "DjEngine"
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    suspend fun fetchPicks(
        seedTitle: String,
        seedArtist: String,
        recent: List<String>,
        persona: String,
        wantBanter: Boolean,
        trackCount: Int,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Result<DjLlmResult> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API key required"))
            }
            val system =
                """
                You are $persona, an AI radio DJ.
                Output ONLY a JSON object: {"banter":"...","tracks":[{"title":"...","artist":"..."}]}
                Rules:
                - banter: ${if (wantBanter) "1 short spoken sentence of vibe/energy only (max 18 words), no emojis, do NOT name song titles (the app announces those)" else "empty string"}
                - tracks: exactly $trackCount real songs that fit the vibe; no duplicates of the recent list
                - Prefer variety of artists
                """.trimIndent()
            val user =
                buildString {
                    append("Seed: \"$seedTitle\" by $seedArtist.\n")
                    if (recent.isNotEmpty()) {
                        append("Recently played (do not repeat):\n")
                        recent.takeLast(20).forEach { append("- $it\n") }
                    }
                    append("Suggest the next $trackCount songs.")
                }
            chat(system, user, apiKey, baseUrl, model).mapCatching { parseDjResponse(it) }
        }

    /** Spoken line that names what just played / what's up next. */
    fun buildHostLine(
        isIntro: Boolean,
        previous: String?,
        nextTitle: String,
        nextArtist: String,
        flavor: String = "",
    ): String {
        val next = listOf(nextTitle, nextArtist).filter { it.isNotBlank() }.joinToString(" by ")
        val cleanFlavor = flavor.trim().trimEnd('.', '!', '?')
        return when {
            isIntro && next.isNotBlank() ->
                buildString {
                    if (cleanFlavor.isNotEmpty()) append("$cleanFlavor. ")
                    append("This is DJ 6. Up next: $next.")
                }
            previous != null && next.isNotBlank() ->
                buildString {
                    append("That was $previous.")
                    if (cleanFlavor.isNotEmpty()) append(" $cleanFlavor.")
                    append(" Coming up: $next.")
                }
            next.isNotBlank() ->
                buildString {
                    if (cleanFlavor.isNotEmpty()) append("$cleanFlavor. ")
                    append("Up next: $next.")
                }
            cleanFlavor.isNotEmpty() -> "$cleanFlavor."
            else -> ""
        }
    }

    suspend fun resolveTrack(
        title: String,
        artist: String,
        excludeIds: Set<String>,
    ): SongItem? =
        withContext(Dispatchers.IO) {
            val query = "$title $artist".trim()
            if (query.isBlank()) return@withContext null
            val items =
                YouTube
                    .search(query, YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    .orEmpty()
            items.firstOrNull { it.id.isNotEmpty() && it.id !in excludeIds }
                ?: items.firstOrNull { it.id.isNotEmpty() }
        }

    internal fun parseDjResponse(content: String): DjLlmResult {
        val raw = extractJsonObject(content) ?: throw IllegalArgumentException("No JSON in LLM response")
        val obj = json.parseToJsonElement(raw).jsonObject
        val banter = obj["banter"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val tracks =
            (obj["tracks"] as? JsonArray)
                ?.mapNotNull { el ->
                    val t = el.jsonObject
                    val title = t["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val artist = t["artist"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (title.isBlank()) null else DjTrackPick(title, artist)
                }.orEmpty()
        return DjLlmResult(banter = banter, tracks = tracks)
    }

    internal fun extractJsonObject(content: String): String? {
        val trimmed = content.trim()
        val fenced =
            Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        val body = fenced ?: trimmed
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return body.substring(start, end + 1)
    }

    private fun chat(
        system: String,
        user: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Result<String> {
        val url = baseUrl.ifBlank { OpenRouterDefaultBaseUrl }
        val body =
            buildJsonObject {
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put("content", system)
                            },
                        )
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", user)
                            },
                        )
                    },
                )
                put("model", model.ifBlank { OpenRouterDefaultModel })
                put("temperature", 0.8)
                put("max_tokens", 400)
            }
        val request =
            Request
                .Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/MetrolistGroup/Metrolist")
                .addHeader("X-Title", "Metrolist")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    Timber.w("$TAG: LLM HTTP ${response.code}: $responseBody")
                    return Result.failure(Exception("DJ LLM failed: ${response.code}"))
                }
                val content =
                    json
                        .parseToJsonElement(responseBody)
                        .jsonObject["choices"]
                        ?.jsonArray
                        ?.getOrNull(0)
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        .orEmpty()
                if (content.isBlank()) {
                    Result.failure(Exception("Empty LLM content"))
                } else {
                    Result.success(content)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: chat failed")
            Result.failure(e)
        }
    }
}
