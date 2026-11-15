@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.musicx.desktop.crash.DesktopCrashRecovery
import dev.musicx.desktop.state.app.UiState
import dev.musicx.desktop.utils.DesktopRenderBackend
import dev.musicx.desktop.utils.desktopRenderBackends
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog
import com.metrolist.innertubex.extraction.strategy.PlaybackClientManifest
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.crash_logs
import dev.musicx.shared.generated.resources.crash_restart
import dev.musicx.shared.generated.resources.devtools_playback_client
import dev.musicx.shared.generated.resources.devtools_playback_client_automatic
import dev.musicx.shared.generated.resources.devtools_playback_client_automatic_description
import dev.musicx.shared.generated.resources.devtools_playback_client_diagnostics_description
import dev.musicx.shared.generated.resources.devtools_playback_client_legend_access
import dev.musicx.shared.generated.resources.devtools_playback_client_legend_content
import dev.musicx.shared.generated.resources.devtools_playback_client_sabr_first
import dev.musicx.shared.generated.resources.devtools_playback_client_sabr_first_description
import dev.musicx.shared.generated.resources.devtools_render_backend
import dev.musicx.shared.generated.resources.devtools_render_backend_auto
import dev.musicx.shared.generated.resources.devtools_render_backend_description
import dev.musicx.shared.generated.resources.devtools_render_backend_direct3d
import dev.musicx.shared.generated.resources.devtools_render_backend_metal
import dev.musicx.shared.generated.resources.devtools_render_backend_opengl
import dev.musicx.shared.generated.resources.devtools_render_backend_software
import dev.musicx.shared.generated.resources.devtools_switch_description
import dev.musicx.shared.generated.resources.devtools_title
import dev.musicx.shared.generated.resources.settings_crash_reports_summary
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.crash.CrashLogsScreen
import dev.musicx.shared.ui.settings.CapabilityStatusKey
import dev.musicx.shared.ui.settings.ClientAccessKey
import dev.musicx.shared.ui.settings.ClientAccessLabel
import dev.musicx.shared.ui.settings.ClientCapabilityChips
import dev.musicx.shared.ui.utils.DragScrollableColumn
import org.jetbrains.compose.resources.stringResource

@Composable
fun DevtoolsSettingsScreen(colorScheme: ColorScheme) {
    var showCrashLogs by remember { mutableStateOf(false) }
    if (showCrashLogs) {
        CrashLogsScreen(DesktopCrashRecovery.store, onClose = { showCrashLogs = false }, onShare = DesktopCrashRecovery::share)
        return
    }
    var devtoolsEnabled by remember { mutableStateOf(true) }
    val storedClientId = Settings.shared.devtoolsPlaybackClient
    val initialClientId =
        storedClientId.takeIf(PlaybackClientCatalog::isDevtoolsSelectionAllowed)
            ?: PlaybackClientCatalog.AUTOMATIC_ID
    var selectedClientId by remember {
        mutableStateOf(initialClientId)
    }
    LaunchedEffect(initialClientId, storedClientId) {
        if (initialClientId != storedClientId) {
            Settings.shared.devtoolsPlaybackClient = initialClientId
        }
    }
    val automaticTitle = stringResource(Res.string.devtools_playback_client_automatic)
    val automaticDescription = stringResource(Res.string.devtools_playback_client_automatic_description)
    val sabrFirstTitle = stringResource(Res.string.devtools_playback_client_sabr_first)
    val sabrFirstDescription = stringResource(Res.string.devtools_playback_client_sabr_first_description)
    val options =
        remember(automaticTitle, automaticDescription, sabrFirstTitle, sabrFirstDescription) {
            listOf(
                ClientRow(
                    id = PlaybackClientCatalog.AUTOMATIC_ID,
                    title = automaticTitle,
                    description = automaticDescription,
                ),
                ClientRow(
                    id = PlaybackClientCatalog.SABR_FIRST_ID,
                    title = sabrFirstTitle,
                    description = sabrFirstDescription,
                ),
            ) +
                PlaybackClientCatalog.devtoolsOverrideOptions.map { option ->
                    ClientRow(
                        id = option.id,
                        title = option.displayName,
                        description =
                            "${option.client.clientName} ·\u00a0${option.client.clientVersion} ·\u00a0" +
                                if (option.client.useSabr) "SABR" else "Direct",
                        manifest = option.manifest,
                    )
                }
        }

    DragScrollableColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 28.dp),
    ) {
        item {
            Material3SettingsGroup(
                colorScheme = colorScheme,
                items =
                    listOf(
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.devtools_title)) },
                            description = { Text(stringResource(Res.string.devtools_switch_description)) },
                            trailingContent = { SettingsSwitch(checked = devtoolsEnabled) },
                            checked = devtoolsEnabled,
                            onClick = {
                                devtoolsEnabled = !devtoolsEnabled
                                Settings.shared.setDevtoolsEnabled(devtoolsEnabled)
                            },
                        ),
                    ),
            )
        }
        item {
            TextButton(onClick = { showCrashLogs = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.crash_logs)) }
        }
        item {
            Text(
                stringResource(Res.string.settings_crash_reports_summary),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        }
        if (!devtoolsEnabled) return@DragScrollableColumn
        item {
            RenderBackendSettings(colorScheme)
        }
        item {
            PlaybackEventLogSection()
        }
        item {
            Material3SettingsGroup(
                title = stringResource(Res.string.devtools_playback_client),
                colorScheme = colorScheme,
                searchKeywords = listOf("playback", "client", "SABR", "streaming", "YouTube"),
                items =
                    listOf(
                        Material3SettingsItem(
                            title = {
                                Text(
                                    stringResource(Res.string.devtools_playback_client_diagnostics_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            },
                            enabled = true,
                            onClick = null,
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.devtools_playback_client_legend_content)) },
                            description = {
                                CapabilityStatusKey(Modifier.padding(top = 8.dp))
                            },
                            enabled = true,
                            onClick = null,
                        ),
                        Material3SettingsItem(
                            title = { Text(stringResource(Res.string.devtools_playback_client_legend_access)) },
                            description = {
                                ClientAccessKey(Modifier.padding(top = 8.dp))
                            },
                            enabled = true,
                            onClick = null,
                        ),
                    ) +
                        options.map { option ->
                            Material3SettingsItem(
                                leadingContent = {
                                    RadioButton(
                                        selected = selectedClientId == option.id,
                                        enabled = option.enabled,
                                        onClick = null,
                                    )
                                },
                                title = { Text(option.title) },
                                description = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(option.description)
                                        option.manifest?.let { manifest ->
                                            ClientCapabilityChips(manifest)
                                            ClientAccessLabel(manifest.authentication)
                                        }
                                    }
                                },
                                enabled = option.enabled,
                                onClick = {
                                    selectedClientId = option.id
                                    Settings.shared.devtoolsPlaybackClient = option.id
                                },
                            )
                        },
            )
        }
    }
}

@Composable
private fun RenderBackendSettings(colorScheme: ColorScheme) {
    val backends = remember { desktopRenderBackends() }
    var selected by remember {
        mutableStateOf(backends.firstOrNull { it.name == Settings.shared.desktopRenderBackend } ?: DesktopRenderBackend.AUTO)
    }
    Material3SettingsGroup(
        title = stringResource(Res.string.devtools_render_backend),
        colorScheme = colorScheme,
        items =
            listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(Res.string.devtools_render_backend_description)) },
                    trailingContent = {
                        TextButton(onClick = { UiState.restartRequested = true }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.crash_restart))
                        }
                    },
                ),
            ) +
                backends.map { backend ->
                    Material3SettingsItem(
                        leadingContent = { RadioButton(selected = selected == backend, onClick = null) },
                        title = {
                            Text(
                                stringResource(
                                    when (backend) {
                                        DesktopRenderBackend.AUTO -> Res.string.devtools_render_backend_auto
                                        DesktopRenderBackend.DIRECT3D -> Res.string.devtools_render_backend_direct3d
                                        DesktopRenderBackend.OPENGL -> Res.string.devtools_render_backend_opengl
                                        DesktopRenderBackend.SOFTWARE -> Res.string.devtools_render_backend_software
                                        DesktopRenderBackend.METAL -> Res.string.devtools_render_backend_metal
                                    },
                                ),
                            )
                        },
                        onClick = {
                            selected = backend
                            Settings.shared.desktopRenderBackend = backend.name
                        },
                    )
                },
    )
}

private data class ClientRow(
    val id: String,
    val title: String,
    val description: String,
    val manifest: PlaybackClientManifest? = null,
    val enabled: Boolean = true,
)
