/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.state.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import dev.musicx.desktop.state.content.SearchState
import dev.musicx.desktop.update.DesktopUpdateManager
import dev.musicx.desktop.update.GitHubDesktopRelease
import dev.musicx.desktop.update.desktopUpdateCheckIsDue
import dev.musicx.desktop.update.isNewerDesktopVersion
import dev.musicx.desktop.utils.AppLogger
import dev.musicx.shared.BuildConfig
import dev.musicx.shared.api.artwork.AnimatedArtworkService
import dev.musicx.shared.api.artwork.DirectCanvasProvider
import dev.musicx.shared.discord.DiscordRpcManager
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.copied
import dev.musicx.shared.generated.resources.could_not_download_update_manager
import dev.musicx.shared.generated.resources.update_failed
import dev.musicx.shared.generated.resources.updates_available_title
import dev.musicx.shared.generated.resources.updates_title
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.GlobalYouTube
import dev.musicx.shared.ui.settings.SettingsPageId
import dev.musicx.shared.utils.OsUtil
import dev.musicx.shared.utils.tryOpenUrl
import dev.nucleusframework.share.ShareException
import dev.nucleusframework.share.ShareSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

object AppState {
    val directCanvasProvider by lazy { DirectCanvasProvider(GlobalYouTube.client) }
    val animatedArtworkService by lazy { AnimatedArtworkService(GlobalYouTube.client) }
    internal val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val snackbarHostState = SnackbarHostState()

    private var updateDownloadJob: Job? = null

    private val updateInitialization: Job =
        scope.launch {
            applyReleases(DesktopUpdateManager.cachedReleases())
            if (Settings.shared.checkForUpdates &&
                desktopUpdateCheckIsDue(Settings.shared.lastUpdateCheckAttemptTimestamp, System.currentTimeMillis())
            ) {
                checkForUpdates(notify = true)
            }
        }

    private suspend fun applyReleases(releases: List<GitHubDesktopRelease>) {
        UiState.updateReleases = releases
        val update = releases.firstOrNull { isNewerDesktopVersion(it.version, BuildConfig.APP_VERSION) }
        if (UiState.availableUpdate?.version != update?.version) UiState.updateDownloadError = null
        UiState.availableUpdate = update
        UiState.canInstallUpdate = UiState.availableUpdate?.let { DesktopUpdateManager.canInstall(it) } == true
    }

    fun checkForUpdates(notify: Boolean = false) {
        if (UiState.isCheckingForUpdates || UiState.isDownloadingUpdate || UiState.updateManagerStarted) return
        UiState.isCheckingForUpdates = true
        UiState.updateCheckFailed = false
        scope.launch {
            try {
                updateInitialization.join()
                DesktopUpdateManager.check().fold(
                    onSuccess = {
                        applyReleases(it)
                        UiState.hasCheckedForUpdates = true
                        if (notify) notifyUpdateAvailable()
                    },
                    onFailure = {
                        UiState.updateCheckFailed = true
                        AppLogger.warning("Updates", "Update check failed", it)
                    },
                )
            } finally {
                UiState.isCheckingForUpdates = false
            }
        }
    }

    private fun notifyUpdateAvailable() {
        val release = UiState.availableUpdate ?: return
        if (!Settings.shared.updateNotifications || Settings.shared.lastNotifiedUpdateVersion == release.version) return
        scope.launch {
            Settings.shared.lastNotifiedUpdateVersion = release.version
            val result =
                snackbarHostState.showSnackbar(
                    message = getString(Res.string.updates_available_title, release.version),
                    actionLabel = getString(Res.string.updates_title),
                )
            if (result == SnackbarResult.ActionPerformed) {
                UiState.selectedSettingsPage = SettingsPageId.UPDATES
                UiState.showSettings = true
            }
        }
    }

    fun copyToClipboard(text: String) {
        try {
            val sel = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            scope.launch { snackbarHostState.showSnackbar(getString(Res.string.copied)) }
        } catch (_: Exception) {
        }
    }

    fun shareLink(
        link: String,
        title: String,
    ) {
        copyToClipboard(link)
        if (OsUtil.isLinux) {
            return
        }
        scope.launch {
            try {
                ShareSheet.share {
                    this.title = title
                    url(link)
                }
            } catch (e: ShareException) {
                AppLogger.warning("Main", "${e.error}: ${e.message}")
            }
        }
    }

    fun installUpdate() {
        val release = UiState.availableUpdate ?: return
        if (UiState.isDownloadingUpdate || UiState.isCheckingForUpdates || UiState.updateManagerStarted) return
        UiState.isDownloadingUpdate = true
        UiState.updateDownloadError = null
        updateDownloadJob =
            scope.launch {
                try {
                    if (!DesktopUpdateManager.canInstall(release)) {
                        openUpdateRelease(release)
                        return@launch
                    }
                    DesktopUpdateManager
                        .downloadAndLaunch(release.version) {
                            withContext(Dispatchers.Main) { UiState.updateManagerStarted = true }
                        }.fold(
                            onSuccess = {},
                            onFailure = {
                                AppLogger.warning("Updates", "Update manager download failed", it)
                                UiState.updateDownloadError = getString(Res.string.could_not_download_update_manager)
                            },
                        )
                } finally {
                    UiState.isDownloadingUpdate = false
                }
            }
    }

    fun openUpdateRelease(release: GitHubDesktopRelease) {
        tryOpenUrl(release.url) { opened ->
            if (!opened) {
                scope.launch {
                    val message = getString(Res.string.update_failed)
                    UiState.updateDownloadError = message
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        DesktopUpdateManager.cancelDownload()
    }

    suspend fun release() {
        cancelUpdateDownload()
        SearchState.close()
        scope.coroutineContext[Job]?.cancelAndJoin()
        DiscordRpcManager
            .destroy()
        runCatching { GlobalYouTube.close() }
    }
}
