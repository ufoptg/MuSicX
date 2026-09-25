/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import com.metrolist.music.R
import com.metrolist.music.constants.AiDjListenCommandsKey
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.dj.DjStartRequest
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.DjQueue
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun DjStartDialog(
    show: Boolean,
    fallbackSeed: MediaMetadata?,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    onStarted: () -> Unit = {},
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }

    val micPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (pendingStart) {
                pendingStart = false
                doStart(context, scope, request, fallbackSeed, playerConnection, onDismiss, onStarted) {
                    loading = it
                }
            }
        }

    val speechLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val spoken =
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
            if (spoken.isNotBlank()) request = spoken
        }

    fun startVoiceInput() {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.ai_dj_voice_prompt))
            }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.ai_dj_voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmStart() {
        if (context.dataStore.get(OpenRouterApiKey, "").isBlank()) {
            Toast.makeText(context, R.string.ai_dj_api_key_required, Toast.LENGTH_LONG).show()
            return
        }
        val wantsListen = context.dataStore.get(AiDjListenCommandsKey, false)
        val hasMic =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (wantsListen && !hasMic) {
            pendingStart = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        doStart(context, scope, request, fallbackSeed, playerConnection, onDismiss, onStarted) {
            loading = it
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.ai_dj_start_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ai_dj_start_desc))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = request,
                        onValueChange = { request = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !loading,
                        placeholder = { Text(stringResource(R.string.ai_dj_start_hint)) },
                    )
                    IconButton(
                        onClick = { startVoiceInput() },
                        enabled = !loading,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.mic),
                            contentDescription = stringResource(R.string.ai_dj_voice_prompt),
                        )
                    }
                }
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirmStart() },
                enabled = !loading,
            ) {
                Text(stringResource(R.string.ai_dj_start))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !loading,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun doStart(
    context: android.content.Context,
    scope: CoroutineScope,
    request: String,
    fallbackSeed: MediaMetadata?,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    onStarted: () -> Unit,
    setLoading: (Boolean) -> Unit,
) {
    setLoading(true)
    scope.launch {
        val seed =
            withContext(Dispatchers.IO) {
                DjStartRequest.resolveSeed(context, request, fallbackSeed)
            }
        setLoading(false)
        if (seed == null) {
            Toast.makeText(context, R.string.ai_dj_need_song, Toast.LENGTH_SHORT).show()
            return@launch
        }
        Toast.makeText(context, R.string.ai_dj_starting, Toast.LENGTH_SHORT).show()
        playerConnection.playQueue(
            DjQueue.fromSeed(
                context = context,
                seed = seed,
                userRequest = request.takeIf { it.isNotBlank() },
            ),
        )
        onDismiss()
        onStarted()
    }
}
