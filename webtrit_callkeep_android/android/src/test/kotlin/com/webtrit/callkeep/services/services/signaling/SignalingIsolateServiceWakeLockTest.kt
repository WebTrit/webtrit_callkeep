package com.webtrit.callkeep.services.services.signaling

import android.content.Context
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SignalingIsolateServiceWakeLockTest {

    private val ctx: Context = RuntimeEnvironment.getApplication()

    @Before
    fun resetWakeLock() {
        SignalingIsolateService.resetWakeLock()
    }

    @Test
    fun `getLock returns same instance on repeated calls`() {
        val first = SignalingIsolateService.getLock(ctx)
        val second = SignalingIsolateService.getLock(ctx)
        assertSame(first, second)
    }

    @Test
    fun `acquired lock is held`() {
        val lock = SignalingIsolateService.getLock(ctx)
        lock.acquire(10 * 60 * 1000L)
        try {
            assertTrue(lock.isHeld)
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    @Test
    fun `released lock is not held after acquire and release`() {
        val lock = SignalingIsolateService.getLock(ctx)
        lock.acquire(10 * 60 * 1000L)
        lock.release()
        assertFalse(lock.isHeld)
    }

    @Test
    fun `release guard is safe when lock was never acquired`() {
        val lock = SignalingIsolateService.getLock(ctx)
        assertFalse(lock.isHeld)
        // Mirrors the guard in onDestroy — must not throw
        if (lock.isHeld) {
            lock.release()
        }
    }
}
