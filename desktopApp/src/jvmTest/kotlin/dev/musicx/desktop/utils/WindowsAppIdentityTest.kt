/*
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.utils

import java.io.File
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsAppIdentityTest {
    @Test
    fun discordHandlerUsesPerUserSchemeAndCurrentPortableExecutable() {
        val directory = Files.createTempDirectory("MuSicX portable żółć ")
        try {
            val launcher = directory.resolve("MuSicX.exe").also { it.writeBytes(byteArrayOf(1)) }.toFile()
            val values = mutableMapOf<Pair<String, String>, String>()
            assertTrue(
                registerWindowsUriHandler(launcher, "MuSicX", "metrolist-auth") { key, name, value -> values[key to name] = value },
            )
            val key = "Software\\Classes\\metrolist-auth"
            assertEquals("", values[key to "URL Protocol"])
            assertEquals("\"${launcher.absolutePath}\" \"%1\"", values["$key\\shell\\open\\command" to ""])
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun uriHandlerQuotesPackagedLauncherAndForwardsOneUrlArgument() {
        val directory = Files.createTempDirectory("MuSicX żółć ")
        try {
            val launcher = directory.resolve("MuSicX.exe").also { it.writeBytes(byteArrayOf(1)) }.toFile()
            val values = mutableMapOf<Pair<String, String>, String>()
            registerWindowsUriHandler(launcher, "MuSicX") { key, name, value -> values[key to name] = value }

            val key = "Software\\Classes\\metrolist"
            assertEquals(
                mapOf(
                    (key to "") to "MuSicX",
                    (key to "URL Protocol") to "",
                    ("$key\\DefaultIcon" to "") to "\"${launcher.absolutePath}\",0",
                    ("$key\\shell\\open\\command" to "") to "\"${launcher.absolutePath}\" \"%1\"",
                ),
                values,
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun uriHandlerDoesNotRegisterUnpackagedOrMissingLaunchers() {
        val directory = Files.createTempDirectory("metrolist-uri")
        try {
            val java = directory.resolve("java").also { it.writeBytes(byteArrayOf(1)) }.toFile()
            val candidates = listOf(null, File("MuSicX.exe"), directory.toFile(), directory.resolve("missing.exe").toFile(), java)
            for (launcher in candidates) {
                registerWindowsUriHandler(launcher, "MuSicX") { _, _, _ -> error("Unexpected registration for $launcher") }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
