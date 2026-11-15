package dev.musicx.desktop.state

import androidx.compose.runtime.Composable
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.app_language_system
import dev.musicx.shared.ui.settings.SettingsChoice
import org.jetbrains.compose.resources.stringResource

@Composable
fun appLanguageChoices(): List<SettingsChoice> =
    DesktopLocaleManager.languages().map { language ->
        SettingsChoice(
            language.tag,
            when {
                language.isSystem -> stringResource(Res.string.app_language_system)
                else -> requireNotNull(language.displayName)
            },
        )
    }
