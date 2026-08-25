package com.metrolist.music.wear

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared between the phone bridge and (conceptually) the wear app's data models.
 * A [WireMediaItem] is a minimal, JSON-serializable projection of a Media3 [MediaItem],
 * small enough to fit in a single Wear Data Layer Message (~100KB cap).
 */
data class WireMediaItem(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val artist: String? = null,
    val artworkUri: String? = null,
    val browsable: Boolean = false,
    val playable: Boolean = false,
    val durationMs: Long = 0L,
)

/**
 * LRU cache mapping mediaId -> the exact [MediaItem] produced by [MusicService]'s
 * MediaLibrarySessionCallback for browse/search results.
 *
 * Why: composite mediaIds such as `search/<query>/<songId>` are not guaranteed to
 * resolve through the service's `onGetItem`. By replaying the exact [MediaItem]
 * the service already built (with metadata + artwork + request metadata), we avoid
 * a second lookup and any ID-resolution mismatch.
 */
object MediaItemCache {
    private const val MAX = 1024
    private val map = object : LinkedHashMap<String, MediaItem>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaItem>?): Boolean =
            size > MAX
    }
    private val lock = Any()

    fun put(item: MediaItem) {
        val id = item.mediaId ?: return
        synchronized(lock) { map[id] = item }
    }

    fun putAll(items: List<MediaItem>) = items.forEach(::put)

    fun get(mediaId: String): MediaItem? = synchronized(lock) { map[mediaId] }
}

private const val K_ID = "mediaId"
private const val K_TITLE = "title"
private const val K_SUBTITLE = "subtitle"
private const val K_ARTIST = "artist"
private const val K_ART = "artworkUri"
private const val K_BROWSABLE = "browsable"
private const val K_PLAYABLE = "playable"
private const val K_DURATION = "durationMs"

fun MediaItem.toWire(): WireMediaItem {
    val md = mediaMetadata ?: MediaMetadata.EMPTY
    val artwork = md.artworkUri?.toString()
    val subtitle = md.subtitle?.toString()
    val artist = md.artist?.toString()
    return WireMediaItem(
        mediaId = mediaId ?: "",
        title = md.title?.toString().orEmpty(),
        subtitle = subtitle,
        artist = artist,
        artworkUri = artwork,
        browsable = md.isBrowsable == true,
        playable = md.isPlayable == true,
        durationMs = mediaMetadata?.durationMs ?: 0L,
    )
}

fun List<MediaItem>.toJsonArray(): ByteArray {
    val arr = JSONArray()
    for (item in this) {
        val w = item.toWire()
        arr.put(JSONObject().apply {
            put(K_ID, w.mediaId)
            put(K_TITLE, w.title)
            w.subtitle?.let { put(K_SUBTITLE, it) }
            w.artist?.let { put(K_ARTIST, it) }
            w.artworkUri?.let { put(K_ART, it) }
            put(K_BROWSABLE, w.browsable)
            put(K_PLAYABLE, w.playable)
            put(K_DURATION, w.durationMs)
        })
    }
    return arr.toString().toByteArray(Charsets.UTF_8)
}

fun parseWireItems(json: ByteArray): List<WireMediaItem> {
    if (json.isEmpty()) return emptyList()
    val arr = JSONArray(String(json, Charsets.UTF_8))
    val out = ArrayList<WireMediaItem>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out += WireMediaItem(
            mediaId = o.getString(K_ID),
            title = o.optString(K_TITLE),
            subtitle = o.optString(K_SUBTITLE).ifBlank { null },
            artist = o.optString(K_ARTIST).ifBlank { null },
            artworkUri = o.optString(K_ART).ifBlank { null },
            browsable = o.optBoolean(K_BROWSABLE),
            playable = o.optBoolean(K_PLAYABLE),
            durationMs = o.optLong(K_DURATION),
        )
    }
    return out
}
