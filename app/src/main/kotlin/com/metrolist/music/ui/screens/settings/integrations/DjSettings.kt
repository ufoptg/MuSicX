/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AiDjPersonaKey
import com.metrolist.music.constants.AiDjTalkEnabledKey
import com.metrolist.music.constants.DEFAULT_AI_DJ_PERSONA
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.OpenRouterModelKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjSettings(navController: NavController) {
    var aiDjTalkEnabled by rememberPreference(AiDjTalkEnabledKey, true)
    var aiDjPersona by rememberPreference(AiDjPersonaKey, DEFAULT_AI_DJ_PERSONA)
    var openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    var openRouterModel by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

    var showPersonaDialog by rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showModelDialog by rememberSaveable { mutableStateOf(false) }

    if (showPersonaDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_dj_persona)) },
            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
            initialTextFieldValue = TextFieldValue(text = aiDjPersona.ifBlank { DEFAULT_AI_DJ_PERSONA }),
            singleLine = true,
            isInputValid = { it.isNotBlank() },
            onDone = {
                aiDjPersona = it.trim().ifBlank { DEFAULT_AI_DJ_PERSONA }
                showPersonaDialog = false
            },
            onDismiss = { showPersonaDialog = false },
        )
    }

    if (showApiKeyDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_api_key)) },
            icon = { Icon(painterResource(R.drawable.key), null) },
            initialTextFieldValue = TextFieldValue(text = openRouterApiKey),
            singleLine = true,
            isInputValid = { true },
            onDone = {
                openRouterApiKey = it.trim()
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

    if (showModelDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_model)) },
            icon = { Icon(painterResource(R.drawable.discover_tune), null) },
            initialTextFieldValue = TextFieldValue(text = openRouterModel),
            singleLine = true,
            isInputValid = { it.isNotBlank() },
            onDone = {
                openRouterModel = it.trim()
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        Text(
            text = stringResource(R.string.ai_dj_settings_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.ai_dj),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.graphic_eq),
                        title = { Text(stringResource(R.string.ai_dj_talk_enabled)) },
                        description = { Text(stringResource(R.string.ai_dj_talk_enabled_desc)) },
                        trailingContent = {
                            Switch(
                                checked = aiDjTalkEnabled,
                                onCheckedChange = { aiDjTalkEnabled = it },
                                thumbContent = {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id = if (aiDjTalkEnabled) R.drawable.check else R.drawable.close,
                                            ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.edit),
                        title = { Text(stringResource(R.string.ai_dj_persona)) },
                        description = {
                            Text(aiDjPersona.ifBlank { DEFAULT_AI_DJ_PERSONA })
                        },
                        onClick = { showPersonaDialog = true },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.ai_setup_guide),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.key),
                        title = { Text(stringResource(R.string.ai_api_key)) },
                        description = {
                            Text(
                                if (openRouterApiKey.isNotEmpty()) {
                                    "•".repeat(minOf(openRouterApiKey.length, 8))
                                } else {
                                    stringResource(R.string.not_set)
                                },
                            )
                        },
                        onClick = { showApiKeyDialog = true },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.discover_tune),
                        title = { Text(stringResource(R.string.ai_model)) },
                        description = { Text(openRouterModel.ifBlank { stringResource(R.string.not_set) }) },
                        onClick = { showModelDialog = true },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.ai_dj)) },
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
        },
    )
}
