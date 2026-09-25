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
import com.metrolist.music.constants.AiDjListenCommandsKey
import com.metrolist.music.constants.AiDjPersonaKey
import com.metrolist.music.constants.AiDjTalkEnabledKey
import com.metrolist.music.constants.AiDjTtsModelKey
import com.metrolist.music.constants.AiDjTtsVoiceKey
import com.metrolist.music.constants.AiDjVoiceEngineKey
import com.metrolist.music.constants.DEFAULT_AI_DJ_PERSONA
import com.metrolist.music.constants.DEFAULT_AI_DJ_TTS_MODEL
import com.metrolist.music.constants.DEFAULT_AI_DJ_TTS_VOICE
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.OpenRouterModelKey
import com.metrolist.music.dj.DjFluxVoices
import com.metrolist.music.dj.DjHostTts
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjSettings(navController: NavController) {
    var aiDjTalkEnabled by rememberPreference(AiDjTalkEnabledKey, true)
    var aiDjListenCommands by rememberPreference(AiDjListenCommandsKey, false)
    var aiDjPersona by rememberPreference(AiDjPersonaKey, DEFAULT_AI_DJ_PERSONA)
    var voiceEngine by rememberPreference(AiDjVoiceEngineKey, "openrouter")
    var ttsModel by rememberPreference(AiDjTtsModelKey, DEFAULT_AI_DJ_TTS_MODEL)
    var ttsVoice by rememberPreference(AiDjTtsVoiceKey, DEFAULT_AI_DJ_TTS_VOICE)
    var openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    var openRouterModel by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

    // One-shot migrate away from old OpenAI TTS defaults.
    LaunchedEffect(Unit) {
        val normalizedModel = DjHostTts.normalizeTtsModel(ttsModel)
        val normalizedVoice = DjHostTts.normalizeTtsVoice(ttsVoice)
        if (normalizedModel != ttsModel) ttsModel = normalizedModel
        if (normalizedVoice != ttsVoice) ttsVoice = normalizedVoice
    }

    var showPersonaDialog by rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showModelDialog by rememberSaveable { mutableStateOf(false) }
    var showVoiceEngineDialog by rememberSaveable { mutableStateOf(false) }
    var showTtsModelDialog by rememberSaveable { mutableStateOf(false) }
    var showTtsVoiceDialog by rememberSaveable { mutableStateOf(false) }

    val voiceEngines = listOf("openrouter", "system")
    val ttsVoices = DjFluxVoices.all.map { it.id }

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
            title = { Text(stringResource(R.string.ai_dj_curation_model)) },
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

    if (showVoiceEngineDialog) {
        EnumDialog(
            onDismiss = { showVoiceEngineDialog = false },
            onSelect = {
                voiceEngine = it
                showVoiceEngineDialog = false
            },
            title = stringResource(R.string.ai_dj_voice_engine),
            current = voiceEngine,
            values = voiceEngines,
            valueText = {
                when (it) {
                    "openrouter" -> stringResource(R.string.ai_dj_voice_engine_openrouter)
                    else -> stringResource(R.string.ai_dj_voice_engine_system)
                }
            },
            valueDescription = {
                when (it) {
                    "openrouter" -> stringResource(R.string.ai_dj_voice_engine_openrouter_desc)
                    else -> stringResource(R.string.ai_dj_voice_engine_system_desc)
                }
            },
        )
    }

    if (showTtsModelDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_dj_tts_model)) },
            icon = { Icon(painterResource(R.drawable.discover_tune), null) },
            initialTextFieldValue = TextFieldValue(text = ttsModel.ifBlank { DEFAULT_AI_DJ_TTS_MODEL }),
            singleLine = true,
            isInputValid = { it.isNotBlank() },
            onDone = {
                ttsModel = it.trim().ifBlank { DEFAULT_AI_DJ_TTS_MODEL }
                showTtsModelDialog = false
            },
            onDismiss = { showTtsModelDialog = false },
        )
    }

    if (showTtsVoiceDialog) {
        EnumDialog(
            onDismiss = { showTtsVoiceDialog = false },
            onSelect = {
                ttsVoice = it
                showTtsVoiceDialog = false
            },
            title = stringResource(R.string.ai_dj_tts_voice),
            current = ttsVoice,
            values = ttsVoices,
            valueText = { DjFluxVoices.labelFor(it) },
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
                        icon = painterResource(R.drawable.mic),
                        title = { Text(stringResource(R.string.ai_dj_listen_commands)) },
                        description = { Text(stringResource(R.string.ai_dj_listen_commands_desc)) },
                        trailingContent = {
                            Switch(
                                checked = aiDjListenCommands,
                                onCheckedChange = { aiDjListenCommands = it },
                                thumbContent = {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id = if (aiDjListenCommands) R.drawable.check else R.drawable.close,
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
            title = stringResource(R.string.ai_dj_voice_section),
            items =
                buildList {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.graphic_eq),
                            title = { Text(stringResource(R.string.ai_dj_voice_engine)) },
                            description = {
                                Text(
                                    when (voiceEngine) {
                                        "openrouter" -> stringResource(R.string.ai_dj_voice_engine_openrouter)
                                        else -> stringResource(R.string.ai_dj_voice_engine_system)
                                    },
                                )
                            },
                            onClick = { showVoiceEngineDialog = true },
                        ),
                    )
                    if (voiceEngine == "openrouter") {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.discover_tune),
                                title = { Text(stringResource(R.string.ai_dj_tts_model)) },
                                description = {
                                    Text(
                                        ttsModel.ifBlank { DEFAULT_AI_DJ_TTS_MODEL }.let {
                                            "$it\n${stringResource(R.string.ai_dj_tts_model_hint)}"
                                        },
                                    )
                                },
                                onClick = { showTtsModelDialog = true },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.mic),
                                title = { Text(stringResource(R.string.ai_dj_tts_voice)) },
                                description = {
                                    Text(DjFluxVoices.labelFor(ttsVoice.ifBlank { DEFAULT_AI_DJ_TTS_VOICE }))
                                },
                                onClick = { showTtsVoiceDialog = true },
                            ),
                        )
                    }
                },
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
                        title = { Text(stringResource(R.string.ai_dj_curation_model)) },
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
