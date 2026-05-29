/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.db.entities.UploadQueueEntity
import com.metrolist.music.db.entities.UploadState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Persistent bottom bar that surfaces the DB-backed upload queue
 * ([com.metrolist.music.viewmodels.UploadViewModel.jobs]). Replaces the per-screen
 * upload `AlertDialog` and the temporary inline jobs list.
 *
 * Collapsed: a one-line summary ("Uploading N of M · P%") with an aggregate
 * progress indicator. Tapping expands a per-job list (name, state, progress,
 * and a per-row action: Cancel for active rows, Retry for failed ones) plus a
 * footer with "Cancel all" / "Clear completed".
 *
 * Visibility: shown while any job is active (`PENDING`/`RUNNING`); once every
 * job is terminal and none failed, it lingers [AUTO_HIDE_DELAY_MS] (mirroring
 * the old dialog's 1 s success delay) then hides. If any job `FAILED`, the bar
 * stays up so the failure is visible until the user retries or clears it.
 *
 * Stateless / VM-agnostic: callers pass the action lambdas (the screens wire
 * them to `UploadViewModel`).
 */
@Composable
fun UploadStatusBar(
    jobs: List<UploadQueueEntity>,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActive = jobs.any { it.state == UploadState.PENDING || it.state == UploadState.RUNNING }
    val hasFailed = jobs.any { it.state == UploadState.FAILED }

    var visible by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(jobs) {
        when {
            jobs.isEmpty() -> visible = false
            hasActive || hasFailed -> visible = true
            else -> {
                // All terminal and nothing failed → linger briefly, then auto-hide.
                visible = true
                delay(AUTO_HIDE_DELAY_MS)
                visible = false
            }
        }
    }

    AnimatedVisibility(
        visible = visible && jobs.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val total = jobs.size
        val done =
            jobs.count {
                it.state == UploadState.SUCCESS ||
                    it.state == UploadState.FAILED ||
                    it.state == UploadState.CANCELLED
            }
        val overall = if (total == 0) 0f else jobs.sumOf { effectiveProgress(it).toDouble() }.toFloat() / total
        val percent = (overall * 100).roundToInt()

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.upload_status_summary, done, total, percent),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { overall },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                        contentDescription = null,
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        jobs.forEach { job ->
                            Spacer(Modifier.height(12.dp))
                            UploadJobRow(job, onCancel = onCancel, onRetry = onRetry)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (hasActive) {
                                TextButton(onClick = onCancelAll) {
                                    Text(stringResource(R.string.upload_cancel_all))
                                }
                            }
                            val hasTerminal =
                                jobs.any {
                                    it.state == UploadState.SUCCESS ||
                                        it.state == UploadState.FAILED ||
                                        it.state == UploadState.CANCELLED
                                }
                            if (hasTerminal) {
                                TextButton(onClick = onClearCompleted) {
                                    Text(stringResource(R.string.upload_clear_completed))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadJobRow(
    job: UploadQueueEntity,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = job.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            UploadStateChip(job.state)
            // Action slot: Cancel while active, Retry once failed, empty otherwise.
            when (job.state) {
                UploadState.PENDING, UploadState.RUNNING ->
                    IconButton(onClick = { onCancel(job.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.upload_cancel),
                        )
                    }
                UploadState.FAILED ->
                    IconButton(onClick = { onRetry(job.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.replay),
                            contentDescription = stringResource(R.string.upload_retry),
                        )
                    }
                UploadState.SUCCESS, UploadState.CANCELLED ->
                    Box(modifier = Modifier.size(40.dp))
            }
        }
        if (job.state == UploadState.RUNNING) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { job.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UploadStateChip(state: UploadState) {
    val color =
        when (state) {
            UploadState.RUNNING -> MaterialTheme.colorScheme.primary
            UploadState.SUCCESS -> MaterialTheme.colorScheme.tertiary
            UploadState.FAILED -> MaterialTheme.colorScheme.error
            UploadState.PENDING, UploadState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color,
    ) {
        Text(
            text = state.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Terminal rows count as fully done; running rows report live progress. */
private fun effectiveProgress(job: UploadQueueEntity): Float =
    when (job.state) {
        UploadState.SUCCESS, UploadState.FAILED, UploadState.CANCELLED -> 1f
        UploadState.RUNNING -> job.progress
        UploadState.PENDING -> 0f
    }

private const val AUTO_HIDE_DELAY_MS = 1000L
