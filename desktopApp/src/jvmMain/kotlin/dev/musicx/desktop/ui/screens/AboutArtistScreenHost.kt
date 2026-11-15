package dev.musicx.desktop.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.musicx.desktop.state.app.AppState
import dev.musicx.desktop.state.content.ArtistState
import dev.musicx.desktop.utils.saveArtistPhoto
import dev.musicx.shared.model.ArtistItem
import dev.musicx.shared.settings.Settings
import dev.musicx.shared.ui.navigation.LocalNavigator
import dev.musicx.shared.ui.screens.AboutArtistContent
import dev.musicx.shared.ui.screens.ArtistPhotoActions
import dev.musicx.shared.viewmodels.AboutArtistViewModel
import dev.musicx.shared.viewmodels.artistCatalogTitles
import kotlinx.coroutines.launch

@Composable
fun AboutArtistScreenHost(
    artistId: String,
    artistName: String,
    initialInfo: ArtistItem? = null,
) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val viewModel = remember(artistId, artistName) { AboutArtistViewModel(artistId, artistName, scope) }
    val uiState by viewModel.uiState.collectAsState()
    val catalogTitles = remember(ArtistState.artistData) { artistCatalogTitles(ArtistState.artistData) }
    LaunchedEffect(viewModel, catalogTitles, ArtistState.isArtistLoading, Settings.shared.lastResolvedContentLanguage) {
        if (!ArtistState.isArtistLoading) viewModel.load(catalogTitles)
    }
    val photoActions =
        remember(artistName) {
            ArtistPhotoActions(
                share = { AppState.shareLink(it, artistName) },
                save = { saveArtistPhoto(it, artistName) },
                showMessage = { message -> AppState.scope.launch { AppState.snackbarHostState.showSnackbar(message) } },
            )
        }
    AboutArtistContent(
        artistName = artistName,
        info = ArtistState.artistData["header"]?.firstOrNull() as? ArtistItem ?: initialInfo,
        uiState = uiState,
        photoActions = photoActions,
        onBackClick = { navigator.goBack() },
    )
}
