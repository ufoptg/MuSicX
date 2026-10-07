/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * Compares [packageVersion] to the latest GitHub release for ufoptg/MuSicX.
 * Shows a result only — never installs.
 */
object DesktopUpdater {
    /** Keep in sync with desktop/build.gradle.kts packageVersion (do not bump here). */
    const val PACKAGE_VERSION = "13.14.2"

    private const val LATEST_URL = "https://api.github.com/repos/ufoptg/MuSicX/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }

    data class CheckResult(
        val current: String,
        val latest: String?,
        val message: String,
    )

    suspend fun checkLatest(): CheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val body =
                    URI(LATEST_URL)
                        .toURL()
                        .openConnection()
                        .apply {
                            setRequestProperty("Accept", "application/vnd.github+json")
                            setRequestProperty("User-Agent", "MuSicX-Desktop/$PACKAGE_VERSION")
                            connectTimeout = 15_000
                            readTimeout = 15_000
                        }.getInputStream()
                        .bufferedReader()
                        .use { it.readText() }
                val tag =
                    json
                        .parseToJsonElement(body)
                        .jsonObject["tag_name"]
                        ?.jsonPrimitive
                        ?.content
                        ?.trim()
                        .orEmpty()
                val latest = tag.removePrefix("v").removePrefix("V")
                val message =
                    when {
                        latest.isBlank() -> "Could not read latest release tag"
                        normalize(latest) == normalize(PACKAGE_VERSION) -> "Up to date ($PACKAGE_VERSION)"
                        else -> "Update available: $latest (you have $PACKAGE_VERSION)"
                    }
                CheckResult(PACKAGE_VERSION, latest.ifBlank { null }, message)
            }.getOrElse { error ->
                DesktopLog.log("update check failed", error)
                CheckResult(
                    PACKAGE_VERSION,
                    null,
                    "Check failed: ${error.message ?: error::class.simpleName}",
                )
            }
        }

    private fun normalize(v: String): String = v.trim().removePrefix("v").removePrefix("V")
}
