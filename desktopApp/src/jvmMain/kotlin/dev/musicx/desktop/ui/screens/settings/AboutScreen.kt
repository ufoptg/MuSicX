/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.desktop.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.musicx.desktop.state.playback.PlayerState
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.app_name
import dev.musicx.shared.generated.resources.logo
import dev.musicx.shared.ui.screens.AboutScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DesktopAboutScreen(
    colorScheme: ColorScheme,
    onDevtoolsUnlocked: () -> Unit = {},
) {
    AboutScreen(
        logo = painterResource(Res.drawable.logo),
        appName = stringResource(Res.string.app_name),
        colorScheme = colorScheme,
        contentPadding = PaddingValues(32.dp),
        onDevtoolsUnlocked = onDevtoolsUnlocked,
        onPlayFavoriteSong = PlayerState::playFromId,
    )
}
