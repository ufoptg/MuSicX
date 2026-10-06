/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launches a dedicated Chrome/Edge profile so the user can sign in normally,
 * then reads the youtube.com cookies from that profile's cookie store.
 *
 * Windows-only because cookie values are DPAPI-decrypted. On other platforms the
 * paste-cookie flow remains the way to sign in; the code path returns a clear
 * error explaining that.
 */
object BrowserCookieImporter {
    const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/"

    fun isWindows(): Boolean =
        (System.getProperty("os.name") ?: "").lowercase().contains("win")

    private fun browserExe(): File? {
        if (!isWindows()) return null
        val localAppData = System.getenv("LOCALAPPDATA") ?: ""
        val programFiles = System.getenv("PROGRAMFILES") ?: ""
        val programFilesX86 = System.getenv("PROGRAMFILES(X86)") ?: ""
        val candidates =
            listOf(
                File(localAppData, "Google\\Chrome\\Application\\chrome.exe"),
                File(programFiles, "Google\\Chrome\\Application\\chrome.exe"),
                File(localAppData, "Microsoft\\Edge\\Application\\msedge.exe"),
                File(programFiles, "Microsoft\\Edge\\Application\\msedge.exe"),
                File(programFilesX86, "Microsoft\\Edge\\Application\\msedge.exe"),
            )
        return candidates.firstOrNull { it.isFile }
    }

    private fun profileDir(): File {
        val base =
            (System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home"))
                .let { File(it, "MuSicX") }
        val dir = File(base, "browser-profile")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Opens the temp-profile browser at the Google sign-in page, then waits until
     * that browser window closes and returns the youtube.com cookie header.
     * Throws with a user-readable message on failure.
     */
    suspend fun import(): String =
        withContext(Dispatchers.IO) {
            val exe =
                browserExe()
                    ?: error("Chrome or Edge not found. Sign in via the system browser and paste the cookie below.")
            val profile = profileDir()
            DesktopLog.log("BrowserCookieImporter: launching ${exe.name} with temp profile ${profile.absolutePath}")
            val process =
                ProcessBuilder(
                    exe.absolutePath,
                    "--user-data-dir=${profile.absolutePath}",
                    "--no-first-run",
                    "--no-default-browser-check",
                    SIGN_IN_URL,
                ).start()
            process.waitFor()
            DesktopLog.log("BrowserCookieImporter: browser process exited, reading cookie store")
            extractCookies(profile)
        }

    private fun extractCookies(profile: File): String {
        val db =
            sequenceOf(
                File(profile, "Default\\Network\\Cookies"),
                File(profile, "Default\\Cookies"),
            ).firstOrNull { it.isFile }
                ?: error("No browser cookie store found. Sign in to YouTube Music in the browser window, then close it, and try again.")

        val tmp = Files.createTempDirectory("musicx-cookies").toFile()
        fun copy(src: File, name: String) {
            val dest = File(tmp, name)
            if (src.isFile) Files.copy(src.toPath(), dest.toPath())
        }
        copy(db, "Cookies")
        copy(File(db.parentFile, "Cookies-wal"), "Cookies-wal")
        copy(File(db.parentFile, "Cookies-shm"), "Cookies-shm")

        val aesKey =
            deriveAesKey(File(profile, "Local State"))
                ?: error("Could not read the browser encryption key from ${profile.absolutePath}")

        val cookies = LinkedHashMap<String, String>()
        DriverManager.getConnection("jdbc:sqlite:${File(tmp, "Cookies").absolutePath}").use { conn ->
            conn
                .prepareStatement(
                    "SELECT name, value, encrypted_value, host_key FROM cookies WHERE host_key LIKE '%youtube.com'",
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("name") ?: continue
                            val value = rs.getString("value").orEmpty()
                            val encrypted = rs.getBytes("encrypted_value")
                            val decrypted =
                                when {
                                    value.isNotEmpty() -> value
                                    encrypted != null && encrypted.isNotEmpty() ->
                                        runCatching { decryptCookie(encrypted, aesKey) }
                                            .getOrElse {
                                                DesktopLog.log("Could not decrypt cookie '$name'", it)
                                                ""
                                            }
                                    else -> ""
                                }
                            if (decrypted.isNotEmpty()) {
                                cookies[name] = decrypted
                            }
                        }
                    }
                }
        }

        if ("SAPISID" !in cookies) {
            error("No YouTube sign-in cookies found in the browser profile. Make sure you completed sign-in (and landed on music.youtube.com) before closing the browser.")
        }
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** Reads the Local State file and runs its DPAPI-encrypted AES key through dpapiUnprotect. */
    private fun deriveAesKey(localState: File): ByteArray? {
        if (!localState.isFile) return null
        return runCatching {
            val json = Json.parseToJsonElement(localState.readText()).jsonObject
            val encKeyB64 =
                json["os_crypt"]
                    ?.jsonObject
                    ?.get("encrypted_key")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: return null
            val raw = Base64.getDecoder().decode(encKeyB64)
            // raw starts with the 5 bytes "DPAPI", rest is the DPAPI blob.
            val blob = raw.copyOfRange(5, raw.size)
            dpapiUnprotect(blob)
        }.getOrNull()
    }

    private fun dpapiUnprotect(blob: ByteArray): ByteArray {
        val b64 = Base64.getEncoder().encodeToString(blob)
        val command =
            listOf(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "[Convert]::ToBase64String(" +
                    "[System.Security.Cryptography.ProtectedData]::Unprotect(" +
                    "[Convert]::FromBase64String('$b64'), \$null, " +
                    "[System.Security.Cryptography.DataProtectionScope]::CurrentUser))",
            )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        if (exitCode != 0 || output.isEmpty()) {
            error("DPAPI unprotect failed (exit $exitCode): ${output.take(500)}")
        }
        return Base64.getDecoder().decode(output)
    }

    /** Decrypts a Chrome v10 AES-GCM cookie value with the given key bytes. */
    private fun decryptCookie(encrypted: ByteArray, key: ByteArray): String {
        val prefix = String(encrypted, 0, 3, Charsets.US_ASCII)
        require(prefix == "v10") { "Unsupported cookie format '$prefix'" }
        val nonce = encrypted.copyOfRange(3, 15)
        val ciphertext = encrypted.copyOfRange(15, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce),
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
