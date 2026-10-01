/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.dj

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import com.metrolist.music.R
import com.metrolist.music.playback.MusicService
import java.util.Locale

/**
 * Same system “Speak now” UI as player-menu Talk to DJ 6, launched after Hey DJ 6 wake.
 */
class DjCommandListenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.ai_dj_talk_command_prompt))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_SPEECH)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.ai_dj_voice_unavailable, Toast.LENGTH_SHORT).show()
            notifyCancelled()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SPEECH) {
            finish()
            return
        }
        if (resultCode == RESULT_OK) {
            val spoken =
                data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
            if (spoken.isNotBlank()) {
                val serviceIntent =
                    Intent(this, MusicService::class.java).apply {
                        action = MusicService.ACTION_DJ_SPOKEN_UTTERANCE
                        putExtra(MusicService.EXTRA_DJ_UTTERANCE, spoken)
                        // After wake, short commands like “skip” are OK without repeating DJ 6.
                        putExtra(MusicService.EXTRA_DJ_REQUIRE_WAKE, false)
                    }
                startService(serviceIntent)
            } else {
                Toast.makeText(this, R.string.ai_dj_command_not_understood, Toast.LENGTH_SHORT).show()
                notifyCancelled()
            }
        } else {
            notifyCancelled()
        }
        finish()
    }

    private fun notifyCancelled() {
        startService(
            Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_DJ_LISTEN_FINISHED
            },
        )
    }

    companion object {
        private const val REQ_SPEECH = 6106
    }
}
