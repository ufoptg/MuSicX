@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.shared.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.artist_name
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.generated.resources.clear_artist_alias
import dev.musicx.shared.generated.resources.edit_artist
import dev.musicx.shared.generated.resources.save
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.settings.ArtistNameAliases
import dev.musicx.shared.settings.displayTitle
import dev.musicx.shared.ui.components.AlertDialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArtistNameAliasDialog(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    var name by remember(artist.id) { mutableStateOf(artist.displayTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_artist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.artist_name)) },
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        ArtistNameAliases.clear(artist.id, artist.channelId, artist.title)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.clear_artist_alias)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    ArtistNameAliases.set(artist.id, artist.channelId, artist.displayTitle, name.trim())
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.cancel)) } },
    )
}
