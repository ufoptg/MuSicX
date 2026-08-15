/**
 * MuSicX Project (C) 2026
 * Credits to Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.widget

/**
 * Dedicated widget provider for the Galaxy Z Flip cover screen (Flex Window).
 *
 * This is a separate provider from [MusicWidgetReceiver] so it can carry the
 * Samsung `com.samsung.android.appwidget.provider` metadata
 * (`display = "sub_screen"`) and `widgetCategory = "keyguard"` without
 * forcing the large cover-screen minimum size onto the home-screen Music
 * Player widget (which supports compact 4x1 / 2x2 layouts).
 *
 * Behaviour is inherited from [MusicWidgetReceiver]: button taps reuse the
 * same ACTION_PLAY_PAUSE / ACTION_NEXT / ACTION_PREVIOUS actions handled by
 * [com.metrolist.music.playback.MusicService], and updates are pushed by
 * [MetrolistWidgetManager].
 */
class CoverScreenWidgetReceiver : MusicWidgetReceiver()
