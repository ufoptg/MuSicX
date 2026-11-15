@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.shared.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.artist
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.edit_song
import dev.musicx.shared.generated.resources.error_unknown
import dev.musicx.shared.generated.resources.reset
import dev.musicx.shared.generated.resources.save
import dev.musicx.shared.generated.resources.title
import dev.musicx.shared.ui.components.AlertDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditSongDialog(
    title: String,
    artist: String,
    onSave: suspend (String, String) -> Unit,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    onReset: (suspend () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val save by rememberUpdatedState(onSave)
    val saved by rememberUpdatedState(onSaved)
    val dismiss by rememberUpdatedState(onDismiss)
    val reset by rememberUpdatedState(onReset)
    val editor =
        remember {
            SongEditor(
                scope,
                title,
                artist,
                { title, artist -> save(title, artist) },
                { saved() },
                { dismiss() },
                { reset?.invoke() },
            )
        }

    AlertDialog(
        onDismissRequest = editor::dismiss,
        title = { Text(stringResource(Res.string.edit_song)) },
        text = {
            Column {
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = { editor.title = it },
                    label = { Text(stringResource(Res.string.title)) },
                    singleLine = true,
                    enabled = !editor.busy,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.artist,
                    onValueChange = { editor.artist = it },
                    label = { Text(stringResource(Res.string.artist)) },
                    singleLine = true,
                    enabled = !editor.busy,
                )
                if (editor.failed) {
                    Text(stringResource(Res.string.error_unknown), color = MaterialTheme.colorScheme.error)
                }
                if (onReset != null) {
                    TextButton(enabled = !editor.busy, onClick = editor::reset, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !editor.busy && editor.title.isNotBlank() && editor.artist.isNotBlank(),
                onClick = editor::confirm,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(enabled = !editor.busy, onClick = editor::dismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
