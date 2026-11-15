package dev.musicx.desktop.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.app.DesktopNavigation
import dev.musicx.desktop.ui.screens.settings.ListenTogetherSettingsContent
import dev.musicx.shared.listentogether.ListenTogetherManager
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.navigation.Screen
import kotlinx.coroutines.launch
import dev.musicx.shared.ui.listentogether.ListenTogetherScreen as SharedListenTogetherScreen

@Composable
fun ListenTogetherScreen(manager: ListenTogetherManager) {
    var showSettings by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { DesktopNavigation.listenTogetherRoomCode = null }
    }

    if (!Settings.shared.listenTogetherEnabled && !showSettings) {
        LaunchedEffect(Unit) { DesktopNavigation.navigate(Screen.Home) }
        return
    }

    if (showSettings) {
        ListenTogetherSettingsContent(
            manager = manager,
            onNavigateBack = { showSettings = false },
        )
    } else {
        SharedListenTogetherScreen(
            manager = manager,
            initialRoomCode = DesktopNavigation.listenTogetherRoomCode,
            onNavigateBack = null,
            onNavigateToSettings = { showSettings = true },
            onCopyText = { _, text -> AppState.copyToClipboard(text) },
            onEventMessage = { message, _ -> AppState.scope.launch { AppState.snackbarHostState.showSnackbar(message) } },
        )
    }
}
