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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.UploadQueueEntity
import com.metrolist.music.upload.UploadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Thin DB client for the upload queue (`upload_queue` table). [enqueue]
 * validates picked URIs, writes `PENDING` rows, and starts [UploadService];
 * the service owns execution (Semaphore, retry, progress writes) so uploads
 * survive process death. This VM no longer drives the queue itself — it just
 * exposes [jobs] for the UI and kicks the service.
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

    init {
        // Resume an interrupted batch on app reopen: if the queue still has
        // PENDING/RUNNING rows (e.g. after a force-stop killed the service),
        // bring the service back so those rows continue. The service re-marks
        // orphaned RUNNING rows to PENDING on start.
        viewModelScope.launch(Dispatchers.IO) {
            if (database.countActiveUploads() > 0) {
                startUploadService()
            }
        }
    }

    /**
     * Validate the picked [uris] and enqueue the acceptable ones as `PENDING`
     * rows, then start [UploadService]. Unsupported formats and oversized files
     * are rejected with a toast.
     */
    fun enqueue(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            var enqueued = 0
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
                enqueued++
            }

            if (enqueued > 0) {
                startUploadService()
            }
        }
    }

    /** Cancel one queued/running upload. Routed to the service, which holds the job handle. */
    fun cancel(id: String) {
        context.startService(
            Intent(context, UploadService::class.java).apply {
                action = UploadService.ACTION_CANCEL
                putExtra(UploadService.EXTRA_UPLOAD_ID, id)
            },
        )
    }

    /** Cancel the entire active queue. */
    fun cancelAll() {
        context.startService(
            Intent(context, UploadService::class.java).apply {
                action = UploadService.ACTION_CANCEL_ALL
            },
        )
    }

    /**
     * Retry a FAILED row: flip it back to PENDING and (re)start the service —
     * a FAILED-only queue has no active rows, so the service has already stopped.
     */
    fun retry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.retryUpload(id)
            startUploadService()
        }
    }

    /** Remove all terminal rows (SUCCESS, CANCELLED, FAILED) from the queue. */
    fun clearCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            database.deleteCompletedUploads()
        }
    }

    private fun startUploadService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, UploadService::class.java),
        )
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
}
