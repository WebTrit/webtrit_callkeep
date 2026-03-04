package com.webtrit.callkeep.common

import android.os.Build
import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class RetryManagerTest {

    private lateinit var handler: Handler
    private lateinit var manager: RetryManager<String>

    /** Always retries until maxAttempts is reached. */
    private val alwaysRetryDecider = object : RetryDecider {
        override fun shouldRetry(attempt: Int, error: Throwable, maxAttempts: Int) =
            attempt < maxAttempts
    }

    /** Never retries — treats every error as fatal. */
    private val neverRetryDecider = object : RetryDecider {
        override fun shouldRetry(attempt: Int, error: Throwable, maxAttempts: Int) = false
    }

    /** Only retries for SecurityException. */
    private val securityOnlyDecider = object : RetryDecider {
        override fun shouldRetry(attempt: Int, error: Throwable, maxAttempts: Int) =
            attempt < maxAttempts && error is SecurityException
    }

    // Zero-delay config so Robolectric can drain the queue without idle-advancement.
    private val immediateConfig = RetryConfig(maxAttempts = 3, initialDelayMs = 0, backoffMultiplier = 1.0, maxDelayMs = 0)

    @Before
    fun setUp() {
        handler = Handler(Looper.getMainLooper())
        manager = RetryManager(handler, alwaysRetryDecider)
    }

    private fun drain() {
        // Run pending + newly posted tasks until the queue is empty.
        repeat(20) {
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        }
    }

    // -------------------------------------------------------------------------
    // Success on first attempt
    // -------------------------------------------------------------------------

    @Test
    fun `block succeeds on first attempt — onSuccess is called`() {
        var successCalled = false
        var attemptCount = 0

        manager.run(
            key = "test",
            config = immediateConfig,
            onAttemptStart = { attemptCount++ },
            onSuccess = { successCalled = true },
        ) { /* no-op = success */ }

        drain()

        assertTrue(successCalled)
        assertEquals(1, attemptCount)
    }

    @Test
    fun `block succeeds on first attempt — onFinalFailure is NOT called`() {
        var failureCalled = false

        manager.run(
            key = "test",
            config = immediateConfig,
            onFinalFailure = { failureCalled = true },
        ) { /* success */ }

        drain()
        assertTrue(!failureCalled)
    }

    // -------------------------------------------------------------------------
    // Retry behaviour
    // -------------------------------------------------------------------------

    @Test
    fun `block retries up to maxAttempts when decider allows`() {
        var attemptCount = 0

        manager.run(
            key = "test",
            config = immediateConfig,
            onAttemptStart = { attemptCount++ },
        ) { throw RuntimeException("always fail") }

        drain()

        // Expecting exactly maxAttempts (3) runs
        assertEquals(immediateConfig.maxAttempts, attemptCount)
    }

    @Test
    fun `onFinalFailure is called after last attempt`() {
        var finalError: Throwable? = null

        manager.run(
            key = "test",
            config = immediateConfig,
            onFinalFailure = { finalError = it },
        ) { throw RuntimeException("always fail") }

        drain()

        assertNotNull(finalError)
        assertEquals("always fail", finalError?.message)
    }

    @Test
    fun `block is NOT retried when decider returns false`() {
        val noRetryManager = RetryManager<String>(handler, neverRetryDecider)
        var attemptCount = 0

        noRetryManager.run(
            key = "test",
            config = immediateConfig,
            onAttemptStart = { attemptCount++ },
        ) { throw RuntimeException("fail") }

        drain()

        assertEquals(1, attemptCount)
    }

    @Test
    fun `block is retried only for matching exception type`() {
        val securityManager = RetryManager<String>(handler, securityOnlyDecider)
        var attemptCount = 0
        var finalError: Throwable? = null

        securityManager.run(
            key = "test",
            config = immediateConfig,
            onAttemptStart = { attemptCount++ },
            onFinalFailure = { finalError = it },
        ) { throw RuntimeException("not a security exception") }

        drain()

        // Should stop immediately — no retry for RuntimeException
        assertEquals(1, attemptCount)
        assertNotNull(finalError)
    }

    @Test
    fun `SecurityException triggers retry when decider matches`() {
        val securityManager = RetryManager<String>(handler, securityOnlyDecider)
        var attemptCount = 0

        securityManager.run(
            key = "test",
            config = immediateConfig,
            onAttemptStart = { attemptCount++ },
        ) { throw SecurityException("CALL_PHONE permission required") }

        drain()

        assertEquals(immediateConfig.maxAttempts, attemptCount)
    }

    // -------------------------------------------------------------------------
    // cancel
    // -------------------------------------------------------------------------

    @Test
    fun `cancel stops pending retries`() {
        var attemptCount = 0

        manager.run(
            key = "cancel-test",
            config = RetryConfig(maxAttempts = 5, initialDelayMs = 0),
            onAttemptStart = { attemptCount++ },
        ) { throw RuntimeException("fail") }

        // Let the first attempt run, then cancel
        Shadows.shadowOf(Looper.getMainLooper()).runOneTask()
        manager.cancel("cancel-test")

        drain()

        // Should have done at most 1 attempt before the cancel
        assertTrue(attemptCount <= 1)
    }

    @Test
    fun `cancel on unknown key does not throw`() {
        manager.cancel("nonexistent")
    }

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    @Test
    fun `clear stops all pending retries`() {
        var count1 = 0
        var count2 = 0

        manager.run(key = "k1", config = RetryConfig(maxAttempts = 5, initialDelayMs = 0), onAttemptStart = { count1++ }) {
            throw RuntimeException("fail")
        }
        manager.run(key = "k2", config = RetryConfig(maxAttempts = 5, initialDelayMs = 0), onAttemptStart = { count2++ }) {
            throw RuntimeException("fail")
        }

        Shadows.shadowOf(Looper.getMainLooper()).runOneTask()
        Shadows.shadowOf(Looper.getMainLooper()).runOneTask()
        manager.clear()
        drain()

        assertTrue(count1 <= 1)
        assertTrue(count2 <= 1)
    }

    // -------------------------------------------------------------------------
    // Re-entrant run (reset on new run for same key)
    // -------------------------------------------------------------------------

    @Test
    fun `second run on same key resets state`() {
        var attemptCount = 0

        // First run — fails and exhausts all retries
        manager.run(key = "reuse", config = immediateConfig, onAttemptStart = { attemptCount++ }) {
            throw RuntimeException("fail")
        }
        drain()
        val afterFirstRun = attemptCount

        // Second run for the same key — should start from 1 again
        attemptCount = 0
        var successCalled = false
        manager.run(key = "reuse", config = immediateConfig, onSuccess = { successCalled = true }) {
            /* success */
        }
        drain()

        assertTrue(successCalled)
        assertEquals(3, afterFirstRun) // sanity check
    }

    // -------------------------------------------------------------------------
    // RetryConfig
    // -------------------------------------------------------------------------

    @Test
    fun `RetryConfig default values are sane`() {
        val config = RetryConfig()
        assertTrue(config.maxAttempts > 0)
        assertTrue(config.initialDelayMs > 0)
        assertTrue(config.backoffMultiplier >= 1.0)
        assertTrue(config.maxDelayMs >= config.initialDelayMs)
    }

    @Test
    fun `onAttemptStart receives incrementing attempt numbers`() {
        val attempts = mutableListOf<Int>()

        manager.run(
            key = "test",
            config = immediateConfig,
            onAttemptStart = { attempts.add(it) },
        ) { throw RuntimeException("fail") }

        drain()

        assertEquals(listOf(1, 2, 3), attempts)
    }
}
