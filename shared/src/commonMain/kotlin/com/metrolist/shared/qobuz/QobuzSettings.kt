package com.metrolist.shared.qobuz

import com.metrolist.shared.state.GlobalYouTube

/**
 * Resolves the Qobuz catalog country. An explicit Qobuz country wins, then the
 * app content country, then the system locale. No fixed country is used as a
 * fallback; blank input stays blank and resolver-specific constraints apply
 * downstream.
 */
object QobuzSettings {
    private val countryCode = Regex("[A-Z]{2}")

    fun normalizeCountry(value: String?): String? = value?.trim()?.uppercase()?.takeIf { it.matches(countryCode) }

    fun effectiveCountry(
        configuredCountry: String,
        contentCountry: String,
        systemCountry: String,
    ): String =
        normalizeCountry(configuredCountry)
            ?: normalizeCountry(contentCountry.takeUnless { it == GlobalYouTube.SYSTEM })
            ?: systemCountry.trim().uppercase()
}
