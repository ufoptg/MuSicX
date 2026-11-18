/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Manual Qobuz match override — lets the user pin a specific Qobuz track ID
 * to a given mediaId when the auto-matcher picks the wrong candidate. The
 * override travels through the playback pipeline as a [QobuzAudioProvider]
 * direct-ID short-circuit (see toQobuzTrackIdOrNull), so playback skips the
 * fuzzy search entirely.
 *
 * Persisted in settings as a JSON map { mediaId: { trackId, label, hires,
 * bitDepth, samplingRateKhz } }. Automatic matches are in-memory only
 * (QobuzAudioProvider.trackCache) — no Room schema change.
 */

package com.metrolist.shared.qobuz

import com.metrolist.shared.constants.QobuzMatchOverridesJsonKey
import com.metrolist.shared.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
data class QobuzMatchOverride(
    val qobuzTrackId: String,
    val label: String,
    val hires: Boolean = false,
    val bitDepth: Int? = null,
    val samplingRateKhz: Double? = null,
) {
    fun providerMediaId(): String = "qobuz:track:$qobuzTrackId"
}

object QobuzMatchOverrides {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /** Overrides currently persisted in the settings JSON key. */
    val overrides: Map<String, QobuzMatchOverride> get() = decode(Settings.shared.qobuzMatchOverridesJson)

    fun decode(value: String?): MutableMap<String, QobuzMatchOverride> {
        if (value.isNullOrBlank()) return mutableMapOf()
        return runCatching {
            val root = json.parseToJsonElement(value).jsonObject
            val out = mutableMapOf<String, QobuzMatchOverride>()
            root.forEach { (mediaId, element) ->
                val obj = element as? JsonObject ?: return@forEach
                // Numbers and booleans coerce to strings, matching the v13 org.json optString behavior.
                val trackId = (obj["trackId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@forEach
                val label = (obj["label"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: trackId
                val hires = (obj["hires"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
                val bitDepth = (obj["bitDepth"] as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it > 0 }
                val sampling = (obj["samplingRateKhz"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0.0 }
                out[mediaId] =
                    QobuzMatchOverride(
                        qobuzTrackId = trackId,
                        label = label,
                        hires = hires,
                        bitDepth = bitDepth,
                        samplingRateKhz = sampling,
                    )
            }
            out
        }.getOrDefault(mutableMapOf())
    }

    fun encode(overrides: Map<String, QobuzMatchOverride>): String {
        val root =
            buildJsonObject {
                overrides.forEach { (mediaId, override) ->
                    put(
                        mediaId,
                        buildJsonObject {
                            put("trackId", JsonPrimitive(override.qobuzTrackId))
                            put("label", JsonPrimitive(override.label))
                            put("hires", JsonPrimitive(override.hires))
                            if (override.bitDepth != null) put("bitDepth", JsonPrimitive(override.bitDepth))
                            if (override.samplingRateKhz != null) put("samplingRateKhz", JsonPrimitive(override.samplingRateKhz))
                        },
                    )
                }
            }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun set(
        mediaId: String,
        override: QobuzMatchOverride,
    ) {
        Settings.shared.qobuzMatchOverridesJson = encode(overrides + (mediaId to override))
    }

    fun remove(mediaId: String) {
        Settings.shared.qobuzMatchOverridesJson = encode(overrides - mediaId)
    }
}
