package com.metrolist.shared.qobuz

import com.metrolist.shared.settings.Settings
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QobuzMatchOverrideTest {
    @BeforeTest
    fun setUp() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
    }

    @AfterTest
    fun tearDown() {
        Settings.replaceSharedForTest(null)
    }

    @Test
    fun decodeEncodeRoundTripsAllFields() {
        val override = QobuzMatchOverride("987654", "Label", hires = true, bitDepth = 24, samplingRateKhz = 192.0)
        val decoded = QobuzMatchOverrides.decode(QobuzMatchOverrides.encode(mapOf("media-1" to override)))
        assertEquals(override, decoded["media-1"])
    }

    @Test
    fun decodeToleratesMissingOptionalFieldsAndBlankEntries() {
        val decoded =
            QobuzMatchOverrides.decode(
                """
                {"a":{"trackId":"1","label":"L"},"b":{"trackId":"  "},"c":{"trackId":2}}
                """.trimIndent(),
            )
        assertEquals(setOf("a", "c"), decoded.keys)
        assertEquals("L", decoded["a"]?.label)
        assertFalse(decoded["a"]?.hires == true)
        assertEquals("2", decoded["c"]?.qobuzTrackId)
        assertNull(decoded["c"]?.bitDepth)
    }

    @Test
    fun decodeBlankAndMalformedInputReturnsEmpty() {
        assertTrue(QobuzMatchOverrides.decode(null).isEmpty())
        assertTrue(QobuzMatchOverrides.decode("   ").isEmpty())
        assertTrue(QobuzMatchOverrides.decode("{not json").isEmpty())
        assertTrue(QobuzMatchOverrides.decode("""{"a":{"label":"no track id"}}""").isEmpty())
    }

    @Test
    fun overridesAreBackedByTheSettingsJsonKey() {
        assertTrue(QobuzMatchOverrides.overrides.isEmpty())

        QobuzMatchOverrides.set("media-1", QobuzMatchOverride("42", "Hi"))
        assertEquals("42", QobuzMatchOverrides.overrides["media-1"]?.qobuzTrackId)
        assertEquals("qobuz:track:42", QobuzMatchOverrides.overrides["media-1"]?.providerMediaId())

        QobuzMatchOverrides.remove("media-1")
        assertTrue(QobuzMatchOverrides.overrides.isEmpty())
    }
}
