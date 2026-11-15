package dev.musicx.shared.ui.menu

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.hidden_from_home
import dev.musicx.shared.generated.resources.undo
import dev.musicx.shared.settings.HiddenHomeItems
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.getString

@Composable
fun HiddenHomeItemsSnackbar(snackbarHostState: SnackbarHostState) {
    LaunchedEffect(snackbarHostState) {
        HiddenHomeItems.hidden.collectLatest { item ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                snackbarHostState.showSnackbar(
                    message = getString(Res.string.hidden_from_home, item.title),
                    actionLabel = getString(Res.string.undo),
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) HiddenHomeItems.restore(item.key)
        }
    }
}
