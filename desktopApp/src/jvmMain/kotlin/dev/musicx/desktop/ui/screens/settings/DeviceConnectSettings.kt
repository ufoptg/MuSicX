/*
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import dev.musicx.desktop.deviceconnect.DesktopDeviceConnect
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.settings.DeviceConnectSettingsHost
import kotlinx.coroutines.launch

@Composable
internal fun rememberDeviceConnectSettingsHost(): DeviceConnectSettingsHost {
    val manager = DesktopDeviceConnect.manager
    val scope = rememberCoroutineScope()
    LaunchedEffect(Settings.shared.deviceConnectEnabled) {
        if (Settings.shared.deviceConnectEnabled) manager.refreshPairingState()
    }
    return DeviceConnectSettingsHost(
        state = manager.state,
        playbackState = manager.remote.state,
        onEnabledChange = { enabled ->
            if (enabled) scope.launch { manager.enable(Unit) } else manager.disable()
        },
        onUpdateName = manager::updateDeviceName,
        onRescan = manager::rescan,
        onConnectDirect = manager::connectDirect,
        onPair = manager::pair,
        onCancelPairing = manager::rejectPairing,
        onForget = { id -> scope.launch { manager.forget(id) } },
    )
}
