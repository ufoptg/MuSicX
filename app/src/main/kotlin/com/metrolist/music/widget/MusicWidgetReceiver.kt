/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.metrolist.music.playback.MusicService
import timber.log.Timber

class MusicWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Force an immediate refresh from the service if it's alive, 
        // otherwise visually update the static structure right away
        if (MusicService.isRunning) {
            triggerServiceUpdate(context)
        } else {
            val views = RemoteViews(context.packageName, getLayoutId(context))
            // Clear or reset UI text to default states so it doesn't look stuck on an old song
            updateWidgetContent(context, appWidgetManager, appWidgetIds, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (MusicService.isRunning) {
            triggerServiceUpdate(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        Timber.tag("MusicWidgetReceiver").d("Received broadcast intent action: $action")

        when (action) {
            // HIGH-PRIORITY REAL-TIME HOOKS: 
            // Intercept direct media framework metadata intents to update UI elements instantly
            "com.metrolist.music.METADATA_CHANGED",
            "com.metrolist.music.PLAYBACK_STATE_CHANGED",
            Intent.ACTION_MEDIA_BUTTON -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, MusicWidgetReceiver::class.java))
                
                val views = RemoteViews(context.packageName, getLayoutId(context))
                
                // Extract track metadata passed directly inside the intent payloads
                val title = intent.getStringExtra("EXTRA_TITLE") ?: "Not Playing"
                val artist = intent.getStringExtra("EXTRA_ARTIST") ?: "Metrolist Media Player"
                val isPlaying = intent.getBooleanExtra("EXTRA_IS_PLAYING", false)

                // Dynamic Resource Mapping ID Lookups (Safely mapped to Metrolist's XML layouts)
                val titleTextViewId = context.resources.getIdentifier("widget_title", "id", context.packageName)
                val artistTextViewId = context.resources.getIdentifier("widget_artist", "id", context.packageName)
                val playButtonId = context.resources.getIdentifier("btn_widget_play", "id", context.packageName)

                if (titleTextViewId != 0) views.setTextViewText(titleTextViewId, title)
                if (artistTextViewId != 0) views.setTextViewText(artistTextViewId, artist)
                
                // Swap play/pause icons in real-time dynamically based on playing state
                if (playButtonId != 0) {
                    val iconRes = if (isPlaying) {
                        context.resources.getIdentifier("ic_widget_pause", "drawable", context.packageName)
                    } else {
                        context.resources.getIdentifier("ic_widget_play", "drawable", context.packageName)
                    }
                    if (iconRes != 0) views.setImageViewResource(playButtonId, iconRes)
                }

                // Push intent bindings back onto UI buttons so user input still fires 
                setupButtonPendingIntents(context, views)
                
                // Blast the updated view to the home screen instantly bypassing the Service completely
                updateWidgetContent(context, appWidgetManager, appWidgetIds, views)
            }

            // Route standard user UI touch interactions straight into the main media container
            ACTION_PLAY_PAUSE, ACTION_LIKE, ACTION_NEXT, ACTION_PREVIOUS -> {
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    this.action = intent.action
                    putExtras(intent)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Timber.tag("MusicWidgetReceiver").e(e, "Failed routing button intent payload to MusicService")
                }
            }
        }
    }

    private fun triggerServiceUpdate(context: Context) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_UPDATE_WIDGET
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // Suppress fallback exceptions elegantly
        }
    }

    private fun getLayoutId(context: Context): Int {
        val layoutId = context.resources.getIdentifier("music_widget_layout", "layout", context.packageName)
        return if (layoutId != 0) layoutId else 0 // Fallback to your primary layout resource reference
    }

    private fun setupButtonPendingIntents(context: Context, views: RemoteViews) {
        // Helper block mapping your classic action keys to your PendingIntents 
        // to make sure your touch targets don't freeze up during metadata updates.
        val actions = listOf(ACTION_PLAY_PAUSE, ACTION_LIKE, ACTION_NEXT, ACTION_PREVIOUS)
        actions.forEach { actionString ->
            val buttonResId = when(actionString) {
                ACTION_PLAY_PAUSE -> context.resources.getIdentifier("btn_widget_play", "id", context.packageName)
                ACTION_LIKE -> context.resources.getIdentifier("btn_widget_like", "id", context.packageName)
                ACTION_NEXT -> context.resources.getIdentifier("btn_widget_next", "id", context.packageName)
                ACTION_PREVIOUS -> context.resources.getIdentifier("btn_widget_prev", "id", context.packageName)
                else -> 0
            }
            if (buttonResId != 0) {
                val btnIntent = Intent(context, MusicWidgetReceiver::class.java).apply { action = actionString }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context, 
                    buttonResId, 
                    btnIntent, 
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(buttonResId, pendingIntent)
            }
        }
    }

    private fun updateWidgetContent(context: Context, manager: AppWidgetManager, ids: IntArray, views: RemoteViews) {
        ids.forEach { id ->
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.metrolist.music.widget.PLAY_PAUSE"
        const val ACTION_LIKE = "com.metrolist.music.widget.LIKE"
        const val ACTION_NEXT = "com.metrolist.music.widget.NEXT"
        const val ACTION_PREVIOUS = "com.metrolist.music.widget.PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "com.metrolist.music.widget.UPDATE_WIDGET"
    }
}
