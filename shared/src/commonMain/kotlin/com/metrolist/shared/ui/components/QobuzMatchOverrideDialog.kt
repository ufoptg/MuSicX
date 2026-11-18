package com.metrolist.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.ok
import com.metrolist.shared.generated.resources.qobuz_match_override
import com.metrolist.shared.generated.resources.qobuz_match_override_auto
import com.metrolist.shared.generated.resources.qobuz_match_override_bit_depth
import com.metrolist.shared.generated.resources.qobuz_match_override_current
import com.metrolist.shared.generated.resources.qobuz_match_override_empty
import com.metrolist.shared.generated.resources.qobuz_match_override_hires
import com.metrolist.shared.generated.resources.qobuz_match_override_sampling_rate
import com.metrolist.shared.generated.resources.qobuz_match_override_searching
import com.metrolist.shared.qobuz.QobuzAudioProvider
import com.metrolist.shared.qobuz.QobuzMatchOverride
import com.metrolist.shared.qobuz.QobuzMatchOverrides
import com.metrolist.shared.qobuz.QobuzSettings
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.utils.systemCountryCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Lets the user pin a specific Qobuz track to [mediaId] when the automatic
 * matcher picks the wrong one. The selected track is persisted as a manual
 * override and the Qobuz stream cache for [mediaId] is dropped so the next
 * play uses it immediately.
 */
@Composable
fun QobuzMatchOverrideDialog(
    mediaId: String,
    title: String,
    artists: List<String>,
    album: String?,
    isrc: String?,
    durationMs: Long?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember(mediaId) { mutableStateOf(true) }
    var candidates by remember(mediaId) { mutableStateOf<List<QobuzAudioProvider.CandidateMetadata>>(emptyList()) }
    var current by remember(mediaId) { mutableStateOf(QobuzMatchOverrides.overrides[mediaId]) }
    var selectedTrackId by remember(mediaId) { mutableStateOf(current?.qobuzTrackId) }

    LaunchedEffect(mediaId) {
        val settings = Settings.shared
        val query =
            QobuzAudioProvider.Query(
                mediaId = mediaId,
                title = title,
                artists = artists.filter { it.isNotBlank() },
                album = album?.takeIf { it.isNotBlank() },
                isrc = isrc?.takeIf { it.isNotBlank() },
                durationMs = durationMs?.takeIf { it > 0L },
                countryCode =
                    QobuzSettings.effectiveCountry(
                        configuredCountry = settings.qobuzCountry,
                        contentCountry = settings.contentCountry,
                        systemCountry = systemCountryCode(),
                    ),
                backend = settings.qobuzBackend,
                qualityCode = QobuzAudioProvider.qualityCodeFor(settings.qobuzAudioQuality),
            )
        candidates =
            try {
                QobuzAudioProvider.searchCandidates(query)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
        loading = false
    }

    fun persist(override: QobuzMatchOverride?) {
        if (override == null) {
            QobuzMatchOverrides.remove(mediaId)
        } else {
            QobuzMatchOverrides.set(mediaId, override)
        }
        // Keep the dialog composed until the cache entry is dropped, otherwise
        // dismissing first would cancel this composition-bound coroutine.
        scope.launch {
            QobuzAudioProvider.invalidate(mediaId)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.qobuz_match_override)) },
        text = {
            Column {
                current?.let {
                    Text(
                        text = stringResource(Res.string.qobuz_match_override_current, it.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                when {
                    loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 16.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(Res.string.qobuz_match_override_searching))
                        }
                    }

                    candidates.isEmpty() -> {
                        Text(
                            text = stringResource(Res.string.qobuz_match_override_empty),
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        ) {
                            items(candidates, key = { it.trackId }) { candidate ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedTrackId = candidate.trackId }
                                            .padding(vertical = 8.dp),
                                ) {
                                    RadioButton(
                                        selected = selectedTrackId == candidate.trackId,
                                        onClick = { selectedTrackId = candidate.trackId },
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(
                                            text = candidate.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        val subtitle = candidateSubtitle(candidate)
                                        if (subtitle.isNotBlank()) {
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedTrackId != null && selectedTrackId != current?.qobuzTrackId,
                onClick = {
                    val candidate = candidates.firstOrNull { it.trackId == selectedTrackId }
                    if (candidate == null) {
                        onDismiss()
                    } else {
                        persist(
                            QobuzMatchOverride(
                                qobuzTrackId = candidate.trackId,
                                label =
                                    listOfNotNull(
                                        candidate.title.takeIf { it.isNotBlank() },
                                        candidate.artist.takeIf { it.isNotBlank() },
                                    ).joinToString(" — ").ifBlank { candidate.trackId },
                                hires = candidate.hires,
                                bitDepth = candidate.bitDepth,
                                samplingRateKhz = candidate.samplingRateKhz,
                            ),
                        )
                    }
                },
            ) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { persist(null) }) {
                        Text(stringResource(Res.string.qobuz_match_override_auto))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun candidateSubtitle(candidate: QobuzAudioProvider.CandidateMetadata): String =
    buildList {
        candidate.artist.takeIf { it.isNotBlank() }?.let(::add)
        candidate.album?.takeIf { it.isNotBlank() }?.let(::add)
        if (candidate.hires) {
            add(
                listOfNotNull(
                    stringResource(Res.string.qobuz_match_override_hires),
                    candidate.bitDepth?.let { stringResource(Res.string.qobuz_match_override_bit_depth, it) },
                    candidate.samplingRateKhz?.let {
                        stringResource(Res.string.qobuz_match_override_sampling_rate, formatKhz(it))
                    },
                ).joinToString(" "),
            )
        }
    }.joinToString(" · ")

private fun formatKhz(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
