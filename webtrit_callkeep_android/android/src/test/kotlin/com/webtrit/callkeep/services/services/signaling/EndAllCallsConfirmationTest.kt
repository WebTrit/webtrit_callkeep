package com.webtrit.callkeep.services.services.signaling

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.broadcaster.ConnectionServicePerformBroadcaster
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies the broadcast-reception and timeout logic used in [SignalingIsolateService.endAllCalls].
 *
 * Tests exercise the same pendingIds/handler pattern without spinning up the full service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@LooperMode(LooperMode.Mode.PAUSED)
class EndAllCallsConfirmationTest {

    private val ctx: Context = RuntimeEnvironment.getApplication()
    private val timeoutMs = 5_000L

    private fun buildReceiver(
        pendingIds: MutableSet<String>,
        onFinish: () -> Unit,
    ): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                val id = intent?.extras?.getString(CallDataConst.CALL_ID) ?: return
                if (pendingIds.remove(id) && pendingIds.isEmpty()) onFinish()
            }
        }
    }

    @Test
    fun `callback resolves after all active callIds confirm`() {
        val resolved = AtomicBoolean(false)
        val ids = listOf("call-A", "call-B")
        val pendingIds = Collections.synchronizedSet(ids.toMutableSet())
        val receiver = buildReceiver(pendingIds) { resolved.set(true) }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(ConnectionPerform.HungUp, ConnectionPerform.DeclineCall), ctx, receiver,
            exported = false,
        )

        ctx.sendInternalBroadcast(ConnectionPerform.HungUp.name,
            Bundle().apply { putString(CallDataConst.CALL_ID, "call-A") })
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse("should not resolve after first of two confirmations", resolved.get())

        ctx.sendInternalBroadcast(ConnectionPerform.HungUp.name,
            Bundle().apply { putString(CallDataConst.CALL_ID, "call-B") })
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("should resolve after all confirmations", resolved.get())

        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(ctx, receiver)
    }

    @Test
    fun `unrelated callId broadcast is ignored and does not resolve callback`() {
        val resolved = AtomicBoolean(false)
        val pendingIds = Collections.synchronizedSet(mutableSetOf("call-X"))
        val receiver = buildReceiver(pendingIds) { resolved.set(true) }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(ConnectionPerform.HungUp), ctx, receiver, exported = false,
        )

        ctx.sendInternalBroadcast(ConnectionPerform.HungUp.name,
            Bundle().apply { putString(CallDataConst.CALL_ID, "unrelated-call") })
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(resolved.get())
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(ctx, receiver)
    }

    @Test
    fun `broadcast without callId is ignored`() {
        val resolved = AtomicBoolean(false)
        val pendingIds = Collections.synchronizedSet(mutableSetOf("call-Y"))
        val receiver = buildReceiver(pendingIds) { resolved.set(true) }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(ConnectionPerform.HungUp), ctx, receiver, exported = false,
        )

        ctx.sendInternalBroadcast(ConnectionPerform.HungUp.name, extras = null)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(resolved.get())
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(ctx, receiver)
    }

    @Test
    fun `timeout resolves callback when confirmations are missing`() {
        val resolved = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())

        handler.postDelayed({ resolved.set(true) }, timeoutMs)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(timeoutMs, TimeUnit.MILLISECONDS)

        assertTrue(resolved.get())
    }
}
