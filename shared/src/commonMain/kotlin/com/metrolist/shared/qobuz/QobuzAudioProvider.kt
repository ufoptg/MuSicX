/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ktor + kotlinx-serialization rewrite of the v13 OkHttp/org.json Qobuz resolver.
 *
 * Contract: [resolve] and [refresh] never throw. Any failure returns null so the
 * playback pipeline falls back to the YouTube backend.
 */

package com.metrolist.shared.qobuz

import com.metrolist.shared.network.bodyAsTextLimited
import com.metrolist.shared.network.createHttpClient
import com.metrolist.shared.utils.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

object QobuzAudioProvider {
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

    const val SQUID_BASE_URL = "https://qobuz.squid.wtf"
    const val JUMO_BASE_URL = "https://jumo-dl.pages.dev"
    const val KENNY_BASE_URL = "https://qobuz.kennyy.com.br"
    const val TRYPT_BASE_URL = "https://trypt-hifi-dl-456461932686.us-west1.run.app"

    private const val STREAM_CACHE_MS = 5 * 60 * 1000L
    private const val REJECT_SCORE = -1_000_000
    private const val MIN_REASONABLE_BITRATE = 32_000
    private const val MAX_REASONABLE_BITRATE = 20_000_000
    private const val DEFAULT_LOSSLESS_CHANNELS = 2
    private const val FLAC_ESTIMATED_COMPRESSION_RATIO = 0.6
    private const val CAPTCHA_COOLDOWN_MS = 5 * 60 * 1000L

    private const val SEARCH_TIMEOUT_MS = 25_000L
    private const val STREAM_TIMEOUT_MS = 25_000L
    private const val CONTENT_LENGTH_TIMEOUT_MS = 10_000L
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val TAG = "QobuzAudioProvider"

    private val JUMO_SUPPORTED_REGIONS = setOf("FR", "NL", "NZ", "JP")

    enum class ResolverBackend {
        MONOKENNY,
        JUMO,
        SQUID,
        TRYPT,
    }

    enum class QobuzAudioQuality {
        AAC_320,
        CD_QUALITY,
        HI_RES_LOSSLESS,
    }

    private enum class SearchBackend {
        KENNY,
        SQUID,
        TRYPT,
    }

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val countryCode: String,
        val backend: ResolverBackend,
        val qualityCode: Int = 27,
    )

    data class Resolved(
        val mediaUri: String,
        val trackId: String,
        val label: String,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val expiresAtMs: Long,
        val hires: Boolean = false,
        val bitDepth: Int? = null,
        val samplingRateKhz: Double? = null,
        val isrc: String? = null,
    )

    class QobuzResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private data class MatchedTrack(
        val trackId: String,
        val hires: Boolean,
        val bitDepth: Int?,
        val samplingRateKhz: Double?,
        val isrc: String? = null,
    )

    private data class StreamAttempt(
        val resolved: Resolved? = null,
        val error: String? = null,
    )

    private data class TrackSearchResult(
        val items: JsonArray? = null,
        val error: String? = null,
    )

    /**
     * Lightweight metadata returned to the manual-match UI so the user can
     * pick the right Qobuz track when the auto-matcher is wrong.
     */
    data class CandidateMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val hires: Boolean,
        val bitDepth: Int?,
        val samplingRateKhz: Double?,
    )

    private val defaultHttpClient: HttpClient by lazy { createHttpClient() }
    internal var clientProvider: () -> HttpClient = { defaultHttpClient }
    private val httpClient: HttpClient get() = clientProvider()

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val cacheMutex = Mutex()
    private val trackCache = mutableMapOf<String, MatchedTrack>()
    private val streamCache = mutableMapOf<String, Resolved>()
    // Backend cooldown after captcha/403. Skip the backend until the timestamp.
    private val backendCooldownUntilMs = mutableMapOf<ResolverBackend, Long>()

    internal var nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    fun qualityCodeFor(audioQuality: QobuzAudioQuality): Int =
        when (audioQuality) {
            QobuzAudioQuality.AAC_320 -> 5
            QobuzAudioQuality.CD_QUALITY -> 6
            QobuzAudioQuality.HI_RES_LOSSLESS -> 27
        }

    fun normalizeResolverRegion(
        countryCode: String,
        backend: ResolverBackend,
    ): String {
        val normalized = countryCode.trim().uppercase()
        return when (backend) {
            ResolverBackend.JUMO -> normalized.takeIf { it in JUMO_SUPPORTED_REGIONS } ?: "FR"
            ResolverBackend.SQUID -> normalized.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US"
            ResolverBackend.MONOKENNY -> normalized.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US"
            ResolverBackend.TRYPT -> normalized.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US"
        }
    }

    /**
     * Resolves a playback resource for [query]. Never throws: on any failure the
     * caller falls back to the YouTube backend and this returns null.
     */
    suspend fun resolve(query: Query): Resolved? {
        return try {
            resolveInternal(query)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.w(
                TAG,
                "Qobuz resolution failed for ${query.title}, falling back to YouTube: ${error.message ?: error::class.simpleName}",
                error,
            )
            null
        }
    }

    /**
     * Re-resolves [query], bypassing the cached stream and track match so a
     * previously expired or failed resolution gets a fresh attempt.
     * Never throws (see [resolve]).
     */
    suspend fun refresh(query: Query): Resolved? {
        return try {
            invalidate(query.mediaId)
            cacheMutex.withLock { trackCache.remove(query.trackCacheKey()) }
            resolveInternal(query)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.w(
                TAG,
                "Qobuz refresh failed for ${query.title}, falling back to YouTube: ${error.message ?: error::class.simpleName}",
                error,
            )
            null
        }
    }

    suspend fun invalidate(mediaId: String) {
        cacheMutex.withLock {
            streamCache.keys
                .filter { it.startsWith("$mediaId::") }
                .forEach { streamCache.remove(it) }
        }
    }

    suspend fun primeKnownTrack(
        query: Query,
        trackId: String,
        hires: Boolean,
        bitDepth: Int?,
        samplingRateKhz: Double?,
        isrc: String?,
    ) {
        cacheMutex.withLock {
            trackCache[query.trackCacheKey()] =
                MatchedTrack(
                    trackId = trackId,
                    hires = hires,
                    bitDepth = bitDepth,
                    samplingRateKhz = samplingRateKhz,
                    isrc = isrc?.takeIf { it.isNotBlank() },
                )
        }
    }

    private suspend fun resolveInternal(query: Query): Resolved? {
        val now = nowMs()
        val directTrackId = query.mediaId.toQobuzTrackIdOrNull()
        val trackCacheKey = query.trackCacheKey()

        val track =
            directTrackId?.let { trackId ->
                MatchedTrack(
                    trackId = trackId,
                    hires = false,
                    bitDepth = null,
                    samplingRateKhz = null,
                    isrc = query.isrc?.takeIf { it.isNotBlank() },
                )
            }
                ?: cacheMutex.withLock { trackCache[trackCacheKey] }
                ?: findBestTrack(query)?.also { matched ->
                    cacheMutex.withLock { trackCache[trackCacheKey] = matched }
                }
                ?: throw QobuzResolutionException("Qobuz match not found for ${query.title}")

        var lastError: String? = null
        for (backend in crossBackendOrder(query.backend)) {
            val cooldownUntil = backendCooldownUntil(backend)
            if (cooldownUntil > now) {
                Logger.d(
                    TAG,
                    "Qobuz backend ${backend.name} in cooldown for ${(cooldownUntil - now) / 1000}s after captcha",
                )
                continue
            }

            val resolverRegion = normalizeResolverRegion(query.countryCode, backend)
            val streamCacheKey = query.cacheKey(resolverRegion)
            val cached = cacheMutex.withLock { streamCache[streamCacheKey] }
            if (cached != null && cached.expiresAtMs > now + 20_000L) {
                return cached
            }

            for (quality in buildQualityFallbackOrder(query.qualityCode)) {
                val attempt = requestStream(backend, track, quality, resolverRegion, query)
                attempt.resolved?.let { resolved ->
                    cacheMutex.withLock { streamCache[streamCacheKey] = resolved }
                    return resolved
                }
                val error = attempt.error
                if (!error.isNullOrBlank()) {
                    lastError = error
                    Logger.d(
                        TAG,
                        "Stream attempt failed for ${query.title} (${backend.name}, region=$resolverRegion, quality=$quality): $error",
                    )
                    if (error.contains("captcha", ignoreCase = true)) {
                        setBackendCooldown(backend, nowMs() + CAPTCHA_COOLDOWN_MS)
                        Logger.w(
                            TAG,
                            "Backend ${backend.name} captcha-blocked, cooldown for ${CAPTCHA_COOLDOWN_MS / 60_000} minutes",
                        )
                        break
                    }
                    if (error.contains("preview", ignoreCase = true)) {
                        // Preview = track not in catalog at this quality tier.
                        // Cascading to lower qualities almost always hits the same
                        // preview wall — short-circuit to save time per failure.
                        Logger.d(TAG, "Preview detected at quality $quality for ${query.title}, aborting ladder")
                        break
                    }
                }
            }
        }

        throw QobuzResolutionException(lastError ?: "Qobuz stream not found for ${query.title}")
    }

    private fun crossBackendOrder(preferred: ResolverBackend): List<ResolverBackend> =
        listOf(preferred) + ResolverBackend.entries.filterNot { it == preferred }

    private suspend fun requestStream(
        backend: ResolverBackend,
        track: MatchedTrack,
        qualityCode: Int,
        resolverRegion: String,
        query: Query,
    ): StreamAttempt =
        when (backend) {
            ResolverBackend.JUMO -> requestJumoStream(track, qualityCode, resolverRegion, query.durationMs)
            ResolverBackend.SQUID -> requestSquidStream(track, query.countryCode, qualityCode, query.durationMs)
            ResolverBackend.MONOKENNY -> requestMonoStream(track, qualityCode, resolverRegion, query.durationMs)
            ResolverBackend.TRYPT -> requestTrypTStream(track, qualityCode, resolverRegion, query.durationMs)
        }

    private suspend fun backendCooldownUntil(backend: ResolverBackend): Long =
        cacheMutex.withLock { backendCooldownUntilMs[backend] ?: 0L }

    private suspend fun setBackendCooldown(
        backend: ResolverBackend,
        untilMs: Long,
    ) = cacheMutex.withLock { backendCooldownUntilMs[backend] = untilMs }

    private suspend fun findBestTrack(query: Query): MatchedTrack? {
        val resolverRegion = normalizeResolverRegion(query.countryCode, query.backend)
        val searchRegion =
            when (query.backend) {
                ResolverBackend.JUMO -> resolverRegion
                ResolverBackend.SQUID -> query.countryCode
                ResolverBackend.MONOKENNY -> resolverRegion
                ResolverBackend.TRYPT -> resolverRegion
            }

        var lastSearchError: String? = null
        for (term in searchTerms(query)) {
            for (backend in searchBackendOrder(query.backend)) {
                val search =
                    when (backend) {
                        SearchBackend.KENNY -> searchTracks(term, query.countryCode, SearchBackend.KENNY)
                        SearchBackend.SQUID ->
                            when (query.backend) {
                                ResolverBackend.MONOKENNY -> searchTracks(term, searchRegion, SearchBackend.SQUID)
                                ResolverBackend.JUMO -> searchTracksJumo(term, searchRegion)
                                ResolverBackend.SQUID -> searchTracks(term, searchRegion, SearchBackend.SQUID)
                                ResolverBackend.TRYPT -> searchTracks(term, searchRegion, SearchBackend.SQUID)
                            }
                        SearchBackend.TRYPT -> searchTracks(term, searchRegion, SearchBackend.TRYPT)
                    }
                if (search.error != null) {
                    lastSearchError = search.error
                }
                val results = search.items ?: continue
                selectBestTrack(results, query)?.let { return it }
            }
        }
        if (lastSearchError != null) {
            Logger.d(TAG, "Qobuz search failed for ${query.title}: $lastSearchError")
        }
        return null
    }

    private fun searchBackendOrder(preferred: ResolverBackend): List<SearchBackend> {
        // JUMO has no search endpoint — when JUMO is the preferred stream
        // backend we lead search with TRYPT instead. Other backends lead with
        // their own search and fall through.
        return when (preferred) {
            ResolverBackend.MONOKENNY -> listOf(SearchBackend.KENNY, SearchBackend.TRYPT, SearchBackend.SQUID)
            ResolverBackend.JUMO -> listOf(SearchBackend.TRYPT, SearchBackend.KENNY, SearchBackend.SQUID)
            ResolverBackend.SQUID -> listOf(SearchBackend.SQUID, SearchBackend.TRYPT, SearchBackend.KENNY)
            ResolverBackend.TRYPT -> listOf(SearchBackend.TRYPT, SearchBackend.KENNY, SearchBackend.SQUID)
        }
    }

    private suspend fun searchTracks(
        term: String,
        countryCode: String,
        backend: SearchBackend,
    ): TrackSearchResult {
        val baseUrl =
            when (backend) {
                SearchBackend.KENNY -> KENNY_BASE_URL
                SearchBackend.SQUID -> SQUID_BASE_URL
                SearchBackend.TRYPT -> TRYPT_BASE_URL
            }
        val url =
            URLBuilder("$baseUrl/api/get-music").apply {
                parameters.append("q", term)
                parameters.append("offset", "0")
            }.buildString()

        return try {
            val response =
                withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                    httpClient.get(url) {
                        header("Accept", "application/json")
                        header("Referer", "$baseUrl/")
                        header("User-Agent", "Mozilla/5.0")
                        if (backend == SearchBackend.SQUID || backend == SearchBackend.TRYPT) {
                            header("Token-Country", countryCode)
                        }
                    }
                } ?: return TrackSearchResult(error = "${backend.name} search timed out")
            val payload = response.bodyAsTextLimited(MAX_RESPONSE_BYTES)
            if (!response.status.isSuccess()) {
                return TrackSearchResult(error = "${backend.name} search HTTP ${response.status.value}: ${payload?.take(160).orEmpty()}")
            }
            if (payload.isNullOrBlank()) {
                return TrackSearchResult(error = "${backend.name} search returned an empty response")
            }
            val root =
                json.parseToJsonElement(payload).jsonObjectOrNull()
                    ?: return TrackSearchResult(error = "${backend.name} search returned a malformed response")
            if (!root.booleanOrDefault("success")) {
                return TrackSearchResult(error = "${backend.name} search rejected request: ${root.stringOrNull("error") ?: "unknown error"}")
            }
            TrackSearchResult(items = root.obj("data")?.obj("tracks")?.arr("items"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TrackSearchResult(error = "${backend.name} search request failed: ${error.message ?: error::class.simpleName}")
        }
    }

    private suspend fun searchTracksJumo(
        term: String,
        region: String,
    ): TrackSearchResult {
        val url =
            URLBuilder("$JUMO_BASE_URL/search").apply {
                parameters.append("query", term)
                parameters.append("offset", "0")
                parameters.append("limit", "30")
                parameters.append("region", region)
            }.buildString()

        return try {
            val response =
                withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                    httpClient.get(url) {
                        header("Accept", "application/json")
                        header("User-Agent", BROWSER_USER_AGENT)
                    }
                } ?: return TrackSearchResult(error = "JUMO search timed out")
            val payload = response.bodyAsTextLimited(MAX_RESPONSE_BYTES)
            if (!response.status.isSuccess()) {
                return TrackSearchResult(error = "JUMO search HTTP ${response.status.value}: ${payload?.take(160).orEmpty()}")
            }
            if (payload.isNullOrBlank()) {
                return TrackSearchResult(error = "JUMO search returned an empty response")
            }
            val root =
                json.parseToJsonElement(payload).jsonObjectOrNull()
                    ?: return TrackSearchResult(error = "JUMO search returned a malformed response")
            if (!root.booleanOrDefault("success")) {
                return TrackSearchResult(error = "JUMO search rejected request: ${root.stringOrNull("error") ?: "unknown error"}")
            }
            TrackSearchResult(items = root.obj("data")?.obj("tracks")?.arr("items"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TrackSearchResult(error = "JUMO search request failed: ${error.message ?: error::class.simpleName}")
        }
    }

    private fun selectBestTrack(
        results: JsonArray,
        query: Query,
    ): MatchedTrack? {
        val wantedTitle = query.title.normalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = query.isrc?.trim()?.uppercase().orEmpty()
        val wantedDescriptorText = listOf(wantedTitle, wantedAlbum).joinToString(" ")
        val wantedDurationSec = query.durationMs?.let { (it / 1000L).toInt() }
        val wantedTitleTokens = significantTokens(wantedTitle)

        data class Candidate(
            val track: MatchedTrack,
            val score: Int,
        )

        data class EvaluatedCandidate(
            val trackId: String,
            val title: String,
            val artists: String,
            val score: Int,
            val downloadable: Boolean,
        )

        val candidates = mutableListOf<Candidate>()
        val evaluated = mutableListOf<EvaluatedCandidate>()
        val minAcceptScore = if (query.backend == ResolverBackend.JUMO) 300 else 330
        results.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val downloadable = obj.booleanOrDefault("downloadable")
            val streamable = obj.booleanOrDefault("streamable")
            if (query.backend == ResolverBackend.SQUID && !downloadable && !streamable) return@forEach

            val trackId = obj.stringOrNull("id") ?: return@forEach
            val candidateTitle = obj.stringOrNull("title").normalized()
            val candidateVersion = obj.stringOrNull("version").normalized()
            val candidateCombinedTitle = listOf(candidateTitle, candidateVersion)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val candidateAlbum = obj.obj("album")?.stringOrNull("title").normalized()
            val candidateIsrc = obj.stringOrNull("isrc")?.trim()?.uppercase().orEmpty()
            val candidateDuration = obj.intOrNull("duration")
            val candidateArtists = collectArtistNames(obj).map { it.normalized() }.filter { it.isNotBlank() }
            val hires = obj.booleanOrDefault("hires")
            val bitDepth = obj.intOrNull("maximum_bit_depth")
            val samplingRate = obj.doubleOrNull("maximum_sampling_rate")
            val candidateTitleTokens = significantTokens(candidateCombinedTitle)
            val matchedTitleTokens = wantedTitleTokens.count(candidateTitleTokens::contains)
            val titleRecall = if (wantedTitleTokens.isEmpty()) 1.0 else matchedTitleTokens.toDouble() / wantedTitleTokens.size
            val titlePrecision = if (candidateTitleTokens.isEmpty()) 0.0 else matchedTitleTokens.toDouble() / candidateTitleTokens.size
            val exactArtistMatches = wantedArtists.count { wanted -> candidateArtists.any { it == wanted } }
            val partialArtistMatches = wantedArtists.count { wanted ->
                candidateArtists.any { candidate -> artistNamesMatch(wanted, candidate) }
            }

            val versionPenalty = versionMismatchPenalty(wantedDescriptorText, candidateCombinedTitle)
            if (versionPenalty <= REJECT_SCORE) return@forEach
            if (!titleTokensPass(
                    wantedTitle = wantedTitle,
                    candidateTitle = candidateCombinedTitle,
                    wantedTokens = wantedTitleTokens,
                    matchedTokenCount = matchedTitleTokens,
                    recall = titleRecall,
                    precision = titlePrecision,
                )
            ) {
                return@forEach
            }
            if (wantedArtists.isNotEmpty() && exactArtistMatches == 0 && partialArtistMatches == 0) return@forEach
            if (wantedDurationSec != null && candidateDuration != null && abs(wantedDurationSec - candidateDuration) > 25) return@forEach

            var score = 0
            score += versionPenalty

            if (wantedIsrc.isNotBlank() && candidateIsrc == wantedIsrc) {
                score += 1000
            }

            if (wantedTitle.isNotBlank()) {
                score += when {
                    candidateTitle == wantedTitle -> 360
                    candidateCombinedTitle == wantedTitle -> 340
                    candidateCombinedTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> 180
                    wantedTitle.wordsOverlap(candidateCombinedTitle) >= 3 -> 110
                    else -> -120
                }
            }

            if (wantedTitleTokens.isNotEmpty()) {
                when {
                    matchedTitleTokens == wantedTitleTokens.size -> score += 180
                    matchedTitleTokens >= wantedTitleTokens.size.coerceAtLeast(1) - 1 -> score += 70
                    matchedTitleTokens >= wantedTitleTokens.size.coerceAtLeast(3) - 2 -> score -= 20
                    else -> score -= 180
                }
            }

            if (wantedAlbum.isNotBlank() && candidateAlbum.isNotBlank()) {
                score += when {
                    candidateAlbum == wantedAlbum -> 160
                    candidateAlbum.contains(wantedAlbum) || wantedAlbum.contains(candidateAlbum) -> 60
                    wantedAlbum.wordsOverlap(candidateAlbum) >= 2 -> 35
                    else -> -50
                }
            }

            if (wantedArtists.isNotEmpty()) {
                score += when {
                    exactArtistMatches > 0 -> 260 + ((exactArtistMatches - 1) * 55)
                    partialArtistMatches > 0 -> 120 + ((partialArtistMatches - 1) * 40)
                    else -> REJECT_SCORE
                }
            }

            if (wantedDurationSec != null && candidateDuration != null) {
                val diff = abs(wantedDurationSec - candidateDuration)
                score += when {
                    diff <= 2 -> 180
                    diff <= 5 -> 120
                    diff <= 10 -> 50
                    diff <= 15 -> 10
                    else -> -90
                }
            }

            if (hires) score += 15
            if (!downloadable && !streamable) score -= 25

            evaluated += EvaluatedCandidate(
                trackId = trackId,
                title = candidateCombinedTitle,
                artists = candidateArtists.joinToString(),
                score = score,
                downloadable = downloadable || streamable,
            )

            if (score > REJECT_SCORE && (score >= minAcceptScore || wantedIsrc.isNotBlank() && candidateIsrc == wantedIsrc)) {
                candidates += Candidate(
                    track = MatchedTrack(
                        trackId = trackId,
                        hires = hires,
                        bitDepth = bitDepth,
                        samplingRateKhz = samplingRate,
                        isrc = candidateIsrc.takeIf { it.isNotBlank() },
                    ),
                    score = score,
                )
            }
        }

        if (candidates.isEmpty()) {
            val top =
                evaluated
                    .sortedByDescending { it.score }
                    .take(3)
                    .joinToString(" | ") {
                        "${it.trackId} score=${it.score} downloadable=${it.downloadable} title='${it.title}' artists='${it.artists}'"
                    }
            if (top.isNotBlank()) {
                Logger.w(
                    TAG,
                    "No acceptable Qobuz candidate for '${query.title}' by '${query.artists.joinToString()}' (backend=${query.backend.name}, country=${query.countryCode}, min=$minAcceptScore). Top matches: $top",
                )
            }
        }

        return candidates.maxByOrNull { it.score }?.track
    }

    private suspend fun requestSquidStream(
        track: MatchedTrack,
        countryCode: String,
        qualityCode: Int,
        durationMs: Long?,
    ): StreamAttempt {
        val url =
            URLBuilder("$SQUID_BASE_URL/api/download-music").apply {
                parameters.append("track_id", track.trackId)
                parameters.append("quality", qualityCode.toString())
            }.buildString()

        return try {
            val response =
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    httpClient.get(url) {
                        header("Accept", "application/json")
                        header("Accept-Language", "en-US,en;q=0.9")
                        header("Token-Country", countryCode)
                        header("Origin", SQUID_BASE_URL)
                        header("Referer", "$SQUID_BASE_URL/")
                        header("User-Agent", BROWSER_USER_AGENT)
                    }
                } ?: return StreamAttempt(error = "Qobuz request timed out")
            val payload = response.bodyAsTextLimited(MAX_RESPONSE_BYTES)
            if (!response.status.isSuccess()) {
                return StreamAttempt(error = "Qobuz HTTP ${response.status.value}: ${payload?.take(160).orEmpty()}")
            }
            if (payload.isNullOrBlank()) {
                return StreamAttempt(error = "Qobuz returned an empty response")
            }
            val root =
                json.parseToJsonElement(payload).jsonObjectOrNull()
                    ?: return StreamAttempt(error = "Qobuz returned a malformed response")
            if (!root.booleanOrDefault("success")) {
                val apiError = root.stringOrNull("error")
                val message =
                    if (apiError.equals("Captcha required.", ignoreCase = true)) {
                        "Qobuz download API requires a browser captcha right now"
                    } else {
                        "Qobuz rejected quality $qualityCode: ${apiError ?: "unknown error"}"
                    }
                return StreamAttempt(error = message)
            }

            val data = root.obj("data")
            val streamUrl =
                data?.stringOrNull("url")
                    ?: return StreamAttempt(error = "Qobuz did not return a stream URL for quality $qualityCode")
            val bitDepth = data.intOrNull("bit_depth") ?: track.bitDepth
            val samplingRate = data.doubleOrNull("sampling_rate") ?: track.samplingRateKhz
            val lossyBitrate =
                data.intOrNull("bitrate")
                    ?: data.intOrNull("bit_rate")
                    ?: root.intOrNull("bitrate")
                    ?: root.intOrNull("bit_rate")
            val losslessBitrate =
                estimateStreamBitrateFromContentLength(streamUrl, durationMs)
                    ?: normalizeBitrate(
                        data.intOrNull("average_bitrate")
                            ?: root.intOrNull("average_bitrate"),
                    ).takeIf { it > 0 }
            val format =
                formatFrom(
                    mimeType = if (qualityCode == 5) "audio/mpeg" else "audio/flac",
                    bitDepth = bitDepth,
                    samplingRateKhz = samplingRate,
                    bitrate = lossyBitrate,
                    losslessBitrate = losslessBitrate,
                    hires = track.hires || (bitDepth ?: 0) > 16 || (samplingRate ?: 0.0) > 44.1 || qualityCode >= 7,
                )
            StreamAttempt(resolved = format.toResolved(streamUrl, track.trackId, track.isrc))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            StreamAttempt(error = "Qobuz request failed: ${error.message ?: error::class.simpleName}")
        }
    }

    private suspend fun requestJumoStream(
        track: MatchedTrack,
        qualityCode: Int,
        region: String,
        durationMs: Long?,
    ): StreamAttempt {
        val url =
            URLBuilder("$JUMO_BASE_URL/fetch").apply {
                parameters.append("track_id", track.trackId)
                parameters.append("format_id", qualityCode.toString())
                parameters.append("region", region)
            }.buildString()

        return try {
            val response =
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    httpClient.get(url) {
                        header("Accept", "application/json,text/plain,*/*")
                        header("Accept-Language", "en-US,en;q=0.9")
                        header("Origin", JUMO_BASE_URL)
                        header("Referer", "$JUMO_BASE_URL/")
                        header("User-Agent", BROWSER_USER_AGENT)
                    }
                } ?: return StreamAttempt(error = "JUMO request timed out")
            val payload = response.bodyAsTextLimited(MAX_RESPONSE_BYTES)
            if (!response.status.isSuccess()) {
                return StreamAttempt(error = "JUMO HTTP ${response.status.value}: ${payload?.take(160).orEmpty()}")
            }
            if (payload.isNullOrBlank()) {
                return StreamAttempt(error = "JUMO returned an empty response")
            }
            val root =
                json.parseToJsonElement(payload).jsonObjectOrNull()
                    ?: return StreamAttempt(error = "JUMO returned a malformed response")
            root.stringOrNull("error")?.takeIf { it.isNotBlank() }?.let { apiError ->
                return StreamAttempt(error = "JUMO rejected quality $qualityCode: $apiError")
            }
            if (root.booleanOrDefault("previewDetected")) {
                return StreamAttempt(error = "JUMO returned a preview instead of a full Qobuz stream")
            }

            val streamUrl =
                root.stringOrNull("directUrl")
                    ?: root.stringOrNull("url")
                    ?: return StreamAttempt(error = "JUMO did not return a stream URL for quality $qualityCode")
            val bitDepth = root.intOrNull("bit_depth") ?: track.bitDepth
            val samplingRate = root.doubleOrNull("sampling_rate") ?: track.samplingRateKhz
            val mimeType =
                root.stringOrNull("mime_type")
                    ?: if (qualityCode == 5) "audio/mpeg" else "audio/flac"
            val lossyBitrate = root.intOrNull("bitrate") ?: root.intOrNull("bit_rate")
            val losslessBitrate =
                estimateStreamBitrateFromContentLength(streamUrl, durationMs)
                    ?: normalizeBitrate(root.intOrNull("average_bitrate")).takeIf { it > 0 }
            val hires = (bitDepth ?: 0) > 16 || (samplingRate ?: 0.0) > 44.1 || qualityCode >= 7
            val format =
                formatFrom(
                    mimeType = mimeType,
                    bitDepth = bitDepth,
                    samplingRateKhz = samplingRate,
                    bitrate = lossyBitrate,
                    losslessBitrate = losslessBitrate,
                    hires = hires,
                )
            StreamAttempt(
                resolved =
                    format.toResolved(
                        url = streamUrl,
                        trackId = root.stringOrNull("resolvedTrackId") ?: track.trackId,
                        isrc = track.isrc,
                    ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            StreamAttempt(error = "JUMO request failed: ${error.message ?: error::class.simpleName}")
        }
    }

    private suspend fun requestMonoStream(
        track: MatchedTrack,
        qualityCode: Int,
        region: String,
        durationMs: Long?,
    ): StreamAttempt {
        val url =
            URLBuilder("$KENNY_BASE_URL/api/download-music").apply {
                parameters.append("track_id", track.trackId)
                parameters.append("quality", qualityCode.toString())
            }.buildString()

        return try {
            val response =
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    httpClient.get(url) {
                        header("Accept", "application/json")
                        header("Accept-Language", "en-US,en;q=0.9")
                        header("Origin", KENNY_BASE_URL)
                        header("Referer", "$KENNY_BASE_URL/")
                        header("User-Agent", BROWSER_USER_AGENT)
                    }
                } ?: return StreamAttempt(error = "Monokenny request timed out")
            val payload = response.bodyAsTextLimited(MAX_RESPONSE_BYTES)
            if (!response.status.isSuccess()) {
                return StreamAttempt(error = "Monokenny HTTP ${response.status.value}: ${payload?.take(160).orEmpty()}")
            }
            if (payload.isNullOrBlank()) {
                return StreamAttempt(error = "Monokenny returned an empty response")
            }
            val root =
                json.parseToJsonElement(payload).jsonObjectOrNull()
                    ?: return StreamAttempt(error = "Monokenny returned a malformed response")
            if (!root.booleanOrDefault("success")) {
                val apiError = root.stringOrNull("error")
                val message =
                    if (apiError.equals("Captcha required.", ignoreCase = true)) {
                        "Monokenny download API requires a browser captcha right now"
                    } else {
                        "Monokenny rejected quality $qualityCode: ${apiError ?: "unknown error"}"
                    }
                return StreamAttempt(error = message)
            }

            val data = root.obj("data")
            val streamUrl =
                data?.stringOrNull("url")
                    ?: return StreamAttempt(error = "Monokenny did not return a stream URL for quality $qualityCode")
            val bitDepth = data.intOrNull("bit_depth") ?: track.bitDepth
            val samplingRate = data.doubleOrNull("sampling_rate") ?: track.samplingRateKhz
            val lossyBitrate =
                data.intOrNull("bitrate")
                    ?: data.intOrNull("bit_rate")
                    ?: root.intOrNull("bitrate")
                    ?: root.intOrNull("bit_rate")
            val losslessBitrate =
                estimateStreamBitrateFromContentLength(streamUrl, durationMs)
                    ?: normalizeBitrate(
                        data.intOrNull("average_bitrate")
                            ?: root.intOrNull("average_bitrate"),
                    ).takeIf { it > 0 }
            val format =
                formatFrom(
                    mimeType = if (qualityCode == 5) "audio/mpeg" else "audio/flac",
                    bitDepth = bitDepth,
                    samplingRateKhz = samplingRate,
                    bitrate = lossyBitrate,
                    losslessBitrate = losslessBitrate,
                    hires = track.hires || (bitDepth ?: 0) > 16 || (samplingRate ?: 0.0) > 44.1 || qualityCode >= 7,
                )
            StreamAttempt(resolved = format.toResolved(streamUrl, track.trackId, track.isrc))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            StreamAttempt(error = "Monokenny request failed: ${error.message ?: error::class.simpleName}")
        }
    }

    private suspend fun requestTrypTStream(
        track: MatchedTrack,
        qualityCode: Int,
        region: String,
        durationMs: Long?,
    ): StreamAttempt {
        val url =
            URLBuilder("$TRYPT_BASE_URL/api/download-music").apply {
                parameters.append("track_id", track.trackId)
                parameters.append("quality", qualityCode.toString())
            }.buildString()

        return try {
            val response =
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    httpClient.get(url) {
                        header("Accept", "application/json,text/plain,*/*")
                        header("Accept-Language", "en-US,en;q=0.9")
                        header("Token-Country", region)
                        header("Origin", TRYPT_BASE_URL)
                        header("Referer", "$TRYPT_BASE_URL/")
                        header("User-Agent", BROWSER_USER_AGENT)
                    }
                } ?: return StreamAttempt(error = "TrypT request timed out")
            val payload = response.bodyAsTextLimited(MAX_RESPONSE_BYTES)
            if (!response.status.isSuccess()) {
                return StreamAttempt(error = "TrypT HTTP ${response.status.value}: ${payload?.take(160).orEmpty()}")
            }
            if (payload.isNullOrBlank()) {
                return StreamAttempt(error = "TrypT returned an empty response")
            }
            val root =
                json.parseToJsonElement(payload).jsonObjectOrNull()
                    ?: return StreamAttempt(error = "TrypT returned a malformed response")
            if (!root.booleanOrDefault("success", default = true)) {
                val apiError = root.stringOrNull("error") ?: root.stringOrNull("message")
                val message =
                    if (apiError != null && apiError.contains("captcha", ignoreCase = true)) {
                        "TrypT download API requires a browser captcha right now"
                    } else {
                        "TrypT rejected quality $qualityCode: ${apiError ?: "unknown error"}"
                    }
                return StreamAttempt(error = message)
            }

            val data = root.obj("data")
            val streamUrl =
                data?.stringOrNull("url")
                    ?: root.stringOrNull("url")
                    ?: return StreamAttempt(error = "TrypT did not return a stream URL for quality $qualityCode")
            val bitDepth =
                data?.intOrNull("bit_depth")
                    ?: root.intOrNull("bit_depth")
                    ?: track.bitDepth
            val samplingRate =
                data?.doubleOrNull("sampling_rate")
                    ?: root.doubleOrNull("sampling_rate")
                    ?: track.samplingRateKhz
            val mimeType =
                data?.stringOrNull("mime_type")
                    ?: root.stringOrNull("mime_type")
                    ?: if (qualityCode == 5) "audio/mpeg" else "audio/flac"
            val lossyBitrate =
                data?.intOrNull("bitrate")
                    ?: data?.intOrNull("bit_rate")
                    ?: root.intOrNull("bitrate")
                    ?: root.intOrNull("bit_rate")
            val losslessBitrate =
                estimateStreamBitrateFromContentLength(streamUrl, durationMs)
                    ?: normalizeBitrate(
                        data?.intOrNull("average_bitrate")
                            ?: root.intOrNull("average_bitrate"),
                    ).takeIf { it > 0 }
            val format =
                formatFrom(
                    mimeType = mimeType,
                    bitDepth = bitDepth,
                    samplingRateKhz = samplingRate,
                    bitrate = lossyBitrate,
                    losslessBitrate = losslessBitrate,
                    hires = track.hires || (bitDepth ?: 0) > 16 || (samplingRate ?: 0.0) > 44.1 || qualityCode >= 7,
                )
            StreamAttempt(resolved = format.toResolved(streamUrl, track.trackId, track.isrc))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            StreamAttempt(error = "TrypT request failed: ${error.message ?: error::class.simpleName}")
        }
    }

    private data class StreamFormat(
        val label: String,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val hires: Boolean = false,
        val bitDepth: Int? = null,
        val samplingRateKhz: Double? = null,
    ) {
        fun toResolved(
            url: String,
            trackId: String,
            isrc: String? = null,
        ) =
            Resolved(
            mediaUri = url,
            trackId = trackId,
            label = label,
            mimeType = mimeType,
            codecs = codecs,
            bitrate = bitrate,
            sampleRate = sampleRate,
            expiresAtMs = extractExpiryMs(url),
            hires = hires,
            bitDepth = bitDepth,
            samplingRateKhz = samplingRateKhz,
            isrc = isrc,
        )
    }

    private fun formatFrom(
        mimeType: String,
        bitDepth: Int?,
        samplingRateKhz: Double?,
        bitrate: Int?,
        losslessBitrate: Int?,
        hires: Boolean,
    ): StreamFormat {
        val lowerMime = mimeType.lowercase()
        val sampleRate =
            samplingRateKhz
                ?.takeIf { it > 0.0 }
                ?.let { (it * 1000.0).roundToInt() }
        val normalizedBitrate = normalizeBitrate(bitrate)

        return when {
            lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> StreamFormat(
                label = "Qobuz MP3",
                mimeType = "audio/mpeg",
                codecs = "mp3",
                bitrate = normalizedBitrate.takeIf { it > 0 } ?: 320_000,
                sampleRate = sampleRate,
                hires = hires,
                bitDepth = bitDepth,
                samplingRateKhz = samplingRateKhz,
            )

            lowerMime.contains("aac") || lowerMime.contains("mp4") -> StreamFormat(
                label = "Qobuz AAC",
                mimeType = "audio/mp4",
                codecs = "mp4a.40.2",
                bitrate = normalizedBitrate.takeIf { it > 0 } ?: 320_000,
                sampleRate = sampleRate,
                hires = hires,
                bitDepth = bitDepth,
                samplingRateKhz = samplingRateKhz,
            )

            else -> {
                StreamFormat(
                    label = buildFlacLabel(bitDepth, samplingRateKhz, hires),
                    mimeType = "audio/flac",
                    codecs = "flac",
                    bitrate =
                        losslessBitrate
                            ?.takeIf { it > 0 }
                            ?: estimateCompressedLosslessBitrate(bitDepth, samplingRateKhz, hires),
                    sampleRate = sampleRate,
                    hires = hires,
                    bitDepth = bitDepth,
                    samplingRateKhz = samplingRateKhz,
                )
            }
        }
    }

    private fun normalizeBitrate(value: Int?): Int {
        return value
            ?.takeIf { it > 0 }
            ?.let { if (it in 1..9999) it * 1000 else it }
            ?: 0
    }

    private suspend fun estimateStreamBitrateFromContentLength(
        url: String,
        durationMs: Long?,
    ): Int? {
        val safeDuration = durationMs?.takeIf { it > 0L } ?: return null
        val length = fetchStreamContentLength(url) ?: return null
        val bitrate = (length * 8L * 1000L) / safeDuration
        return bitrate
            .takeIf { it in 32_000L..20_000_000L }
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
    }

    private fun estimateCompressedLosslessBitrate(
        bitDepth: Int?,
        samplingRateKhz: Double?,
        hires: Boolean,
    ): Int {
        val depth = bitDepth?.takeIf { it > 0 } ?: if (hires) 24 else 16
        val sampleRate =
            samplingRateKhz
                ?.takeIf { it > 0.0 }
                ?.let { (it * 1000.0).roundToInt() }
                ?: if (hires) 96_000 else 44_100
        val estimated = depth * sampleRate * DEFAULT_LOSSLESS_CHANNELS * FLAC_ESTIMATED_COMPRESSION_RATIO
        return estimated
            .roundToInt()
            .coerceIn(MIN_REASONABLE_BITRATE, MAX_REASONABLE_BITRATE)
    }

    private suspend fun fetchStreamContentLength(url: String): Long? {
        return try {
            val head =
                withTimeoutOrNull(CONTENT_LENGTH_TIMEOUT_MS) {
                    httpClient.head(url) {
                        header("Accept", "*/*")
                        header("User-Agent", BROWSER_USER_AGENT)
                    }
                }
            if (head != null && head.status.isSuccess()) {
                head.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
            }

            val ranged =
                withTimeoutOrNull(CONTENT_LENGTH_TIMEOUT_MS) {
                    httpClient.get(url) {
                        header("Accept", "*/*")
                        header("User-Agent", BROWSER_USER_AGENT)
                        header(HttpHeaders.Range, "bytes=0-0")
                    }
                }
            if (ranged == null || !ranged.status.isSuccess()) return null
            ranged.headers[HttpHeaders.ContentRange]
                ?.substringAfterLast('/', missingDelimiterValue = "")
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: ranged.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0L && ranged.status.value == 206 }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
    }

    private fun buildFlacLabel(
        bitDepth: Int?,
        samplingRateKhz: Double?,
        hires: Boolean,
    ): String =
        buildString {
            append("Qobuz ")
            append(if (hires) "Hi-Res FLAC" else "CD FLAC")
            if (bitDepth != null && samplingRateKhz != null) {
                append(" ")
                append(bitDepth)
                append("bit/")
                append(formatSamplingRate(samplingRateKhz))
                append("kHz")
            }
        }

    private fun buildQualityFallbackOrder(qualityCode: Int): List<Int> {
        val ladder = listOf(27, 7, 6, 5)
        val startIndex = ladder.indexOf(qualityCode)
        return if (startIndex >= 0) ladder.drop(startIndex) else listOf(qualityCode)
    }

    private fun searchTerms(query: Query): List<String> {
        val title = query.title.trim()
        val artists = query.artists.map { it.trim() }.filter { it.isNotBlank() }
        val artistPart = artists.take(3).joinToString(" ")
        val album = query.album.orEmpty().trim()
        return linkedSetOf(
            query.isrc.orEmpty().trim(),
            listOf(title, artists.firstOrNull().orEmpty(), album).filter { it.isNotBlank() }.joinToString(" "),
            listOf(title, artistPart, album).filter { it.isNotBlank() }.joinToString(" "),
            listOf(title, artists.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" "),
            listOf(title, artistPart).filter { it.isNotBlank() }.joinToString(" "),
            listOf(title, album).filter { it.isNotBlank() }.joinToString(" "),
            title,
        ).filter { it.isNotBlank() }
    }

    private fun collectArtistNames(track: JsonObject): List<String> {
        val names = mutableListOf<String>()
        track.obj("performer")?.stringOrNull("name")?.let(names::add)
        val album = track.obj("album")
        album?.obj("artist")?.stringOrNull("name")?.let(names::add)
        album?.arr("artists")?.forEach { artist -> (artist as? JsonObject)?.stringOrNull("name")?.let(names::add) }
        return names.distinct()
    }

    private fun versionMismatchPenalty(
        query: String,
        candidateTitle: String,
    ): Int {
        val strictTokens =
            listOf(
                "remix",
                "live",
                "instrumental",
                "karaoke",
                "sped up",
                "slowed",
            )
        val softTokens =
            listOf(
                "remaster",
                "remastered",
                "edit",
                "acoustic",
                "version",
                "mono",
                "stereo",
                "deluxe",
            )
        val queryHasStrict = strictTokens.any { query.contains(it) }
        val candidateHasStrict = strictTokens.any { candidateTitle.contains(it) }
        if (candidateHasStrict && !queryHasStrict) return REJECT_SCORE

        val queryHasSoft = softTokens.any { query.contains(it) }
        val candidateHasSoft = softTokens.any { candidateTitle.contains(it) }
        return if (candidateHasSoft && !queryHasSoft) -45 else 0
    }

    private fun Query.cacheKey(resolverRegion: String): String {
        return listOf(
            mediaId,
            trackCacheKey(),
            backend.name,
            resolverRegion,
            qualityCode.toString(),
        ).joinToString("::")
    }

    private fun Query.trackCacheKey(): String {
        return listOf(
            title.normalized(),
            artists.joinToString("|") { it.normalized() },
            album.normalized(),
            isrc?.trim()?.uppercase().orEmpty(),
            countryCode.uppercase(),
        ).joinToString("|")
    }

    private fun extractExpiryMs(url: String): Long {
        val etsp = runCatching { Url(url).parameters["etsp"] }.getOrNull()?.toLongOrNull()
        return if (etsp != null) (etsp * 1000L) - 15_000L else nowMs() + STREAM_CACHE_MS
    }

    private fun formatSamplingRate(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    private fun significantTokens(value: String): Set<String> {
        val stopWords = setOf("a", "an", "and", "feat", "ft", "for", "of", "the", "with")
        return value.split(" ")
            .map { it.trim() }
            .filter { it.length > 1 && it !in stopWords }
            .toSet()
    }

    private fun titleTokensPass(
        wantedTitle: String,
        candidateTitle: String,
        wantedTokens: Set<String>,
        matchedTokenCount: Int,
        recall: Double,
        precision: Double,
    ): Boolean {
        if (wantedTokens.isEmpty()) return true
        if (candidateTitle == wantedTitle || candidateTitle.contains(wantedTitle)) return true
        return when {
            wantedTokens.size <= 2 -> matchedTokenCount == wantedTokens.size
            wantedTokens.size <= 4 -> matchedTokenCount >= wantedTokens.size - 1 && recall >= 0.75 && precision >= 0.45
            wantedTokens.size <= 7 -> matchedTokenCount >= maxOf(3, wantedTokens.size - 2) && recall >= 0.6 && precision >= 0.4
            else -> matchedTokenCount >= maxOf(4, (wantedTokens.size * 3) / 5) && recall >= 0.6 && precision >= 0.35
        }
    }

    private fun artistNamesMatch(
        wantedArtist: String,
        candidateArtist: String,
    ): Boolean {
        if (wantedArtist == candidateArtist) return true
        if (wantedArtist.contains(candidateArtist) || candidateArtist.contains(wantedArtist)) {
            return minOf(wantedArtist.length, candidateArtist.length) >= 5
        }
        val wantedTokens = significantTokens(wantedArtist)
        val candidateTokens = significantTokens(candidateArtist)
        if (wantedTokens.isEmpty() || candidateTokens.isEmpty()) return false
        val overlap = wantedTokens.intersect(candidateTokens).size
        return overlap >= minOf(wantedTokens.size, candidateTokens.size).coerceAtMost(2)
    }

    // java.text.Normalizer (NFD + Mn strip) is JVM-only, so fold the common Latin
    // diacritics explicitly to keep matching deterministic on every KMP target.
    private val DIACRITIC_FOLDS: Map<Char, String> =
        mapOf(
            'à' to "a",
            'á' to "a",
            'â' to "a",
            'ã' to "a",
            'ä' to "a",
            'å' to "a",
            'æ' to "ae",
            'ç' to "c",
            'è' to "e",
            'é' to "e",
            'ê' to "e",
            'ë' to "e",
            'ì' to "i",
            'í' to "i",
            'î' to "i",
            'ï' to "i",
            'ñ' to "n",
            'ò' to "o",
            'ó' to "o",
            'ô' to "o",
            'õ' to "o",
            'ö' to "o",
            'œ' to "oe",
            'ù' to "u",
            'ú' to "u",
            'û' to "u",
            'ü' to "u",
            'ý' to "y",
            'ÿ' to "y",
            'ß' to "ss",
        )

    private fun String?.normalized(): String {
        val folded = orEmpty().lowercase().map { DIACRITIC_FOLDS[it] ?: it.toString() }.joinToString("")
        return folded
            .replace("&", " and ")
            .replace(Regex("""\[[^]]*]"""), " ")
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun String.wordsOverlap(other: String): Int {
        val first = split(' ').filter { it.length > 1 }.toSet()
        val second = other.split(' ').filter { it.length > 1 }.toSet()
        return first.intersect(second).size
    }

    /**
     * Parses three possible direct-ID forms out of a string and returns the
     * Qobuz track ID. Used by [resolve] to short-circuit fuzzy matching when
     * the caller already knows the exact track.
     *
     * Accepted forms:
     * - bare digits ("12345678")
     * - the "qobuz:track:<id>" URI scheme (case-insensitive)
     * - a qobuz.com URL with optional locale and album segments
     */
    fun String.toQobuzTrackIdOrNull(): String? {
        val trimmed = trim()
        if (trimmed.matches(Regex("\\d+"))) return trimmed
        Regex("""^qobuz:track:(\d+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        Regex("""qobuz\.com/(?:[a-z]{2}-[a-z]{2}/)?(?:album/[^/]+/)?(\d+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return null
    }

    /**
     * Returns up to [limit] de-duplicated Qobuz candidates for the query,
     * gathered across every available search backend and every search-term
     * variant. Used by the manual override UI.
     */
    suspend fun searchCandidates(
        query: Query,
        limit: Int = 12,
    ): List<CandidateMetadata> {
        val resolverRegion = normalizeResolverRegion(query.countryCode, query.backend)
        val results = linkedMapOf<String, CandidateMetadata>()
        for (term in searchTerms(query)) {
            for (backend in searchBackendOrder(query.backend)) {
                val search =
                    when (backend) {
                        SearchBackend.KENNY -> searchTracks(term, query.countryCode, SearchBackend.KENNY)
                        SearchBackend.SQUID -> searchTracks(term, resolverRegion, SearchBackend.SQUID)
                        SearchBackend.TRYPT -> searchTracks(term, resolverRegion, SearchBackend.TRYPT)
                    }
                val items = search.items ?: continue
                items.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val trackId = obj.stringOrNull("id") ?: return@forEach
                    if (results.containsKey(trackId)) return@forEach
                    val title = obj.stringOrNull("title").orEmpty()
                    val version = obj.stringOrNull("version")
                    val titleWithVersion = listOfNotNull(title.takeIf { it.isNotBlank() }, version).joinToString(" ")
                    val artistName = collectArtistNames(obj).firstOrNull().orEmpty()
                    val album = obj.obj("album")?.stringOrNull("title")
                    val isrc = obj.stringOrNull("isrc")
                    val durationSec = obj.intOrNull("duration")
                    val hires = obj.booleanOrDefault("hires")
                    val bitDepth = obj.intOrNull("maximum_bit_depth")
                    val samplingRate = obj.doubleOrNull("maximum_sampling_rate")
                    results[trackId] =
                        CandidateMetadata(
                            trackId = trackId,
                            title = titleWithVersion.ifBlank { title },
                            artist = artistName,
                            album = album,
                            isrc = isrc,
                            durationMs = durationSec?.toLong()?.times(1000L),
                            hires = hires,
                            bitDepth = bitDepth,
                            samplingRateKhz = samplingRate,
                        )
                    if (results.size >= limit) return results.values.toList()
                }
            }
        }
        return results.values.toList()
    }

    internal fun resetForTests(client: HttpClient? = null) {
        clientProvider = if (client != null) { { client } } else { { defaultHttpClient } }
        nowMs = { Clock.System.now().toEpochMilliseconds() }
        trackCache.clear()
        streamCache.clear()
        backendCooldownUntilMs.clear()
    }

    private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject

    private fun JsonObject.arr(name: String): JsonArray? = get(name) as? JsonArray

    private fun JsonObject.stringOrNull(name: String): String? =
        (get(name) as? JsonPrimitive)?.content?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    private fun JsonObject.intOrNull(name: String): Int? =
        (get(name) as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.doubleOrNull(name: String): Double? =
        (get(name) as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun JsonObject.booleanOrDefault(
        name: String,
        default: Boolean = false,
    ): Boolean = (get(name) as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: default
}
