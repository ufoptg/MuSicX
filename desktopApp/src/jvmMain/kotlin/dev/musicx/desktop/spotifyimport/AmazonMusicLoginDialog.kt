package dev.musicx.desktop.spotifyimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.musicx.shared.amazonmusicimport.AmazonMusicImportManager
import dev.musicx.shared.amazonmusicimport.AmazonMusicMarketplacePicker
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.import_amazon_music_session_sign_in
import dev.musicx.shared.generated.resources.import_connect_amazon_music_account
import io.ktor.http.Url
import org.jetbrains.compose.resources.stringResource

@Composable
@Suppress("FunctionName")
internal fun AmazonMusicLoginDialog(
    onDismissRequest: () -> Unit,
    onSessionCookie: (String, String) -> Unit,
) {
    var marketplace by remember { mutableStateOf<String?>(null) }
    val host = marketplace
    if (host == null) {
        AmazonMusicMarketplacePicker(onSelect = { marketplace = it }, onDismiss = onDismissRequest)
        return
    }
    val musicUrl = "https://$host"
    ProviderLoginDialog(
        title = stringResource(Res.string.import_connect_amazon_music_account),
        loginUrl = musicUrl,
        signInHint = stringResource(Res.string.import_amazon_music_session_sign_in),
        onDismissRequest = onDismissRequest,
        extractCredentials = { state ->
            val url = state.lastLoadedUrl.orEmpty()
            if (runCatching { Url(url).host }.getOrNull() != host) {
                null
            } else {
                val cookies =
                    state.cookieManager.getCookies(musicUrl)
                val cookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                AmazonMusicImportManager.extractSessionCookie(cookieHeader)
            }
        },
        onAuthenticated = { onSessionCookie(it, host) },
    )
}
