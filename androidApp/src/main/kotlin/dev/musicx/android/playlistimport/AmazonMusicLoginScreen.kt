package dev.musicx.android.playlistimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.musicx.shared.amazonmusicimport.AmazonMusicImportManager
import dev.musicx.shared.amazonmusicimport.AmazonMusicMarketplacePicker
import dev.musicx.shared.generated.resources.Res
import dev.musicx.shared.generated.resources.amazon_music_login_failed
import dev.musicx.shared.generated.resources.import_connect_amazon_music_account

@Composable
@Suppress("FunctionName")
fun AmazonMusicLoginScreen(
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onSessionToken: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var marketplace by remember { mutableStateOf<String?>(null) }
    val host = marketplace
    if (host == null) {
        AmazonMusicMarketplacePicker(onSelect = { marketplace = it }, onDismiss = onNavigateBack)
        return
    }
    ProviderLoginShell(
        titleRes = Res.string.import_connect_amazon_music_account,
        loginErrorRes = Res.string.amazon_music_login_failed,
        cookieUrl = "https://$host",
        loginUrl = "https://$host",
        logTag = "AmazonMusicImport",
        credentialLogMessage = "Amazon Music WebView session cookie",
        extractCredentials = { AmazonMusicImportManager.extractSessionCookie(it) },
        credentialKey = { it },
        isAuthenticating = isAuthenticating,
        authenticationError = authenticationError,
        onClearAuthenticationError = onClearAuthenticationError,
        onCredentials = { onSessionToken(it, host) },
        onNavigateBack = onNavigateBack,
    )
}
