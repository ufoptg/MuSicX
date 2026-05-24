package com.metrolist.music.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Retries [block] up to [maxAttempts] times when it returns a [Result.failure],
 * with exponential backoff between attempts. Returns the final [Result] —
 * either the first success or the last failure.
 *
 * Cancellation: if a returned failure wraps a [CancellationException] (e.g.
 * the wrapped operation used `runCatching` which swallowed the cancellation),
 * the failure is returned immediately rather than retried. The [delay] call
 * between attempts is itself a suspension point and will throw
 * [CancellationException] on scope cancellation; that throw propagates out
 * of this helper normally.
 *
 * With defaults (maxAttempts=3, initialDelayMs=1000, factor=4.0) the
 * inter-attempt delays are 1 s, 4 s — total worst-case backoff of 5 s
 * plus up to 3× the cost of [block].
 */
suspend fun <T> withJobRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000,
    factor: Double = 4.0,
    block: suspend () -> Result<T>,
): Result<T> {
    var delayMs = initialDelayMs
    var result = block()
    var attempts = 1
    while (result.isFailure && attempts < maxAttempts) {
        if (result.exceptionOrNull() is CancellationException) return result
        delay(delayMs)
        delayMs = (delayMs * factor).toLong()
        result = block()
        attempts++
    }
    return result
}
