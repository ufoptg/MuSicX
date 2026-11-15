/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.shared.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.musicx.shared.ui.haptics.HapticEvent
import dev.musicx.shared.ui.haptics.rememberMuSicXHaptics
import dev.musicx.shared.ui.icons.MuSicXIcons

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberMuSicXHaptics()
    Switch(
        checked = checked,
        onCheckedChange =
            onCheckedChange?.let { onChange ->
                { value ->
                    haptics.toggle(value)
                    onChange(value)
                }
            },
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            if (checked) {
                Icon(
                    imageVector = MuSicXIcons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            } else {
                Icon(
                    imageVector = MuSicXIcons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
    )
}
