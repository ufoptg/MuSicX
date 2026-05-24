package com.metrolist.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.metrolist.music.R

class MusicWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Core initialization loop
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    /**
     * Call this dedicated interface method within Metrolist's background service/playback loop 
     * whenever track changes happen and fresh artwork is resolved.
     */
    fun updateWidgetMetadata(context: Context, title: String, artist: String, albumArt: Bitmap?) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, MusicWidgetReceiver::class.java)
        val views = RemoteViews(context.packageName, R.layout.music_widget)

        // Bind raw text parameters
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist_name, artist)

        if (albumArt != null) {
            views.setImageViewBitmap(R.id.widget_album_art, albumArt)

            // Extract structural colors asynchronously to prevent rendering pipeline lockups
            Palette.from(albumArt).generate { palette ->
                // Extract dominant spectrum with mid-gray fallback safety bounds
                val extractedColor = palette?.getDominantColor(0xFF888888.toInt()) ?: 0xFF888888.toInt()
                
                // Restructure hex structure down to 35% opacity saturation levels (89 out of 255)
                val glassTintedColor = ColorUtils.setAlphaComponent(extractedColor, 89)
                
                // Reflect color changes safely across process boundaries to the root container background
                views.setInt(
                    R.id.widget_root_container, 
                    "setBackgroundColor", 
                    glassTintedColor
                )
                
                // Commit viewport configuration delta records back onto launcher manager thread
                appWidgetManager.updateAppWidget(thisWidget, views)
            }
        } else {
            // Revert background layer states back to default neutral opacity matrix values
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_placeholder)
            views.setInt(R.id.widget_root_container, "setBackgroundColor", 0x33FFFFFF)
            appWidgetManager.updateAppWidget(thisWidget, views)
        }
    }
}
