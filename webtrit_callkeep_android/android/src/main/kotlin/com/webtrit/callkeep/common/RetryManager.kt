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

        /**
         * Schedules the next retry attempt with an exponential backoff delay.
         * This function calculates the delay and posts the [nextAttemptRunnable] to the handler.
         */
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

            // Runnable that encapsulates the logic for the *next* retry attempt after a delay.
            // It delegates the core logic to performAttemptInternal and passes a lambda to schedule the next retry.
            val nextAttemptRunnable = Runnable {
                performAttemptInternal(
                    key, config, onAttemptStart, onSuccess, onFinalFailure, block
                ) { nextAttempt ->
                    // This lambda is called if a retry is needed; it schedules the subsequent attempt
                    scheduleNext(nextAttempt)
                }
            }

            // Save & post
            states[key] = State(attempt = attempt - 1, runnable = nextAttemptRunnable)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                handler.postDelayed(nextAttemptRunnable, key, nextDelay)
            } else {
                handler.postDelayed(nextAttemptRunnable, nextDelay)
            }
        }

        // Runnable for the initial attempt (attempt 1), executed without delay.
        // It delegates the core logic to performAttemptInternal and passes a lambda to schedule subsequent retries if needed.
        val initialAttemptRunnable = Runnable {
            performAttemptInternal(
                key, config, onAttemptStart, onSuccess, onFinalFailure, block
            ) { nextAttempt ->
                // This lambda is called if a retry is needed; it schedules the first delayed attempt
                scheduleNext(nextAttempt)
            }
        }

        states[key] = State(attempt = 0, runnable = initialAttemptRunnable)
        handler.post(initialAttemptRunnable)
    }

    /**
     * Performs a single attempt of the block operation, handles its success or failure,
     * and decides whether to schedule a next retry based on the decider.
     *
     * @param scheduleNextAction A callback function to invoke if a retry is deemed necessary,
     *                           passing the number of the next attempt to be scheduled.
     */
    private fun performAttemptInternal(
        key: K,
        config: RetryConfig,
        onAttemptStart: (attempt: Int) -> Unit,
        onSuccess: () -> Unit,
        onFinalFailure: (Throwable) -> Unit,
        block: (attempt: Int) -> Unit,
        scheduleNextAction: (nextAttempt: Int) -> Unit // Callback to schedule the next attempt
    ) {
        // Retrieve the current retry state for this key. Guard if canceled in the meantime.
        val state = states[key] ?: return
        val currentAttempt = state.attempt + 1
        state.attempt = currentAttempt
        onAttemptStart(currentAttempt)

        try {
            // user operation; should throw on failure
            // success — cleanup and notify
            block(currentAttempt)
            cancel(key)
            onSuccess()
        } catch (t: Throwable) {
            if (decider.shouldRetry(currentAttempt, t, config.maxAttempts)) {
                // schedule another attempt using the provided callback
                scheduleNextAction(currentAttempt + 1)
            } else {
                cancel(key)
                onFinalFailure(t)
            }
        }
    }

    fun cancel(key: K) {
        states.remove(key)?.runnable?.let { handler.removeCallbacks(it) }
    }

    fun clear() {
        states.values.forEach { state -> state.runnable?.let { handler.removeCallbacks(it) } }
        states.clear()
    }
}
