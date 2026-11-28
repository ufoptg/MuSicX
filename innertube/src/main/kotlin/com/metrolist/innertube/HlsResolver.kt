package com.metrolist.innertube

import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.models.YouTubeClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result of resolving an HLS-based video stream for inline playback.
 *
 * @param audioUrl Direct audio segment URL (or master manifest URL if no separate audio variant).
 * @param videoUrl Direct video segment URL (or master manifest URL if no separate video variant).
 * @param videoWidth Rendered video width, or null if unknown.
 * @param videoHeight Rendered video height, or null if unknown.
 * @param hlsMasterUrl The master manifest URL itself, used as a fallback when no variant can be selected.
 */
data class HlsStreamResult(
    val audioUrl: String,
    val videoUrl: String?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val hlsMasterUrl: String?,
)

/**
 * A single variant entry from an HLS master manifest playlist.
 *
 * @param url Variant media playlist URL (resolved against the master URL).
 * @param height Variant height in pixels.
 * @param width Variant width in pixels.
 * @param bandwidth Variant bandwidth in bits per second.
 */
data class HlsVariant(
    val url: String,
    val height: Int,
    val width: Int,
    val bandwidth: Int,
)

/**
 * Resolves a watchable HLS video stream for inline playback.
 *
 * Fetches the YouTube player response for [videoId], extracts the HLS master manifest URL,
 * downloads it, parses available variants, and selects the best audio + video pair within
 * [targetHeight]. Returns null when no HLS stream or no usable variant can be found.
 *
 * Ported from Metrolist v15 `DefaultStreamResolver.resolveHlsStream`.
 */
suspend fun resolveHlsStream(videoId: String, targetHeight: Int = 1080): HlsStreamResult? {
    val playerResponse = YouTube.player(videoId = videoId, client = YouTubeClient.WEB_REMIX).getOrNull() ?: return null
    val masterUrl = playerResponse.streamingData?.hlsManifestUrl ?: return null
    val manifestText = withTimeoutOrNull(4_000.milliseconds) {
        YouTube.httpClient().get(masterUrl) { header("User-Agent", YouTubeClient.WEB_REMIX.userAgent) }.bodyAsText()
    } ?: return null
    val variants = parseHlsMasterPlaylist(masterUrl, manifestText)
    if (variants.isEmpty()) {
        return HlsStreamResult(audioUrl = masterUrl, videoUrl = masterUrl, videoWidth = null, videoHeight = null, hlsMasterUrl = masterUrl)
    }
    val withinCap = variants.filter { it.height <= targetHeight }
    val chosenVideo = withinCap.maxByOrNull { it.height } ?: variants.minByOrNull { it.height } ?: variants.first()
    val chosenAudio = variants.filter { it.height in 360..720 }.maxByOrNull { it.bandwidth } ?: chosenVideo
    return HlsStreamResult(
        audioUrl = chosenAudio.url,
        videoUrl = chosenVideo.url,
        videoWidth = chosenVideo.width,
        videoHeight = chosenVideo.height,
        hlsMasterUrl = masterUrl,
    )
}

/**
 * Parses an HLS master manifest playlist and returns available [HlsVariant] entries.
 *
 * Expected manifest format (simplified):
 * ```
 * #EXTM3U
 * #EXT-X-STREAM-INF:BANDWIDTH=nnn,RESOLUTION=WxH,CODECS="..."
 * https://example.com/video_variant.m3u8
 * #EXT-X-STREAM-INF:BANDWIDTH=...,RESOLUTION=...
 * https://example.com/audio_variant.m3u8
 * ```
 */
fun parseHlsMasterPlaylist(masterUrl: String, manifestText: String): List<HlsVariant> {
    val variants = mutableListOf<HlsVariant>()
    val lines = manifestText.lines().toList()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (!line.startsWith("#EXT-X-STREAM-INF:") || line.length <= 16) {
            i++
            continue
        }
        val attrs = line.substringAfter("#EXT-X-STREAM-INF:")
        val bandwidth = attrs.split(",").firstOrNull { it.startsWith("BANDWIDTH=") }?.substringAfter("BANDWIDTH=")?.toIntOrNull() ?: 0
        val parts = attrs.split(",").firstOrNull { it.startsWith("RESOLUTION=") }?.substringAfter("RESOLUTION=")?.split("x")
        val width = parts?.getOrNull(0)?.toIntOrNull()
        val height = parts?.getOrNull(1)?.toIntOrNull()
        if (width == null || height == null) {
            i++
            continue
        }

        // The variant URL is on the next non-comment, non-empty line
        var j = i + 1
        while (j < lines.size && (lines[j].trim().startsWith("#") || lines[j].trim().isBlank())) {
            j++
        }
        if (j >= lines.size) { i = j; continue }

        val url = lines[j].trim()
        val absoluteUrl = if (url.startsWith("http")) url else URL(URL(masterUrl), url).toString()
        variants.add(HlsVariant(url = absoluteUrl, height = height, width = width, bandwidth = bandwidth))
        i = j + 1
    }
    return variants
}

/**
 * Extracts the HLS master manifest URL from a [PlayerResponse]'s raw streamingData JSON.
 * In practice the manifest lives under `hlsManifestUrl` inside the streamingData object.
 */
internal fun extractHlsMasterUrl(response: PlayerResponse): String? = response.streamingData?.hlsManifestUrl