package dev.musicx.android.utils

import android.app.Application
import dev.musicx.shared.settings.Settings

object SentryManager {
    fun initialize(application: Application) = Unit

    fun setEnabled(enabled: Boolean) {
        Settings.shared.sentryEnabled = enabled
    }
}
