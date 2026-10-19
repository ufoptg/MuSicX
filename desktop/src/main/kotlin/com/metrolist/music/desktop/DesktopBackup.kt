/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Serializable
data class DesktopBackupPayload(
    val prefs: DesktopPrefs = DesktopPrefs(),
    val library: DesktopLibraryData = DesktopLibraryData(),
)

/**
 * JSON backup of prefs + library via AWT file chooser (SAF substitute).
 */
object DesktopBackup {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun export(): Result<File> =
        runCatching {
            val target =
                pickFile(save = true, defaultName = "musicx-backup.json")
                    ?: error("cancelled")
            val payload =
                DesktopBackupPayload(
                    prefs = DesktopPrefsStore.load(),
                    library = DesktopLibraryStore.load(),
                )
            target.writeText(json.encodeToString(payload))
            target
        }

    fun import(): Result<DesktopBackupPayload> =
        runCatching {
            val source =
                pickFile(save = false, defaultName = "musicx-backup.json")
                    ?: error("cancelled")
            val payload = json.decodeFromString<DesktopBackupPayload>(source.readText())
            DesktopPrefsStore.save(payload.prefs)
            DesktopLibraryStore.save(payload.library)
            payload
        }

    private fun pickFile(
        save: Boolean,
        defaultName: String,
    ): File? {
        var picked: File? = null
        val block =
            Runnable {
                val chooser =
                    JFileChooser().apply {
                        dialogTitle = if (save) "Export MuSicX backup" else "Import MuSicX backup"
                        fileFilter = FileNameExtensionFilter("JSON backup", "json")
                        selectedFile = File(defaultName)
                    }
                val result =
                    if (save) {
                        chooser.showSaveDialog(null)
                    } else {
                        chooser.showOpenDialog(null)
                    }
                if (result != JFileChooser.APPROVE_OPTION) return@Runnable
                val file = chooser.selectedFile ?: return@Runnable
                picked =
                    if (save && !file.name.endsWith(".json", ignoreCase = true)) {
                        File(file.parentFile, "${file.name}.json")
                    } else {
                        file
                    }
            }
        if (SwingUtilities.isEventDispatchThread()) {
            block.run()
        } else {
            SwingUtilities.invokeAndWait(block)
        }
        return picked
    }
}
