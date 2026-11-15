package dev.musicx.shared.utils

import kotlin.time.Clock

object MuSicXClock {
    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
