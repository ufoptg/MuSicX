/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient.Companion.WEB_REMIX
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

data class SearchHit(
    val videoId: String,
    val title: String,
    val subtitle: String? = null,
)

/**
 * Thin JVM wrapper around InnerTubeX for desktop search.
 * ponytail: local JSON walk instead of porting Android page parsers; replace when :innertube is JVM-capable.
 */
class DesktopInnerTube : AutoCloseable {
    private val httpClient = createClient()
    private val innerTube = InnerTube(httpClient)

    suspend fun searchSongs(query: String): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val raw =
            innerTube
                .search(
                    client = WEB_REMIX,
                    query = trimmed,
                    params = FILTER_SONG,
                    setLogin = false,
                ).body<JsonObject>()

        return extractHits(raw)
    }

    override fun close() {
        innerTube.close()
        httpClient.close()
    }

    companion object {
        private const val FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

        @OptIn(ExperimentalSerializationApi::class)
        private val jsonConfig =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        @OptIn(ExperimentalSerializationApi::class)
        private fun createClient(): HttpClient =
            HttpClient(OkHttp) {
                expectSuccess = false
                install(ContentNegotiation) {
                    json(jsonConfig)
                }
                install(ContentEncoding) {
                    gzip(0.9F)
                    deflate(0.8F)
                }
                engine {
                    config {
                        connectTimeout(30, TimeUnit.SECONDS)
                        readTimeout(60, TimeUnit.SECONDS)
                        writeTimeout(60, TimeUnit.SECONDS)
                        retryOnConnectionFailure(true)
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                }
            }

        internal fun extractHits(root: JsonElement): List<SearchHit> {
            val hits = LinkedHashMap<String, SearchHit>()
            fun walk(el: JsonElement) {
                when (el) {
                    is JsonObject -> {
                        el["musicResponsiveListItemRenderer"]?.jsonObject?.let { renderer ->
                            parseRenderer(renderer)?.let { hit ->
                                hits.putIfAbsent(hit.videoId, hit)
                            }
                        }
                        el.values.forEach(::walk)
                    }
                    is JsonArray -> el.forEach(::walk)
                    else -> Unit
                }
            }
            walk(root)
            return hits.values.toList()
        }

        private fun parseRenderer(renderer: JsonObject): SearchHit? {
            val videoId =
                renderer["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                    ?: findVideoId(renderer)
                    ?: return null

            val flexColumns = renderer["flexColumns"]?.jsonArray.orEmpty()
            val title =
                flexColumns
                    .getOrNull(0)
                    ?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")
                    ?.jsonObject
                    ?.let { firstText(it) }
                    ?: return null
            val subtitle =
                flexColumns
                    .getOrNull(1)
                    ?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")
                    ?.jsonObject
                    ?.let { firstText(it) }

            return SearchHit(videoId = videoId, title = title, subtitle = subtitle)
        }

        private fun findVideoId(el: JsonElement): String? {
            when (el) {
                is JsonObject -> {
                    el["videoId"]?.jsonPrimitive?.contentOrNull?.let { return it }
                    el["watchEndpoint"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull?.let { return it }
                    for (value in el.values) {
                        findVideoId(value)?.let { return it }
                    }
                }
                is JsonArray -> {
                    for (item in el) {
                        findVideoId(item)?.let { return it }
                    }
                }
                else -> Unit
            }
            return null
        }

        private fun firstText(flexColumnRenderer: JsonObject): String? {
            val runs =
                flexColumnRenderer["text"]?.jsonObject?.get("runs")?.jsonArray
                    ?: return null
            val text =
                runs.joinToString("") { run ->
                    (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                }.trim()
            return text.takeIf { it.isNotEmpty() }
        }
    }
}
