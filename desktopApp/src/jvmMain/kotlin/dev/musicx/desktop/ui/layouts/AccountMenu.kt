/*
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.desktop.ui.layouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.state.auth.AuthState
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.account_downloading_update
import dev.musicx.shared.generated.resources.account_logout_webview_failed
import dev.musicx.shared.generated.resources.account_retry_cleanup
import dev.musicx.shared.generated.resources.account_update_available
import dev.musicx.shared.generated.resources.account_webview_remove_failed
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.state.GlobalYouTubeRepository
import dev.musicx.shared.ui.components.ContainedLoadingIndicator
import dev.musicx.shared.ui.icons.MuSicXIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import dev.musicx.shared.ui.components.AccountMenu as SharedAccountMenu

@Composable
fun AccountMenu(onDismiss: () -> Unit) {
    var logoutError by remember { mutableStateOf<String?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val webViewRemoveFailed = stringResource(Res.string.account_webview_remove_failed)
    val colorScheme = MaterialTheme.colorScheme
    val performLogout: (() -> Unit) -> Unit = { onSuccess ->
        if (!isLoggingOut) {
            isLoggingOut = true
            scope.launch {
                val failure = AuthState.signOut()
                isLoggingOut = false
                if (failure == null) {
                    onSuccess()
                } else {
                    logoutError = failure.message ?: webViewRemoveFailed
                }
            }
        }
    }

    SharedAccountMenu(
        isSignedIn = AuthState.isSignedIn,
        accountName =
            GlobalYouTubeRepository.instance.lastAccountInfo?.name
                ?: Settings.shared.accountName.takeIf { it.isNotBlank() },
        accountSubtitle =
            GlobalYouTubeRepository.instance.lastAccountInfo?.channelHandle
                ?: Settings.shared.accountChannelHandle.takeIf { it.isNotBlank() }
                ?: GlobalYouTubeRepository.instance.lastAccountInfo?.email
                ?: Settings.shared.accountEmail.takeIf { it.isNotBlank() },
        accountImageUrl = AuthState.profilePicUrl,
        onDismiss = onDismiss,
        onSignIn = {
            UiState.showSignIn = true
            UiState.showSwitchChannel = false
            UiState.showSettings = false
            PlayerState.exitExpanded()
        },
        onTokenLogin = { UiState.showTokenLogin = true },
        onSwitchChannel = { UiState.showSwitchChannel = true },
        showStatistics = false,
        onNavigateToIntegrations = {
            UiState.showIntegrationsSettings = true
            UiState.showSettings = false
            UiState.showSignIn = false
            UiState.showSwitchChannel = false
            PlayerState.exitExpanded()
        },
        onNavigateToSettings = {
            UiState.showSettings = true
            UiState.showSignIn = false
            UiState.showSwitchChannel = false
            PlayerState.exitExpanded()
        },
        onLogout = performLogout,
        isLoggingOut = isLoggingOut,
        menuMaxWidth = 432.dp,
        afterMenuContent = { dismiss ->
            if (Settings.shared.credentialsPending) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ContainedLoadingIndicator(Modifier.size(32.dp))
                }
            }
            logoutError?.let { error ->
                Surface(shape = RoundedCornerShape(12.dp), color = colorScheme.errorContainer) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(Res.string.account_logout_webview_failed),
                            color = colorScheme.onErrorContainer,
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onErrorContainer,
                        )
                        FilledTonalButton(
                            onClick = {
                                logoutError = null
                                performLogout { dismiss {} }
                            },
                            enabled = !isLoggingOut,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(Res.string.account_retry_cleanup))
                        }
                    }
                }
            }
            UiState.availableUpdate?.let { pendingUpdate ->
                Surface(shape = RoundedCornerShape(12.dp), color = colorScheme.surfaceContainer) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    dismiss { AppState.installUpdate() }
                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            MuSicXIcons.Outlined.Upgrade,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            if (UiState.isDownloadingUpdate) {
                                stringResource(Res.string.account_downloading_update, pendingUpdate.version)
                            } else {
                                stringResource(Res.string.account_update_available, pendingUpdate.version)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    )
}
