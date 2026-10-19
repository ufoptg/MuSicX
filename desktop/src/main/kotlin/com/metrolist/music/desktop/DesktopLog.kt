/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Tiny append-only file logger. A packaged desktop app has no console, so we write
 * timestamped diagnostics to a log file the user can send us to pinpoint slow/failing
 * stages (resolve vs. VLC buffering vs. fallback download).
 */
object DesktopLog {
    private val stamp = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    val logFile: File by lazy {
        val dir = System.getProperty("java.io.tmpdir") ?: "."
        File(dir, "musicx-desktop.log").also { f ->
            runCatching {
                f.appendText("\n===== MuSicX desktop session ${LocalDateTime.now()} =====\n")
            }
        }
    }

    fun log(message: String) {
        val line = "${LocalDateTime.now().format(stamp)}  $message"
        runCatching { logFile.appendText(line + "\n") }
        System.err.println("[MuSicX] $line")
    }

    fun log(
        message: String,
        error: Throwable,
    ) {
        log("$message :: ${error::class.simpleName}: ${error.message}")
        runCatching { logFile.appendText(error.stackTraceToString() + "\n") }
    }
}
