package com.webtrit.callkeep.services.services.signaling

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.common.sendInternalBroadcast
import com.webtrit.callkeep.services.broadcaster.CallLifecycleEvent
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies the broadcast-reception and timeout logic used in [SignalingIsolateService.endCall].
 *
 * Tests exercise the same receiver/handler pattern without spinning up the full service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@LooperMode(LooperMode.Mode.PAUSED)
class EndCallConfirmationTest {

    private val ctx: Context = RuntimeEnvironment.getApplication()
    private val timeoutMs = 5_000L

    private fun buildReceiverAndFinish(
        callId: String,
        onFinish: () -> Unit,
    ): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                val id = intent?.extras?.getString(CallDataConst.CALL_ID) ?: return
                if (id == callId) onFinish()
            }
        }
    }

    @Test
    fun `callback resolves when matching HungUp broadcast arrives`() {
        val resolved = AtomicBoolean(false)
        val callId = "call-1"
        val receiver = buildReceiverAndFinish(callId) { resolved.set(true) }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(CallLifecycleEvent.HungUp), ctx, receiver, exported = false,
        )

        val extras = Bundle().apply { putString(CallDataConst.CALL_ID, callId) }
        ctx.sendInternalBroadcast(CallLifecycleEvent.HungUp.name, extras)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(resolved.get())
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(ctx, receiver)
    }

    @Test
    fun `callback resolves when matching DeclineCall broadcast arrives`() {
        val resolved = AtomicBoolean(false)
        val callId = "call-2"
        val receiver = buildReceiverAndFinish(callId) { resolved.set(true) }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(CallLifecycleEvent.HungUp, CallLifecycleEvent.DeclineCall), ctx, receiver,
            exported = false,
        )

        val extras = Bundle().apply { putString(CallDataConst.CALL_ID, callId) }
        ctx.sendInternalBroadcast(CallLifecycleEvent.DeclineCall.name, extras)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(resolved.get())
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(ctx, receiver)
    }

    @Test
    fun `callback does not resolve when broadcast has a different callId`() {
        val resolved = AtomicBoolean(false)
        val callId = "call-3"
        val receiver = buildReceiverAndFinish(callId) { resolved.set(true) }

        ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
            listOf(CallLifecycleEvent.HungUp), ctx, receiver, exported = false,
        )

        val extras = Bundle().apply { putString(CallDataConst.CALL_ID, "other-call") }
        ctx.sendInternalBroadcast(CallLifecycleEvent.HungUp.name, extras)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(resolved.get())
        ConnectionServicePerformBroadcaster.unregisterConnectionPerformReceiver(ctx, receiver)
    }

    @Test
    fun `timeout resolves callback when no broadcast arrives`() {
        val resolved = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())

        handler.postDelayed({ resolved.set(true) }, timeoutMs)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS
        )

        assertTrue(resolved.get())
    }
}
