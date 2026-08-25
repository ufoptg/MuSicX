package dev.ufoptg.musicx.wear.model

import org.json.JSONArray
import org.json.JSONObject

/** Minimal projection of a Media3 MediaItem, parsed from the phone bridge's JSON. */
data class MediaItem(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val artist: String? = null,
    val artworkUri: String? = null,
    val browsable: Boolean = false,
    val playable: Boolean = false,
    val durationMs: Long = 0L,
)

data class NowPlaying(
    val title: String? = null,
    val subtitle: String? = null,
    val artist: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
) {
    val hasTrack: Boolean get() = !title.isNullOrBlank()
}

fun parseItems(json: ByteArray): List<MediaItem> {
    if (json.isEmpty()) return emptyList()
    val arr = JSONArray(String(json, Charsets.UTF_8))
    val out = ArrayList<MediaItem>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out += MediaItem(
            mediaId = o.getString("mediaId"),
            title = o.optString("title").ifBlank { "Unknown" },
            subtitle = o.optString("subtitle").ifBlank { null },
            artist = o.optString("artist").ifBlank { null },
            artworkUri = o.optString("artworkUri").ifBlank { null },
            browsable = o.optBoolean("browsable"),
            playable = o.optBoolean("playable"),
            durationMs = o.optLong("durationMs"),
        )
    }
    return out
}

fun parseNowPlaying(json: ByteArray): NowPlaying {
    if (json.isEmpty()) return NowPlaying()
    val o = JSONObject(String(json, Charsets.UTF_8))
    return NowPlaying(
        title = o.optString("title").ifBlank { null },
        subtitle = o.optString("subtitle").ifBlank { null },
        artist = o.optString("artist").ifBlank { null },
        artworkUri = o.optString("artworkUri").ifBlank { null },
        durationMs = o.optLong("durationMs"),
        positionMs = o.optLong("positionMs"),
        isPlaying = o.optBoolean("isPlaying"),
    )
}
