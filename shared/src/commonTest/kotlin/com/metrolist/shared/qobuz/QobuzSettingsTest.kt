package com.metrolist.shared.qobuz

import com.metrolist.shared.state.GlobalYouTube
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QobuzSettingsTest {
    @Test
    fun normalizeCountryAcceptsTwoLetterCodesOnly() {
        assertEquals("DE", QobuzSettings.normalizeCountry(" de "))
        assertEquals("US", QobuzSettings.normalizeCountry("us"))
        assertNull(QobuzSettings.normalizeCountry(""))
        assertNull(QobuzSettings.normalizeCountry("  "))
        assertNull(QobuzSettings.normalizeCountry("USA"))
        assertNull(QobuzSettings.normalizeCountry("1A"))
        assertNull(QobuzSettings.normalizeCountry(null))
    }

    @Test
    fun explicitCountryWinsOverContentAndSystem() {
        assertEquals(
            "JP",
            QobuzSettings.effectiveCountry(
                configuredCountry = "jp",
                contentCountry = "DE",
                systemCountry = "FR",
            ),
        )
    }

    @Test
    fun blankExplicitCountryFallsBackToContentCountry() {
        assertEquals(
            "DE",
            QobuzSettings.effectiveCountry(
                configuredCountry = "",
                contentCountry = "DE",
                systemCountry = "FR",
            ),
        )
    }

    @Test
    fun systemContentCountryFallsBackToSystemLocale() {
        assertEquals(
            "FR",
            QobuzSettings.effectiveCountry(
                configuredCountry = "",
                contentCountry = GlobalYouTube.SYSTEM,
                systemCountry = "fr",
            ),
        )
    }

    @Test
    fun invalidExplicitCountryFallsThroughToContentCountry() {
        assertEquals(
            "DE",
            QobuzSettings.effectiveCountry(
                configuredCountry = "XX1",
                contentCountry = "DE",
                systemCountry = "FR",
            ),
        )
    }

    @Test
    fun blankInputsStayBlankInsteadOfDefaultingToAFixedCountry() {
        assertEquals(
            "",
            QobuzSettings.effectiveCountry(
                configuredCountry = "",
                contentCountry = GlobalYouTube.SYSTEM,
                systemCountry = "",
            ),
        )
    }
}
