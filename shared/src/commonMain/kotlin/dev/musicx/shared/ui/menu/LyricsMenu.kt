@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.shared.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.musicx.shared.api.lyrics.LyricsUtils
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.ai_translation_for_track
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.copied
import dev.musicx.shared.generated.resources.copy
import dev.musicx.shared.generated.resources.edit
import dev.musicx.shared.generated.resources.edit_lyrics
import dev.musicx.shared.generated.resources.lyrics_offset
import dev.musicx.shared.generated.resources.lyrics_romanize_title
import dev.musicx.shared.generated.resources.lyrics_show_translations
import dev.musicx.shared.generated.resources.no_provider_results_found
import dev.musicx.shared.generated.resources.offset_ms
import dev.musicx.shared.generated.resources.refetch
import dev.musicx.shared.generated.resources.save
import dev.musicx.shared.generated.resources.sources
import dev.musicx.shared.model.LyricsProviderResult
import dev.musicx.shared.model.SongItem
import dev.musicx.shared.model.previewAspectRatio
import dev.musicx.shared.model.previewThumbnailUrl
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.settings.displayName
import dev.musicx.shared.ui.components.ExpressiveSwitch
import dev.musicx.shared.ui.components.Material3MenuGroup
import dev.musicx.shared.ui.components.Material3MenuItemData
import dev.musicx.shared.ui.components.ModalBottomSheet
import dev.musicx.shared.ui.components.PlayerMenuAction
import dev.musicx.shared.ui.components.PlayerMenuPrimaryActions
import dev.musicx.shared.ui.components.PopupVisibility
import dev.musicx.shared.ui.components.lyrics.LyricsEditorScreen
import dev.musicx.shared.ui.icons.MuSicXIcons
import dev.musicx.shared.ui.theme.ComponentShapes
import dev.musicx.shared.ui.utils.setText
import dev.musicx.shared.ui.utils.verticalScrollWithDrag
import dev.musicx.shared.viewmodels.LyricsUiState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenu(
    song: SongItem,
    lyricsUiState: LyricsUiState,
    onDismiss: () -> Unit,
    onRefetch: (customTitle: String?, customArtist: String?) -> Unit,
    onLoadProviders: (customTitle: String?, customArtist: String?) -> Unit,
    onSelectProvider: (LyricsProviderResult) -> Unit,
    onShowLyricsOffset: () -> Unit,
    onLyricsChanged: () -> Unit,
    onManualAiTranslationChange: ((Boolean) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var editorSong by remember { mutableStateOf(song.id to song.title) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    val queryTitle = lyricsUiState.queryTitle ?: song.title
    val queryArtist = lyricsUiState.queryArtist ?: song.artists.joinToString { it.name }
    var editText by remember(lyricsUiState.rawLyrics) { mutableStateOf(TextFieldValue(lyricsUiState.rawLyrics.orEmpty())) }
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMessage = stringResource(Res.string.copied)
    var romanizationEnabled by remember { mutableStateOf(Settings.shared.lyricsRomanizationEnabled) }
    var manualAiTranslationChecked by remember(lyricsUiState.manualAiTranslationEnabled) {
        mutableStateOf(lyricsUiState.manualAiTranslationEnabled)
    }

    PopupVisibility(visible = showEditDialog) {
        LyricsEditorScreen(
            songId = editorSong.first,
            songTitle = editorSong.second,
            rawLyrics = editText.text,
            onChanged = onLyricsChanged,
            onDismiss = {
                showEditDialog = false
                onDismiss()
            },
        )
    }

    PopupVisibility(visible = showSearchDialog) {
        LyricsSearchDialog(
            initialTitle = queryTitle,
            initialArtist = queryArtist,
            onDismiss = { showSearchDialog = false },
            onSearch = { title, artist ->
                onLoadProviders(title, artist)
                showSearchDialog = false
                showProviderDialog = true
            },
        )
    }

    PopupVisibility(visible = showProviderDialog) {
        LyricsProviderDialog(
            search = lyricsUiState.providerSearch,
            noResultsText = stringResource(Res.string.no_provider_results_found),
            onDismiss = { showProviderDialog = false },
            onSelect = { result ->
                onSelectProvider(result)
                showProviderDialog = false
                onDismiss()
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ComponentShapes.BottomSheetShape,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScrollWithDrag(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExpressiveMediaMenuHeader(
                title = song.title,
                subtitle = song.artists.joinToString { it.displayName },
                thumbnail = song.previewThumbnailUrl,
                thumbnailAspectRatio = song.previewAspectRatio,
            )

            PlayerMenuPrimaryActions(
                actions =
                    listOf(
                        PlayerMenuAction(
                            icon = MuSicXIcons.Outlined.Edit,
                            title = stringResource(Res.string.edit),
                            onClick = {
                                editText = TextFieldValue(LyricsUtils.lyricsForEditing(lyricsUiState.rawLyrics, lyricsUiState.lyrics))
                                editorSong = song.id to song.title
                                showEditDialog = true
                            },
                        ),
                        PlayerMenuAction(
                            icon = MuSicXIcons.Outlined.Cached,
                            title = stringResource(Res.string.refetch),
                            onClick = {
                                onRefetch(queryTitle, queryArtist)
                                onDismiss()
                            },
                        ),
                        PlayerMenuAction(
                            icon = MuSicXIcons.Outlined.Search,
                            title = stringResource(Res.string.sources),
                            onClick = {
                                showSearchDialog = true
                            },
                        ),
                    ),
            )

            Material3MenuGroup(
                items =
                    listOfNotNull(
                        if (Settings.shared.lyricsAiTranslationEnabled) {
                            Material3MenuItemData(
                                title = { Text(stringResource(Res.string.ai_translation_for_track)) },
                                icon = {
                                    Icon(
                                        imageVector = MuSicXIcons.Outlined.Translate,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    manualAiTranslationChecked = !manualAiTranslationChecked
                                    onManualAiTranslationChange?.invoke(manualAiTranslationChecked)
                                },
                                trailingContent = {
                                    ExpressiveSwitch(
                                        checked = manualAiTranslationChecked,
                                        onCheckedChange = {
                                            manualAiTranslationChecked = it
                                            onManualAiTranslationChange?.invoke(it)
                                        },
                                    )
                                },
                            )
                        } else {
                            null
                        },
                        if (lyricsUiState.lyrics.any {
                                !it.translatedText.isNullOrBlank() || !it.backgroundTranslatedText.isNullOrBlank()
                            }
                        ) {
                            Material3MenuItemData(
                                title = { Text(stringResource(Res.string.lyrics_show_translations)) },
                                icon = {
                                    Icon(
                                        imageVector = MuSicXIcons.Outlined.Translate,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    Settings.shared.lyricsShowTranslations = !Settings.shared.lyricsShowTranslations
                                },
                                trailingContent = {
                                    ExpressiveSwitch(
                                        checked = Settings.shared.lyricsShowTranslations,
                                        onCheckedChange = { Settings.shared.lyricsShowTranslations = it },
                                    )
                                },
                            )
                        } else {
                            null
                        },
                        Material3MenuItemData(
                            title = { Text(stringResource(Res.string.lyrics_romanize_title)) },
                            icon = {
                                Icon(
                                    imageVector = MuSicXIcons.Outlined.Translate,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                romanizationEnabled = !romanizationEnabled
                                Settings.shared.lyricsRomanizationEnabled = romanizationEnabled
                            },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = romanizationEnabled,
                                    onCheckedChange = {
                                        romanizationEnabled = it
                                        Settings.shared.lyricsRomanizationEnabled = it
                                    },
                                )
                            },
                        ),
                        if (lyricsUiState.lyrics.any { it.time > 0L }) {
                            Material3MenuItemData(
                                title = { Text(stringResource(Res.string.lyrics_offset)) },
                                description = {
                                    val sign = if (lyricsUiState.timingOffsetMs > 0) "+" else ""
                                    Text(sign + stringResource(Res.string.offset_ms, lyricsUiState.timingOffsetMs))
                                },
                                icon = {
                                    Icon(
                                        imageVector = MuSicXIcons.Outlined.FastForward,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    onShowLyricsOffset()
                                },
                            )
                        } else {
                            null
                        },
                        Material3MenuItemData(
                            title = { Text(stringResource(Res.string.copy)) },
                            description = {
                                Text(
                                    lyricsUiState.lyrics
                                        .firstOrNull()
                                        ?.text
                                        .orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = MuSicXIcons.Outlined.ContentCopy,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                val textToCopy =
                                    LyricsUtils.toPlainText(lyricsUiState.lyrics)
                                coroutineScope.launch {
                                    clipboard.setText(textToCopy)
                                    snackbarHostState.showSnackbar(copiedMessage)
                                }
                            },
                        ),
                    ),
            )

            SnackbarHost(snackbarHostState)
            Spacer(Modifier.height(24.dp))
        }
    }
}
