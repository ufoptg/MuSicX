/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertubex.models.YouTubeLocale
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

@Serializable
data class SearchHit(
    val videoId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
)

/**
 * A titled row of songs for the YouTube Music Home feed.
 * Skipped entirely when [DesktopSpotify.hideYoutubeHome] is true
 * (enableSpotify && useSpotifyHome && spDc != "" && spotifyHomeOnly).
 */
data class HomeRow(
    val title: String,
    val items: List<SearchHit>,
)

/** A playlist in the user's library (liked/saved playlists tab). */
data class PlaylistHit(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
)

/**
 * Thin JVM wrapper around InnerTubeX for desktop search + stream resolve.
 * ponytail: local JSON walk instead of porting Android page parsers; replace when :innertube is JVM-capable.
 */
class DesktopInnerTube : AutoCloseable {
    private val httpClient = createClient()
    private val innerTube = InnerTube(httpClient)
    private val configStore =
        RemotePlayerConfigStore(
            httpClient = httpClient,
            repository = PlayerConfigRepository.disabled(),
        )
    private val cipherService = YouTubeCipherService(httpClient, configStore)
    private val extractor =
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(httpClient, innerTube, configStore),
            cipherService = cipherService,
            innerTube = innerTube,
        )

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
    suspend fun searchAlbums(query: String): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val raw =
            innerTube
                .search(
                    client = WEB_REMIX,
                    query = trimmed,
                    params = FILTER_ALBUM,
                    setLogin = false,
                ).body<JsonObject>()

        return extractBrowseHits(raw) { id -> id.startsWith("MPRE") }
    }

    suspend fun searchArtists(query: String): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val raw =
            innerTube
                .search(
                    client = WEB_REMIX,
                    query = trimmed,
                    params = FILTER_ARTIST,
                    setLogin = false,
                ).body<JsonObject>()

        return extractBrowseHits(raw) { id -> id.startsWith("UC") }
    }

    suspend fun searchPlaylists(query: String): List<PlaylistHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val raw =
            innerTube
                .search(
                    client = WEB_REMIX,
                    query = trimmed,
                    params = FILTER_PLAYLIST,
                    setLogin = false,
                ).body<JsonObject>()

        val hits = LinkedHashMap<String, PlaylistHit>()
        fun walk(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    el["musicTwoRowItemRenderer"]?.jsonObject?.let { renderer ->
                        parsePlaylistHit(renderer)?.let { hit -> hits.putIfAbsent(hit.id, hit) }
                    }
                    el.values.forEach(::walk)
                }
                is JsonArray -> el.forEach(::walk)
                else -> Unit
            }
        }
        walk(raw)
        return hits.values.toList()
    }


    /**
     * Home feed as titled shelves. Prefers the real YTM home browse; falls back to
     * category-search-seeded shelves when the browse fails or yields nothing playable
     * (offline, blocked, unexpected layout).
     */
    suspend fun homeFeed(): List<HomeRow> {
        val real = runCatching { homeRows() }.getOrDefault(emptyList())
        if (real.isNotEmpty()) return real
        return coroutineScope {
            HOME_CATEGORIES
                .map { (title, query) ->
                    async(Dispatchers.IO) {
                        val items =
                            runCatching { searchSongs(query) }
                                .getOrDefault(emptyList())
                                .take(12)
                        HomeRow(title, items)
                    }
                }.awaitAll()
                .filter { it.items.isNotEmpty() }
        }
    }

    /** Real YTM home browse: one row per carousel shelf, items capped at 12. */
    private suspend fun homeRows(): List<HomeRow> {
        val raw =
            innerTube
                .browse(client = WEB_REMIX, browseId = "FEmusic_home", setLogin = true)
                .body<JsonObject>()
        val rows = mutableListOf<HomeRow>()
        fun walkShelves(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    el["musicCarouselShelfRenderer"]?.jsonObject?.let { shelf ->
                        val title =
                            shelf["header"]
                                ?.jsonObject
                                ?.get("musicCarouselShelfBasicHeaderRenderer")
                                ?.jsonObject
                                ?.get("title")
                                ?.jsonObject
                                ?.get("runs")
                                ?.jsonArray
                                ?.joinToString("") { run ->
                                    (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                                }?.trim()
                        val items = extractHits(shelf).take(12)
                        if (!title.isNullOrEmpty() && items.isNotEmpty()) {
                            rows += HomeRow(title, items)
                        }
                    }
                    el.values.forEach(::walkShelves)
                }
                is JsonArray -> el.forEach(::walkShelves)
                else -> Unit
            }
        }
        walkShelves(raw)
        return rows
    }

    suspend fun likedSongs(): List<SearchHit> {
        val raw =
            innerTube
                .browse(client = WEB_REMIX, browseId = "FEmusic_liked_videos", setLogin = true)
                .body<JsonObject>()
        return extractHits(raw)
    }

    suspend fun likedPlaylists(): List<PlaylistHit> {
        val raw =
            innerTube
                .browse(client = WEB_REMIX, browseId = "FEmusic_liked_playlists", setLogin = true)
                .body<JsonObject>()
        val hits = LinkedHashMap<String, PlaylistHit>()
        fun walk(el: JsonElement) {
            when (el) {
                is JsonObject -> {
                    el["musicTwoRowItemRenderer"]?.jsonObject?.let { renderer ->
                        parsePlaylistHit(renderer)?.let { hit -> hits.putIfAbsent(hit.id, hit) }
                    }
                    el.values.forEach(::walk)
                }
                is JsonArray -> el.forEach(::walk)
                else -> Unit
            }
        }
        walk(raw)
        return hits.values.toList()
    }

    suspend fun playlistTracks(playlistId: String): List<SearchHit> {
        val raw =
            innerTube
                .browse(client = WEB_REMIX, browseId = "VL$playlistId", setLogin = true)
                .body<JsonObject>()
        return extractHits(raw)
    }

    /** Album tracks via album page browse. Politely avoids SABR/HLS. */
    suspend fun albumTracks(albumId: String): List<SearchHit> {
        val browseId =
            when {
                albumId.startsWith("MPRE") || albumId.startsWith("FE") -> albumId
                else -> "MPREb$albumId"
            }
        val raw =
            innerTube
                .browse(client = WEB_REMIX, browseId = browseId, setLogin = true)
                .body<JsonObject>()
        return extractHits(raw)
    }

    /** Artist songs via artist page browse. Politely avoids SABR/HLS. */
    suspend fun artistTracks(artistId: String): List<SearchHit> {
        val browseId = if (artistId.startsWith("UC")) artistId else "UC$artistId"
        val raw =
            innerTube
                .browse(client = WEB_REMIX, browseId = browseId, setLogin = true)
                .body<JsonObject>()
        return extractHits(raw)
    }

    suspend fun resolveAudioStream(videoId: String, quality: Int = 1): ExtractedStream {
        // VLC plays WebM/Opus and AAC; disable SABR/HLS and prefer non-bounded progressive URLs.
        val hints =
            ContentHints(wantVideo = false).withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = false,
            )
        return extractor.extract(
            videoId = videoId,
            hints = hints,
            audioQuality = audioQualityForSetting(quality),
        ) ?: error("No playable stream for $videoId")
    }

    fun setSessionCookie(cookie: String?) {
        innerTube.cookie = cookie
        innerTube.useLoginForBrowse = !cookie.isNullOrBlank()
    }

    /** Apply InnerTube hl/gl used for browse/search (YouTubeLocale gl, hl). */
    fun setLocale(
        language: String,
        country: String,
    ) {
        val hl = language.ifBlank { "en" }
        val gl = country.ifBlank { "US" }
        innerTube.locale = YouTubeLocale(gl, hl)
    }

    val signedIn: Boolean
        get() = !innerTube.sessionSnapshot().cookie.isNullOrBlank()

    override fun close() {
        runBlocking {
            runCatching { cipherService.dispose() }
        }
        innerTube.close()
        httpClient.close()
    }

    companion object {
        internal fun audioQualityForSetting(setting: Int): AudioQuality =
            when (setting) {
                0 -> AudioQuality.AUTO
                2 -> AudioQuality.LOW
                else -> AudioQuality.HIGH
            }

        private const val FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val FILTER_ALBUM = "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"
        // Artist marker `g` (not album `Y`); BRAKE suffix matches desktop song/album filters.
        // Android/YTM ChAFE form: EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D
        private const val FILTER_ARTIST = "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val FILTER_PLAYLIST = "EgWKAQIAWoKEAkQBRAKEAMQBA%3D%3D"

        private val HOME_CATEGORIES =
            listOf(
                "Quick picks" to "today's top hits",
                "Trending" to "trending music",
                "Chill" to "chill music mix",
                "Focus" to "focus instrumental",
                "Throwbacks" to "throwback hits",
                "Workout" to "workout music",
            "Daily discover" to "daily mix",
            "Keep listening" to "your playlist",
            "Speed dial" to "hot hits",
            "New releases" to "new music",
            "Recently played" to "recently played",
            "Account playlists" to "liked playlists",
            "From the community" to "community picks",
            "Similar recommendation" to "related",
            "Home page" to "home feed",
            "Mood and genres" to "mood magic"
            )

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
                        el["musicTwoRowItemRenderer"]?.jsonObject?.let { renderer ->
                            parseTwoRowRenderer(renderer)?.let { hit ->
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

        /** Album/artist search rows use browseEndpoint ids, not videoIds. */
        internal fun extractBrowseHits(
            root: JsonElement,
            accept: (browseId: String) -> Boolean,
        ): List<SearchHit> {
            val hits = LinkedHashMap<String, SearchHit>()
            fun walk(el: JsonElement) {
                when (el) {
                    is JsonObject -> {
                        el["musicResponsiveListItemRenderer"]?.jsonObject?.let { renderer ->
                            parseBrowseHit(renderer)?.takeIf { accept(it.videoId) }?.let { hit ->
                                hits.putIfAbsent(hit.videoId, hit)
                            }
                        }
                        el["musicTwoRowItemRenderer"]?.jsonObject?.let { renderer ->
                            parseBrowseHit(renderer)?.takeIf { accept(it.videoId) }?.let { hit ->
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

        private fun parseBrowseHit(renderer: JsonObject): SearchHit? {
            val browseId =
                renderer["navigationEndpoint"]
                    ?.jsonObject
                    ?.get("browseEndpoint")
                    ?.jsonObject
                    ?.get("browseId")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: return null
            val flexColumns = renderer["flexColumns"]?.jsonArray.orEmpty()
            val title =
                flexColumns
                    .getOrNull(0)
                    ?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")
                    ?.jsonObject
                    ?.let { firstText(it) }
                    ?: renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                        ?.joinToString("") { run ->
                            (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                        }?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val subtitle =
                flexColumns
                    .getOrNull(1)
                    ?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")
                    ?.jsonObject
                    ?.let { firstText(it) }
                    ?: renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                        ?.joinToString(" ") { run ->
                            (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                        }?.replace("  ", " ")?.trim()
                        ?.takeIf { it.isNotEmpty() }
            val thumbnailUrl =
                renderer["thumbnail"]
                    ?.jsonObject
                    ?.get("musicThumbnailRenderer")
                    ?.jsonObject
                    ?.get("thumbnail")
                    ?.jsonObject
                    ?.get("thumbnails")
                    ?.jsonArray
                    ?.lastOrNull()
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: renderer["thumbnailRenderer"]
                        ?.jsonObject
                        ?.get("musicThumbnailRenderer")
                        ?.jsonObject
                        ?.get("thumbnail")
                        ?.jsonObject
                        ?.get("thumbnails")
                        ?.jsonArray
                        ?.lastOrNull()
                        ?.jsonObject
                        ?.get("url")
                        ?.jsonPrimitive
                        ?.contentOrNull
            return SearchHit(videoId = browseId, title = title, subtitle = subtitle, thumbnailUrl = thumbnailUrl)
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

            val thumbnailUrl =
                renderer["thumbnail"]
                    ?.jsonObject
                    ?.get("musicThumbnailRenderer")
                    ?.jsonObject
                    ?.get("thumbnail")
                    ?.jsonObject
                    ?.get("thumbnails")
                    ?.jsonArray
                    ?.lastOrNull()
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.contentOrNull

            return SearchHit(videoId = videoId, title = title, subtitle = subtitle, thumbnailUrl = thumbnailUrl)
        }

        private fun parsePlaylistHit(renderer: JsonObject): PlaylistHit? {
            val browseId =
                renderer["navigationEndpoint"]
                    ?.jsonObject
                    ?.get("browseEndpoint")
                    ?.jsonObject
                    ?.get("browseId")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: return null
            // Playlist browses use "VL<playlistId>"; skip anything else (albums, artists).
            val playlistId = browseId.removePrefix("VL").takeIf { browseId.startsWith("VL") && it.isNotEmpty() } ?: return null
            val title =
                renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                    ?.joinToString("") { run ->
                        (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                    }?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val subtitle =
                renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                    ?.joinToString(" ") { run ->
                        (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                    }?.replace("  ", " ")?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val thumbnailUrl =
                renderer["thumbnailRenderer"]
                    ?.jsonObject
                    ?.get("musicThumbnailRenderer")
                    ?.jsonObject
                    ?.get("thumbnail")
                    ?.jsonObject
                    ?.get("thumbnails")
                    ?.jsonArray
                    ?.lastOrNull()
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.contentOrNull
            return PlaylistHit(id = playlistId, title = title, subtitle = subtitle, thumbnailUrl = thumbnailUrl)
        }

        private fun parseTwoRowRenderer(renderer: JsonObject): SearchHit? {
            val navigation = renderer["navigationEndpoint"]?.jsonObject
            val videoId =
                navigation?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                    ?: navigation?.get("watchPlaylistEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                    ?: findVideoId(renderer)
                    ?: return null
            val title =
                renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                    ?.joinToString("") { run ->
                        (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                    }?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val subtitle =
                renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                    ?.joinToString(" ") { run ->
                        (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                    }?.replace("  ", " ")?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val thumbnailUrl =
                runCatching {
                    renderer["thumbnailRenderer"]
                        ?.jsonObject
                        ?.get("musicThumbnailRenderer")
                        ?.jsonObject
                        ?.get("thumbnail")
                        ?.jsonObject
                        ?.get("thumbnails")
                        ?.jsonArray
                        ?.lastOrNull()
                        ?.jsonObject
                        ?.get("url")
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()
                    ?: renderer["thumbnail"]
                        ?.jsonObject
                        ?.get("musicThumbnailRenderer")
                        ?.jsonObject
                        ?.get("thumbnail")
                        ?.jsonObject
                        ?.get("thumbnails")
                        ?.jsonArray
                        ?.lastOrNull()
                        ?.jsonObject
                        ?.get("url")
                        ?.jsonPrimitive
                        ?.contentOrNull
            return SearchHit(videoId = videoId, title = title, subtitle = subtitle, thumbnailUrl = thumbnailUrl)
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
