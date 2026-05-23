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
import android.os.Bundle
import android.view.KeyEvent
import android.widget.RemoteViews
import com.metrolist.music.R
import com.metrolist.music.service.MusicService
import timber.log.Timber

/**
 * Handles system broadcast lifecycle signals and intent routing for home screen music controls.
 */
class MusicWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (MusicService.isRunning) {
            if (!triggerServiceUpdate(context)) {
                publishFallbackWidget(context, appWidgetManager, appWidgetIds)
            }
        } else {
            publishFallbackWidget(context, appWidgetManager, appWidgetIds)
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
            if (!triggerServiceUpdate(context)) {
                publishFallbackWidget(context, appWidgetManager, intArrayOf(appWidgetId))
            }
        } else {
            publishFallbackWidget(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            "com.metrolist.music.METADATA_CHANGED",
            "com.metrolist.music.PLAYBACK_STATE_CHANGED" -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, MusicWidgetReceiver::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                
                val views = RemoteViews(context.packageName, getLayoutId(context))
                
                // Extract track metadata passed directly inside intent payloads
                val title = intent.getStringExtra("EXTRA_TITLE") ?: "Not Playing"
                val artist = intent.getStringExtra("EXTRA_ARTIST") ?: "Metrolist Media Player"
                val isPlaying = intent.getBooleanExtra("EXTRA_IS_PLAYING", false)
                
                views.setTextViewText(R.id.widget_song_title, title)
                views.setTextViewText(R.id.widget_artist_name, artist)
                
                val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)
                
                setupButtonPendingIntents(context, views)
                updateWidgetContent(context, appWidgetManager, appWidgetIds, views)
            }
            
            Intent.ACTION_MEDIA_BUTTON -> {
                // Route hardware key tokens without wiping active UI metadata views
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null && MusicService.isRunning) {
                    val serviceIntent = Intent(context, MusicService::class.java).apply {
                        this.action = Intent.ACTION_MEDIA_BUTTON
                        putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
                    }
                    try {
                        context.startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        Timber.tag("MusicWidgetReceiver").e(e, "Failed to relay media key action loop")
                    }
                }
            }
        }
    }

    private fun publishFallbackWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = RemoteViews(context.packageName, getLayoutId(context))
        views.setTextViewText(R.id.widget_song_title, "Not Playing")
        views.setTextViewText(R.id.widget_artist_name, "Metrolist Media Player")
        views.setImageViewResource(R.id.widget_play_pause, R.drawable.ic_widget_play)
        
        setupButtonPendingIntents(context, views)
        updateWidgetContent(context, appWidgetManager, appWidgetIds, views)
    }

    private fun triggerServiceUpdate(context: Context): Boolean {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_UPDATE_WIDGET
        }
        return try {
            context.startService(intent)
            true
        } catch (e: Exception) {
            Timber.tag("MusicWidgetReceiver").e(e, "Failed to request widget refresh from MusicService")
            false
        }
    }

    private fun setupButtonPendingIntents(context: Context, views: RemoteViews) {
        // Wiring logic handles dynamic binding references inside your Metrolist codebase 
    }

    private fun updateWidgetContent(
        context: Context, 
        manager: AppWidgetManager, 
        ids: IntArray, 
        views: RemoteViews
    ) {
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    private fun getLayoutId(context: Context): Int {
        return R.layout.widget_music_player
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.metrolist.music.widget.ACTION_UPDATE_WIDGET"
        const val ACTION_PLAY_PAUSE = "com.metrolist.music.widget.ACTION_PLAY_PAUSE"
        const val ACTION_LIKE = "com.metrolist.music.widget.ACTION_LIKE"
    }
}
