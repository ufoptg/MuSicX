package com.metrolist.music.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** One cancellable countdown shared by the settings and the player overlay. */
internal class SleepTimer(
    private val scope: CoroutineScope,
    private val minuteMillis: Long = 60_000L,
    private val onExpire: suspend () -> Unit,
) {
    private var job: Job? = null
    var mode: Int = 0
        private set

    fun start(option: Int) {
        require(option in 0..5)
        job?.cancel()
        job = null
        mode = option
        if (option in 1..4) {
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    delay(option * 15L * minuteMillis)
                    onExpire()
                } finally {
                    if (job === currentCoroutineContext().job) {
                        job = null
                        mode = 0
                    }
                }
            }
        }
    }

    fun cancel() = start(0)

    fun consumeEndOfTrack(): Boolean {
        if (mode != 5) return false
        cancel()
        return true
    }
}
