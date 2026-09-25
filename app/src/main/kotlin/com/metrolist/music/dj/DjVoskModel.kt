/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Downloads and caches the small English Vosk model used for silent “Hey DJ 6” wake listening.
 */
object DjVoskModel {
    private const val TAG = "DjVoskModel"
    private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_ZIP_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    fun modelDir(context: Context): File =
        File(context.applicationContext.filesDir, "vosk/$MODEL_DIR_NAME")

    fun isReady(context: Context): Boolean {
        val dir = modelDir(context)
        return dir.isDirectory && File(dir, "am/final.mdl").isFile
    }

    /**
     * Ensures the model is on disk. Safe to call repeatedly; no-ops when ready.
     */
    suspend fun ensureReady(context: Context): Result<File> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val dest = modelDir(app)
            if (isReady(app)) return@withContext Result.success(dest)

            val parent = dest.parentFile ?: return@withContext Result.failure(IllegalStateException("No vosk dir"))
            parent.mkdirs()
            val zipFile = File(parent, "$MODEL_DIR_NAME.zip")
            try {
                if (dest.exists()) dest.deleteRecursively()
                Timber.i("$TAG: downloading Vosk model")
                val request = Request.Builder().url(MODEL_ZIP_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Model download HTTP ${response.code}"))
                    }
                    val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                    zipFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                unzip(zipFile, parent)
                zipFile.delete()
                if (!isReady(app)) {
                    return@withContext Result.failure(Exception("Model extract incomplete"))
                }
                Result.success(dest)
            } catch (e: Exception) {
                Timber.w(e, "$TAG: ensureReady failed")
                runCatching { zipFile.delete() }
                runCatching { dest.deleteRecursively() }
                Result.failure(e)
            }
        }

    private fun unzip(
        zipFile: File,
        destDir: File,
    ) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
