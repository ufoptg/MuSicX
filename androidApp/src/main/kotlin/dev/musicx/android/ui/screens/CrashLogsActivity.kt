package dev.musicx.android.ui.screens

import android.app.Activity
import android.os.Bundle
import dev.musicx.android.utils.CrashHandler

class CrashLogsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(CrashHandler.logsIntent(this))
        finish()
    }
}
