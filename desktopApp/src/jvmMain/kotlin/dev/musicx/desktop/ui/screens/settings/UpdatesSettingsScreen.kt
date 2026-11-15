/*
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.update.DesktopUpdateManager
import dev.musicx.desktop.update.GitHubDesktopRelease
import dev.musicx.shared.BuildConfig
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.action_download
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.close
import dev.musicx.shared.generated.resources.downloading_percent
import dev.musicx.shared.generated.resources.general
import dev.musicx.shared.generated.resources.percent_symbol
import dev.musicx.shared.generated.resources.settings_check_releases_summary
import dev.musicx.shared.generated.resources.settings_release_overview
import dev.musicx.shared.generated.resources.update_downloading_manager
import dev.musicx.shared.generated.resources.updates_auto_check
import dev.musicx.shared.generated.resources.updates_available_title
import dev.musicx.shared.generated.resources.updates_changelog_preview
import dev.musicx.shared.generated.resources.updates_changelogs
import dev.musicx.shared.generated.resources.updates_check
import dev.musicx.shared.generated.resources.updates_checking
import dev.musicx.shared.generated.resources.updates_date
import dev.musicx.shared.generated.resources.updates_error
import dev.musicx.shared.generated.resources.updates_install
import dev.musicx.shared.generated.resources.updates_installing_title
import dev.musicx.shared.generated.resources.updates_last_check
import dev.musicx.shared.generated.resources.updates_no_updates
import dev.musicx.shared.generated.resources.updates_notifications
import dev.musicx.shared.generated.resources.updates_release_notes_title
import dev.musicx.shared.generated.resources.updates_retry
import dev.musicx.shared.generated.resources.updates_size
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.components.AlertDialog
import dev.musicx.shared.ui.components.PopupContent
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.settings.settingsPageSubtitle
import dev.musicx.shared.ui.update.GitHubMarkdown
import dev.musicx.shared.ui.update.UpdateInfo
import dev.musicx.shared.ui.utils.rememberFileSizeFormatter
import dev.musicx.shared.ui.utils.verticalScrollWithDrag
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdatesSettingsScreen(colorScheme: ColorScheme) {
    val formatFileSize = rememberFileSizeFormatter()
    var expandedRelease by remember { mutableStateOf<GitHubDesktopRelease?>(null) }
    val progress by DesktopUpdateManager.downloadProgress.collectAsState()
    val pending = UiState.availableUpdate
    val busy = UiState.isCheckingForUpdates || UiState.isDownloadingUpdate || UiState.updateManagerStarted

    LaunchedEffect(Unit) {
        if (!UiState.hasCheckedForUpdates) AppState.checkForUpdates()
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScrollWithDrag(rememberScrollState()).padding(vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Material3SettingsGroup(
            colorScheme = colorScheme,
            items =
                buildList {
                    add(
                        Material3SettingsItem(
                            icon = MuSicXIcons.Outlined.Refresh,
                            title = {
                                Text(
                                    stringResource(
                                        when {
                                            UiState.isCheckingForUpdates -> Res.string.updates_checking
                                            UiState.updateCheckFailed -> Res.string.updates_error
                                            UiState.hasCheckedForUpdates && pending == null -> Res.string.updates_no_updates
                                            else -> Res.string.updates_check
                                        },
                                    ),
                                )
                            },
                            description = { Text(stringResource(Res.string.settings_check_releases_summary)) },
                            enabled = !busy,
                            onClick = { AppState.checkForUpdates() },
                        ),
                    )
                    if (pending != null) {
                        add(
                            Material3SettingsItem(
                                icon = MuSicXIcons.Outlined.NewReleases,
                                title = { Text(stringResource(Res.string.updates_available_title, pending.version)) },
                                description = { Text(stringResource(Res.string.settings_release_overview)) },
                                onClick = { expandedRelease = pending },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = MuSicXIcons.Outlined.Upgrade,
                                title = {
                                    Text(
                                        stringResource(
                                            when {
                                                UiState.updateManagerStarted -> Res.string.updates_installing_title
                                                UiState.isDownloadingUpdate -> Res.string.update_downloading_manager
                                                UiState.updateDownloadError != null -> Res.string.updates_retry
                                                UiState.canInstallUpdate -> Res.string.updates_install
                                                else -> Res.string.action_download
                                            },
                                        ),
                                    )
                                },
                                description = UiState.updateDownloadError?.let { message -> { Text(message, color = colorScheme.error) } },
                                enabled = !busy,
                                onClick = AppState::installUpdate,
                            ),
                        )
                    }
                },
        )
        if (UiState.isCheckingForUpdates || UiState.isDownloadingUpdate) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val downloadProgress = progress
                if (UiState.isDownloadingUpdate && downloadProgress != null) {
                    LinearWavyProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(Res.string.downloading_percent, downloadProgress, stringResource(Res.string.percent_symbol)))
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (UiState.isDownloadingUpdate && !UiState.updateManagerStarted) {
                    TextButton(
                        onClick = AppState::cancelUpdateDownload,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            UpdateInfo(
                version = BuildConfig.APP_VERSION,
                details =
                    listOf(
                        stringResource(Res.string.updates_last_check) to
                            Settings.shared.lastUpdateCheckTimestamp
                                .takeIf { it > 0 }
                                ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
                                .orEmpty(),
                    ),
            )
        }
        Material3SettingsGroup(
            title = stringResource(Res.string.general),
            colorScheme = colorScheme,
            items =
                listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(Res.string.updates_auto_check)) },
                        checked = Settings.shared.checkForUpdates,
                        onClick = {
                            Settings.shared.checkForUpdates = !Settings.shared.checkForUpdates
                            if (Settings.shared.checkForUpdates) AppState.checkForUpdates()
                        },
                        trailingContent = { SettingsSwitch(checked = Settings.shared.checkForUpdates) },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(Res.string.updates_notifications)) },
                        checked = Settings.shared.updateNotifications,
                        onClick = { Settings.shared.updateNotifications = !Settings.shared.updateNotifications },
                        trailingContent = { SettingsSwitch(checked = Settings.shared.updateNotifications) },
                    ),
                ),
        )
        if (UiState.updateReleases.isNotEmpty()) {
            Material3SettingsGroup(
                title = stringResource(Res.string.updates_changelogs),
                colorScheme = colorScheme,
                items =
                    UiState.updateReleases.map { release ->
                        Material3SettingsItem(
                            title = { Text(release.version) },
                            description = {
                                Text(
                                    settingsPageSubtitle(
                                        stringResource(Res.string.settings_release_overview),
                                        release.publishedAt?.substringBefore('T'),
                                    ),
                                )
                            },
                            onClick = { expandedRelease = release },
                        )
                    },
            )
        }
    }
    PopupContent(expandedRelease) { release ->
        AlertDialog(
            onDismissRequest = { expandedRelease = null },
            title = { Text(stringResource(Res.string.updates_release_notes_title, release.version)) },
            text = {
                Column(
                    Modifier.verticalScrollWithDrag(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    UpdateInfo(
                        version = release.version,
                        details =
                            buildList {
                                release.publishedAt?.let { add(stringResource(Res.string.updates_date) to it.substringBefore('T')) }
                                if (UiState.canInstallUpdate && release == pending) {
                                    release.installer?.let { add(stringResource(Res.string.updates_size) to formatFileSize(it.size)) }
                                }
                            },
                    )
                    GitHubMarkdown(release.body.orEmpty())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { expandedRelease = null },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.close)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    AppState.openUpdateRelease(release)
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_download)) }
            },
        )
    }
}
