@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.musicx.shared.amazonmusicimport

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.amazon_music_marketplace
import dev.musicx.shared.generated.resources.cancel
import dev.musicx.shared.playlistimport.AmazonMusicPlaylistImporter
import dev.musicx.shared.ui.components.AlertDialog
import dev.musicx.shared.ui.utils.DragScrollableColumn
import org.jetbrains.compose.resources.stringResource

@Composable
fun AmazonMusicMarketplacePicker(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.amazon_music_marketplace)) },
        text = {
            DragScrollableColumn {
                items(AmazonMusicPlaylistImporter.marketplaceHosts.toList()) { host ->
                    TextButton(
                        onClick = { onSelect(host) },
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(host) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.cancel)) } },
    )
}
