package com.webtrit.callkeep.common

import android.os.Build
import android.os.Handler
import kotlin.math.pow

interface RetryDecider {
    fun shouldRetry(attempt: Int, error: Throwable, maxAttempts: Int): Boolean
}

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 750L,
    val backoffMultiplier: Double = 1.5,
    val maxDelayMs: Long = 5_000L,
)

class RetryManager<K>(
    private val handler: Handler, private val decider: RetryDecider
) {

    private data class State(
        var attempt: Int, var runnable: Runnable?
    )

    private val states = mutableMapOf<K, State>()

    /**
     * Runs [block] with retries keyed by [key]. On each attempt we invoke [onAttemptStart].
     * If [block] throws:
     *  - if [decider] says "retry", we schedule next attempt with backoff;
     *  - otherwise we call [onFinalFailure] and stop.
     *
     * Use [cancel] to stop future retries for [key].
     */
    fun run(
        key: K,
        config: RetryConfig,
        onAttemptStart: (attempt: Int) -> Unit = {},
        onSuccess: () -> Unit = {},
        onFinalFailure: (Throwable) -> Unit = {},
        block: (attempt: Int) -> Unit
    ) {
        cancel(key) // ensure clean slate

        fun scheduleNext(attempt: Int) {
            // Calculate delay before the next retry attempt using exponential backoff.
            //
            // Logic:
            // - Each retry waits longer than the previous one: delay = initialDelayMs * (backoffMultiplier ^ (attempt - 1))
            // - For example, with initialDelayMs=750 and backoffMultiplier=1.5:
            //     attempt 1 → 750ms
            //     attempt 2 → 1125ms
            //     attempt 3 → 1687ms
            // - The delay never exceeds maxDelayMs, ensuring we don't wait unreasonably long.
            val nextDelay =
                (config.initialDelayMs * config.backoffMultiplier.pow((attempt - 1).toDouble())).coerceAtMost(
                    config.maxDelayMs.toDouble()
                ).toLong()

            val r = Runnable {
                // Guard if canceled in the meantime
                val s = states[key] ?: return@Runnable
                val currentAttempt = s.attempt + 1
                s.attempt = currentAttempt
                onAttemptStart(currentAttempt)

                try {
                    // user operation; should throw on failure
                    // success — cleanup and notify
                    block(currentAttempt)
                    cancel(key)
                    onSuccess()
                } catch (t: Throwable) {
                    if (decider.shouldRetry(currentAttempt, t, config.maxAttempts)) {
                        // schedule another attempt
                        scheduleNext(currentAttempt + 1)
                    } else {
                        cancel(key)
                        onFinalFailure(t)
                    }
                }
            }

            // Save & post
            states[key] = State(attempt = attempt - 1, runnable = r)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                handler.postDelayed(r, key, nextDelay)
            } else {
                handler.postDelayed(r, nextDelay)
            }
        }

        // first attempt fires immediately (delay 0)
        val runnable = Runnable {
            val s = states[key] ?: return@Runnable
            val attempt = s.attempt + 1
            s.attempt = attempt
            onAttemptStart(attempt)

            try {
                block(attempt)
                cancel(key)
                onSuccess()
            } catch (t: Throwable) {
                if (decider.shouldRetry(attempt, t, config.maxAttempts)) {
                    scheduleNext(attempt + 1)
                } else {
                    cancel(key)
                    onFinalFailure(t)
                }
            }
        }

        states[key] = State(attempt = 0, runnable = runnable)
        handler.post(runnable)

    }

    fun cancel(key: K) {
        states.remove(key)?.runnable?.let { handler.removeCallbacks(it) }
    }

    fun clear() {
        states.values.forEach { st -> st.runnable?.let { handler.removeCallbacks(it) } }
        states.clear()
    }
}
