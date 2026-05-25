package com.metrolist.music.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.EnableDiscordRPCKey
import com.metrolist.music.discord.DiscordRpcManager
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
    navController: NavController,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val enabled by rememberPreference(EnableDiscordRPCKey, true)
    var isBusy by remember { mutableStateOf(false) }
    val connectionStatus by DiscordRpcManager.connectionStatus.collectAsState()

    val statusText = when {
        connectionStatus == DiscordRpcManager.Status.Connected -> "Connected"
        connectionStatus == DiscordRpcManager.Status.Authorizing -> "Authorized - connecting..."
        !DiscordRpcManager.isInitialized() -> "Not initialized"
        else -> "Not connected"
    }

    if (!DiscordRpcManager.isInitialized()) {
        DiscordRpcManager.init()
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.discord),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.discord_connect_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        if (statusText.isNotEmpty()) {
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (DiscordRpcManager.isInitialized()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.enable),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { value ->
                        scope.launch {
                            context.dataStore.edit { it[EnableDiscordRPCKey] = value }
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (DiscordRpcManager.isAuthorized() || DiscordRpcManager.isReady()) {
            OutlinedButton(
                onClick = {
                    isBusy = true
                    scope.launch(Dispatchers.IO) {
                        DiscordRpcManager.disconnect()
                        withContext(Dispatchers.Main) {
                            isBusy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
            ) {
                Text(stringResource(R.string.disconnect))
            }
        } else if (!isBusy) {
            OutlinedButton(
                onClick = {
                    isBusy = true
                    DiscordRpcManager.authorize { success ->
                        isBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Authorize Discord")
            }
        }

        if (isBusy) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (DiscordRpcManager.isAuthorized()) "Connecting..." else "Opening browser...",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.integrations)) },
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
        windowInsets = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal),
    )
}
