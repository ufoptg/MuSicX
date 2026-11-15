/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package dev.musicx.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.musicx.android.App
import dev.musicx.android.playback.MusicService
import dev.musicx.android.utils.launchAsync
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class TurntableWidgetReceiver :
    AppWidgetProvider(),
    KoinComponent {
    private val widgetManager: MuSicXWidgetManager by inject()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        requestWidgetRefresh(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        requestWidgetRefresh(context, intArrayOf(appWidgetId))
    }

    private fun requestWidgetRefresh(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        launchAsync {
            try {
                (context.applicationContext as App).startup.await()
                if (MusicService.isRunning) {
                    requestServiceSnapshot(context)
                } else {
                    widgetManager.updateIdleTurntableWidgets(appWidgetIds)
                    if (MusicService.isRunning) requestServiceSnapshot(context)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Failed to refresh turntable widgets")
            }
        }
    }

    private fun requestServiceSnapshot(context: Context) {
        context.startService(
            Intent(context, MusicService::class.java).setAction(MusicWidgetReceiver.ACTION_UPDATE_WIDGET),
        )
    }

    companion object {
        const val ACTION_TURNTABLE_PLAY_PAUSE = "dev.musicx.music.widget.TURNTABLE_PLAY_PAUSE"
        const val ACTION_TURNTABLE_NEXT = "dev.musicx.music.widget.TURNTABLE_NEXT"
        const val ACTION_TURNTABLE_PREVIOUS = "dev.musicx.music.widget.TURNTABLE_PREVIOUS"
        const val ACTION_UPDATE_TURNTABLE_WIDGET = "dev.musicx.music.widget.UPDATE_TURNTABLE_WIDGET"
    }
}
