/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.upload

import android.app.Notification
import android.app.PendingIntent
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
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.withJobRetry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

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
 * Cancel & Retry: the status bar and notification deliver `ACTION_CANCEL(id)` /
 * `ACTION_CANCEL_ALL` intents here (only the service holds the per-row coroutine
 * [Job] handles needed to abort a live upload). Retry is a pure DB write driven
 * from the ViewModel, so it has no action here.
 *
 * Once a batch goes idle (queue fully drained) and at least one upload
 * succeeded, we kick a single [SyncUtils.syncUploadedSongs] so the library
 * reflects the new songs even if no upload screen is open.
 */
@AndroidEntryPoint
class UploadService : LifecycleService() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var syncUtils: SyncUtils

    private val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

    // Set when any upload in the current run reaches SUCCESS; consumed once the
    // queue drains to trigger a single uploaded-songs sync (coalesces a whole
    // batch into one sync). Atomic because stopIfDone can be entered from both
    // the collector and a job's finally, interleaving at suspend points.
    private val hadSuccessfulUpload = AtomicBoolean(false)

    // Rows whose runOne has been launched, mapped to their coroutine Job so a
    // specific row can be cancelled. Guards against the pending collector
    // re-emitting a still-PENDING row (it only leaves the query once it flips to
    // RUNNING) and double-launching it.
    private val active = ConcurrentHashMap<String, Job>()

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()

        lifecycleScope.launch {
            // Reactivate rows the previous process left mid-flight, BEFORE the
            // collector starts — otherwise stale RUNNING rows never retry.
            database.resetRunningUploads()

            database.observePendingUploads().collect { pending ->
                pending.forEach { row ->
                    if (!active.containsKey(row.id)) {
                        val job =
                            launch(start = CoroutineStart.LAZY) {
                                try {
                                    semaphore.withPermit { runOne(row) }
                                } finally {
                                    active.remove(row.id)
                                    stopIfDone()
                                }
                            }
                        active[row.id] = job
                        job.start()
                    }
                }
                stopIfDone()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_CANCEL ->
                intent.getStringExtra(EXTRA_UPLOAD_ID)?.let { id ->
                    lifecycleScope.launch { cancelOne(id) }
                }
            ACTION_CANCEL_ALL -> lifecycleScope.launch { cancelAll() }
        }
        return START_NOT_STICKY
    }

    /** Cancel a single row: mark it CANCELLED, then abort its job if running. */
    private suspend fun cancelOne(id: String) {
        // Mark CANCELLED first so the row leaves the PENDING query and loses the
        // markUploadRunning race if runOne is just starting.
        database.updateUploadState(id, UploadState.CANCELLED, completedAt = System.currentTimeMillis())
        active.remove(id)?.cancel()
        stopIfDone()
    }

    /** Cancel the whole queue (notification "Cancel all"). */
    private suspend fun cancelAll() {
        database.cancelActiveUploads(System.currentTimeMillis())
        val jobs = active.values.toList()
        active.clear()
        jobs.forEach { it.cancel() }
        stopIfDone()
    }

    private suspend fun runOne(row: UploadQueueEntity) {
        // Atomic PENDING -> RUNNING; bail if the row was cancelled in the gap
        // between the collector reading it and us acquiring the permit.
        if (database.markUploadRunning(row.id) == 0) return

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

        // If this job was cancelled mid-flight, the cancel path already wrote
        // CANCELLED — propagate the cancellation instead of overwriting it with
        // FAILED (uploadSong swallows CancellationException into Result.failure,
        // and withJobRetry returns it without rethrowing).
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        coroutineContext.ensureActive()

        if (result.isSuccess && result.getOrDefault(false)) {
            database.updateUploadProgress(row.id, 1f)
            database.updateUploadState(
                row.id,
                UploadState.SUCCESS,
                completedAt = System.currentTimeMillis(),
            )
            hadSuccessfulUpload.set(true)
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
            // Batch idle: if anything succeeded this run, refresh the library
            // once. Fire-and-forget on SyncUtils' own scope, so it survives the
            // stopSelf() below; getAndSet keeps it to a single sync per batch.
            if (hadSuccessfulUpload.getAndSet(false)) {
                syncUtils.syncUploadedSongs()
            }
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

    private fun buildNotification(): Notification {
        val cancelAllIntent =
            PendingIntent.getService(
                this,
                0,
                Intent(this, UploadService::class.java).apply { action = ACTION_CANCEL_ALL },
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.upload)
            .setContentTitle(getString(R.string.upload_notification_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(R.drawable.close, getString(R.string.upload_cancel_all), cancelAllIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "uploads"
        const val ACTION_CANCEL = "com.metrolist.music.upload.action.CANCEL"
        const val ACTION_CANCEL_ALL = "com.metrolist.music.upload.action.CANCEL_ALL"
        const val EXTRA_UPLOAD_ID = "com.metrolist.music.upload.extra.UPLOAD_ID"
        private const val NOTIFICATION_ID = 9200
        private const val MAX_CONCURRENT_UPLOADS = 2
        private const val PROGRESS_WRITE_INTERVAL_MS = 250L
    }
}
