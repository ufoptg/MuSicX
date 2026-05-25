package com.metrolist.music.ui.screens.settings.integrations

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.DiscordAvatarKey
import com.metrolist.music.constants.DiscordInfoDismissedKey
import com.metrolist.music.constants.DiscordNameKey
import com.metrolist.music.constants.DiscordUsernameKey
import com.metrolist.music.constants.EnableDiscordRPCKey
import com.metrolist.music.db.entities.Song
import com.metrolist.music.discord.DiscordRpcManager
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscordSettings(
    navController: NavController,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val song by playerConnection.currentSong.collectAsState(null)
    val playbackState by playerConnection.playbackState.collectAsState()

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var discordAvatar by rememberPreference(DiscordAvatarKey, "")
    var infoDismissed by rememberPreference(DiscordInfoDismissedKey, false)

    val (discordRPC, onDiscordRPCChange) = rememberPreference(EnableDiscordRPCKey, true)

    val isLoggedIn = remember(discordName) { discordName.isNotEmpty() }
    var isBusy by remember { mutableStateOf(false) }

    val connectionStatus by DiscordRpcManager.connectionStatus.collectAsState()

    val statusText = when {
        connectionStatus == DiscordRpcManager.Status.Connected -> "Connected"
        connectionStatus == DiscordRpcManager.Status.Authorizing -> "Authorizing..."
        !DiscordRpcManager.isInitialized() -> "Not initialized"
        DiscordRpcManager.isAuthorized() -> "Authorized"
        else -> ""
    }

    if (!DiscordRpcManager.isInitialized()) {
        DiscordRpcManager.init()
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
            }
        }
    }

    Column(
        modifier =
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

        AnimatedVisibility(visible = !infoDismissed) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.discord_information_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { infoDismissed = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            }
        }

        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = if (isLoggedIn) 20.dp else 8.dp,
                        ).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(56.dp)) {
                    if (isLoggedIn && discordAvatar.isNotEmpty()) {
                        AsyncImage(
                            model = discordAvatar,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.discord),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .align(Alignment.Center)
                                    .alpha(0.4f),
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (isLoggedIn) {
                                discordName
                            } else {
                                stringResource(R.string.not_logged_in)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f),
                    )
                    if (discordUsername.isNotEmpty()) {
                        Text(
                            text = "@$discordUsername",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isLoggedIn) {
                        Text(
                            text = stringResource(R.string.discord_connect_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isLoggedIn) {
                    OutlinedButton(onClick = {
                        discordName = ""
                        discordUsername = ""
                        discordAvatar = ""
                        coroutineScope.launch(Dispatchers.IO) {
                            DiscordRpcManager.disconnect()
                        }
                    }) {
                        Text(stringResource(R.string.action_logout))
                    }
                }
            }

            if (!isLoggedIn) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!isBusy) {
                        OutlinedButton(
                            onClick = {
                                isBusy = true
                                DiscordRpcManager.authorize { success ->
                                    isBusy = false
                                    if (success) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val token = DiscordRpcManager.getAccessToken()
                                            if (token != null) {
                                                val user = DiscordRpcManager.fetchCurrentUser(token)
                                                if (user != null) {
                                                    withContext(Dispatchers.Main) {
                                                        discordUsername = user.username
                                                        discordName = user.name
                                                        discordAvatar = user.avatar ?: ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.action_login))
                        }
                    }
                }
            }

            if (isBusy) {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        Material3SettingsGroup(
            title = stringResource(R.string.options),
            items =
                listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.enable_discord_rpc)) },
                        trailingContent = {
                            Switch(
                                checked = discordRPC,
                                onCheckedChange = onDiscordRPCChange,
                                enabled = isLoggedIn,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (discordRPC) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            )
                        },
                        enabled = isLoggedIn,
                        onClick = { if (isLoggedIn) onDiscordRPCChange(!discordRPC) },
                    ),
                ),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.discord_rpc_preview),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
        )

        RichPresence(
            song = song,
            currentPlaybackTimeMillis = position,
        )

        Spacer(Modifier.height(24.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.discord_integration)) },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RichPresence(
    song: Song?,
    currentPlaybackTimeMillis: Long = 0L,
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val previewSongTitle = song?.song?.title ?: "Song Title"
            val previewArtistName = song?.artists?.joinToString { it.name } ?: "Artist"

            Text(
                text = "Listening to $previewArtistName",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(108.dp)) {
                    AsyncImage(
                        model = song?.song?.thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .align(Alignment.TopStart)
                                .run {
                                    if (song == null) {
                                        border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            RoundedCornerShape(3.dp),
                                        )
                                    } else {
                                        this
                                    }
                                },
                    )

                    song?.artists?.firstOrNull()?.thumbnailUrl?.let {
                        Box(
                            modifier =
                                Modifier
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        CircleShape,
                                    ).padding(2.dp)
                                    .align(Alignment.BottomEnd),
                        ) {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = previewSongTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = previewArtistName,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (song != null) {
                        SongProgressBar(
                            currentTimeMillis = currentPlaybackTimeMillis,
                            durationMillis = song.song.duration.times(1000L),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                enabled = song != null,
                onClick = {
                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://music.youtube.com/watch?v=${song?.id}".toUri(),
                        )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Listen on YouTube Music")
            }

            OutlinedButton(
                onClick = {
                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/MetrolistGroup/Metrolist".toUri(),
                        )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Visit Metrolist")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongProgressBar(
    currentTimeMillis: Long,
    durationMillis: Long,
) {
    val progress = if (durationMillis > 0) currentTimeMillis.toFloat() / durationMillis else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))

        LinearWavyProgressIndicator(
            progress = { progress },
            amplitude = { 1f },
            wavelength = 16.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = makeTimeString(currentTimeMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontSize = 12.sp,
            )
            Text(
                text = makeTimeString(durationMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontSize = 12.sp,
            )
        }
    }
}
