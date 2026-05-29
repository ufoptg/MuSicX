/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.upload

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.UploadQueueEntity
import com.metrolist.music.db.entities.UploadState
import com.metrolist.music.utils.withJobRetry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Foreground service that owns execution of the DB-backed upload queue
 * (`upload_queue`). Moving the queue driver here (it used to live in
 * [com.metrolist.music.viewmodels.UploadViewModel]'s scope) lets uploads
 * survive navigation, process death, and OS-driven app shutdown — the
 * ViewModel is now a thin DB client that just enqueues rows and starts us.
 *
 * On start we re-mark any rows orphaned in `RUNNING` by a previous process
 * back to `PENDING` (before collecting), then run at most
 * [MAX_CONCURRENT_UPLOADS] jobs at once via a [Semaphore]. We stop ourselves
 * once no `PENDING`/`RUNNING` rows remain.
 *
 * Cancel/Retry intents (#3604 iter 7) and the post-success
 * `syncUploadedSongs()` call (iter 8) are intentionally not wired here yet.
 */
@AndroidEntryPoint
class UploadService : LifecycleService() {
    @Inject
    lateinit var database: MusicDatabase

    private val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

    // Rows whose runOne has already been launched. Guards against the pending
    // collector re-emitting a still-PENDING row (it only leaves the query once
    // it flips to RUNNING) and double-launching it.
    private val active: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()

        lifecycleScope.launch {
            // Reactivate rows the previous process left mid-flight, BEFORE the
            // collector starts — otherwise stale RUNNING rows never retry.
            database.resetRunningUploads()

            database.observePendingUploads().collect { pending ->
                pending.forEach { row ->
                    if (active.add(row.id)) {
                        launch {
                            try {
                                semaphore.withPermit { runOne(row) }
                            } finally {
                                active.remove(row.id)
                                stopIfDone()
                            }
                        }
                    }
                }
                stopIfDone()
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
                        contentResolver.openInputStream(row.uri.toUri())
                            ?: throw IOException("Cannot open ${row.uri}")
                    },
                    onProgress = { progress ->
                        val now = System.currentTimeMillis()
                        if (now - lastProgressWrite >= PROGRESS_WRITE_INTERVAL_MS) {
                            lastProgressWrite = now
                            lifecycleScope.launch {
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

    /** Stop the service once nothing is pending or running. */
    private suspend fun stopIfDone() {
        if (active.isEmpty() && database.countActiveUploads() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (securityException: SecurityException) {
            Timber.w(securityException, "Unable to start upload foreground service")
            stopSelf()
        } catch (runtimeException: RuntimeException) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runtimeException::class.java.name ==
                "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                Timber.w(runtimeException, "Unable to start upload foreground service")
                stopSelf()
            } else {
                throw runtimeException
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.upload)
            .setContentTitle(getString(R.string.upload_notification_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL_ID = "uploads"
        private const val NOTIFICATION_ID = 9200
        private const val MAX_CONCURRENT_UPLOADS = 2
        private const val PROGRESS_WRITE_INTERVAL_MS = 250L
    }
}
