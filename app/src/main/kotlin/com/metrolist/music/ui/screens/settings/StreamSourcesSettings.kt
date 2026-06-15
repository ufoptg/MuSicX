/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.StreamSourceAndroidCreatorKey
import com.metrolist.music.constants.StreamSourceAndroidVRKey
import com.metrolist.music.constants.StreamSourceIOSKey
import com.metrolist.music.constants.StreamSourceIPadOSKey
import com.metrolist.music.constants.StreamSourceTVHTML5Key
import com.metrolist.music.constants.StreamSourceVisionOSKey
import com.metrolist.music.constants.StreamSourceWebCreatorKey
import com.metrolist.music.constants.StreamSourceWebRemixKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSourcesSettings(
    navController: NavController
) {
    val (webRemix, onWebRemixChange) = rememberPreference(StreamSourceWebRemixKey, defaultValue = true)
    val (tvhtml5, onTvhtml5Change) = rememberPreference(StreamSourceTVHTML5Key, defaultValue = true)
    val (visionOS, onVisionOSChange) = rememberPreference(StreamSourceVisionOSKey, defaultValue = true)
    val (androidVR, onAndroidVRChange) = rememberPreference(StreamSourceAndroidVRKey, defaultValue = true)
    val (ios, onIosChange) = rememberPreference(StreamSourceIOSKey, defaultValue = false)
    val (ipadOS, onIpadOSChange) = rememberPreference(StreamSourceIPadOSKey, defaultValue = false)
    val (webCreator, onWebCreatorChange) = rememberPreference(StreamSourceWebCreatorKey, defaultValue = true)
    val (androidCreator, onAndroidCreatorChange) = rememberPreference(StreamSourceAndroidCreatorKey, defaultValue = false)

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.stream_source_web_clients),
            items = listOf(
                streamClientItem(R.string.stream_source_web_remix, R.string.stream_source_web_remix_desc, webRemix, onWebRemixChange),
                streamClientItem(R.string.stream_source_tvhtml5, R.string.stream_source_tvhtml5_desc, tvhtml5, onTvhtml5Change),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.stream_source_native_clients),
            items = listOf(
                streamClientItem(R.string.stream_source_visionos, R.string.stream_source_visionos_desc, visionOS, onVisionOSChange),
                streamClientItem(R.string.stream_source_android_vr, R.string.stream_source_android_vr_desc, androidVR, onAndroidVRChange),
                streamClientItem(R.string.stream_source_ios, R.string.stream_source_ios_desc, ios, onIosChange),
                streamClientItem(R.string.stream_source_ipados, R.string.stream_source_ipados_desc, ipadOS, onIpadOSChange),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.stream_source_creator_clients),
            items = listOf(
                streamClientItem(R.string.stream_source_web_creator, R.string.stream_source_web_creator_desc, webCreator, onWebCreatorChange),
                streamClientItem(R.string.stream_source_android_creator, R.string.stream_source_android_creator_desc, androidCreator, onAndroidCreatorChange),
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.stream_sources)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

@Composable
private fun streamClientItem(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Material3SettingsItem = Material3SettingsItem(
    icon = painterResource(R.drawable.play),
    title = { Text(stringResource(titleRes)) },
    description = { Text(stringResource(descriptionRes)) },
    trailingContent = {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = {
                Icon(
                    painter = painterResource(id = if (checked) R.drawable.check else R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        )
    },
    onClick = { onCheckedChange(!checked) },
)
