/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.UploadQueueEntity
import com.metrolist.music.db.entities.UploadState
import com.metrolist.music.utils.withJobRetry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Drives the DB-backed upload queue (`upload_queue` table). Replaces the
 * in-Compose upload loop that used to live in `LibrarySongsScreen`.
 *
 * [enqueue] validates the picked URIs and writes `PENDING` rows; a collector
 * in [init] picks those rows up and runs at most [MAX_CONCURRENT_UPLOADS] at a
 * time via [Semaphore]. Execution lives in [viewModelScope] — it survives
 * navigation but **not** process death; moving it to a foreground service is a
 * later iteration (#3604, iter 6).
 */
@HiltViewModel
class UploadViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    val jobs: StateFlow<List<UploadQueueEntity>> =
        database
            .observeAllUploads()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

    // Rows whose runOne has already been launched. Guards against the pending
    // collector re-emitting a still-PENDING row (it only leaves the query once
    // it flips to RUNNING) and double-launching it.
    private val active: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        viewModelScope.launch {
            database.observePendingUploads().collect { pending ->
                pending.forEach { row ->
                    if (active.add(row.id)) {
                        launch {
                            try {
                                semaphore.withPermit { runOne(row) }
                            } finally {
                                active.remove(row.id)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Validate the picked [uris] and enqueue the acceptable ones as `PENDING`
     * rows. Unsupported formats and oversized files are rejected with a toast.
     */
    fun enqueue(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (e: SecurityException) {
                    Timber.w(e, "Could not take persistable permission")
                }

                val fileName = resolveDisplayName(uri)
                val extension = fileName.substringAfterLast('.', "").lowercase()
                if (extension !in YouTube.SUPPORTED_UPLOAD_TYPES) {
                    toast(R.string.upload_unsupported_format)
                    return@forEach
                }

                val size =
                    context.contentResolver
                        .openFileDescriptor(uri, "r")
                        ?.use { it.statSize }
                        ?: -1L
                if (size <= 0L) return@forEach
                if (size > YouTube.MAX_UPLOAD_SIZE) {
                    toast(R.string.upload_file_too_large)
                    return@forEach
                }

                database.insert(
                    UploadQueueEntity(
                        uri = uri.toString(),
                        displayName = fileName,
                        sizeBytes = size,
                    ),
                )
            }
        }
    }

    private suspend fun runOne(row: UploadQueueEntity) {
        database.updateUploadState(row.id, UploadState.RUNNING)

        var lastProgressWrite = 0L
        val result =
            withJobRetry {
                YouTube.uploadSong(
                    filename = row.displayName,
                    contentLength = row.sizeBytes,
                    contentSource = {
                        context.contentResolver.openInputStream(row.uri.toUri())
                            ?: throw IOException("Cannot open ${row.uri}")
                    },
                    onProgress = { progress ->
                        val now = System.currentTimeMillis()
                        if (now - lastProgressWrite >= PROGRESS_WRITE_INTERVAL_MS) {
                            lastProgressWrite = now
                            viewModelScope.launch {
                                database.updateUploadProgress(row.id, progress)
                            }
                        }
                    },
                )
            }

        if (result.isSuccess && result.getOrDefault(false)) {
            database.updateUploadProgress(row.id, 1f)
            database.updateUploadState(
                row.id,
                UploadState.SUCCESS,
                completedAt = System.currentTimeMillis(),
            )
        } else {
            database.updateUploadState(
                row.id,
                UploadState.FAILED,
                error = result.exceptionOrNull()?.message,
                completedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        var fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                    }
                }
            }
        }
        return fileName
    }

    private suspend fun toast(resId: Int) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val MAX_CONCURRENT_UPLOADS = 2
        private const val PROGRESS_WRITE_INTERVAL_MS = 250L
    }
}
