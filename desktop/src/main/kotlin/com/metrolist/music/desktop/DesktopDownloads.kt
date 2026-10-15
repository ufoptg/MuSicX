/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline downloads stored as <musicxDataDir>/downloads/<videoId>.<ext>. The index of
 * downloaded songs lives in [DesktopLibraryData.downloads]; this class only touches files.
 */
class DesktopDownloads(
    private val client: DesktopInnerTube,
    private val player: DesktopAudioPlayer,
    val dir: File = File(musicxDataDir(), "downloads"),
) {
    suspend fun download(hit: SearchHit, quality: Int = 1): DownloadInfo =
        withContext(Dispatchers.IO) {
            val stream = client.resolveAudioStream(hit.videoId, quality)
            val ext = if (stream.mimeType.orEmpty().contains("webm", ignoreCase = true)) "webm" else "m4a"
            dir.mkdirs()
            val out = File(dir, "${hit.videoId}.$ext")
            val part = File(dir, "${hit.videoId}.$ext.part")
            try {
                player.downloadToFile(stream, part)
                check(part.length() >= 1024L) { "Downloaded audio too small (${part.length()} bytes)" }
                out.delete()
                check(part.renameTo(out)) { "Could not move download into place" }
            } finally {
                part.delete()
            }
            DownloadInfo(
                videoId = hit.videoId,
                title = hit.title,
                artist = hit.subtitle,
                thumbnailUrl = hit.thumbnailUrl,
                durationSec = stream.mediaMetadata?.durationSeconds ?: 0,
                filePath = out.absolutePath,
                sizeBytes = out.length(),
            )
        }

    fun delete(info: DownloadInfo) {
        File(info.filePath).delete()
    }

    /** The downloaded file for [videoId], or null if it is not downloaded or the file is gone. */
    fun localFile(
        videoId: String,
        downloads: List<DownloadInfo>,
    ): File? = downloads.firstOrNull { it.videoId == videoId }?.let { File(it.filePath) }?.takeIf { it.isFile }

    fun sizeBytes(): Long = dirSize(dir)

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private val tempDir: File get() = File(System.getProperty("java.io.tmpdir") ?: ".")

        /** Size of streaming fallback temp files (musicx-*.m4a/webm) in the system temp dir. */
        private fun songCacheFiles(): List<File> =
            tempDir.listFiles { f ->
                f.isFile && f.name.startsWith("musicx-") && (f.extension == "m4a" || f.extension == "webm")
            }?.toList().orEmpty()

        fun songCacheBytes(): Long = songCacheFiles().sumOf { it.length() }

        fun clearSongCache() {
            songCacheFiles().forEach { it.delete() }
        }

        fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        fun formatBytes(bytes: Long): String =
            when {
                bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
                bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
                bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
                else -> "$bytes B"
            }
    }
}
