package dev.musicx.android.ui.menu

import androidx.compose.runtime.Composable
import dev.musicx.shared.db.MusicDatabase
import dev.musicx.shared.model.YTItem
import dev.musicx.shared.ui.menu.SpeedDialToggle
import org.koin.compose.koinInject
import dev.musicx.shared.ui.menu.rememberSpeedDialToggle as rememberSharedSpeedDialToggle

@Composable
internal fun rememberSpeedDialToggle(item: YTItem): SpeedDialToggle = rememberSharedSpeedDialToggle(item, koinInject<MusicDatabase>())
