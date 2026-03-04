package com.webtrit.callkeep.services.services.incoming_call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ActivityLifecycleBroadcaster
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform
import com.webtrit.callkeep.services.services.foreground.ForegroundService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the incomingCallFullScreen connection-tracker integration in [IncomingCallService].
 *
 * Verifies:
 * - [IncomingCallService] populates [ForegroundService.connectionTracker] in [handleLaunch]
 *   when full-screen mode is enabled.
 * - The tracker is NOT populated when full-screen mode is disabled.
 * - The tracker entry is cleaned up in [performDeclineCall].
 *
 * The tracker update in [handleLaunch] occurs before [IncomingCallNotificationBuilder.build],
 * so if notification building fails due to test-environment resource limitations
 * (library string resources not always resolvable via Robolectric), the tracker state
 * tested here is still valid.  [runCatching] is used in tests that trigger notification
 * building to absorb this expected environment exception without hiding tracker regressions.
 *
 * [ActivityLifecycleBroadcaster] is set to ON_RESUME so that [DefaultIsolateLaunchPolicy]
 * returns IsolateType.MAIN and the Flutter isolate is never started.
 *
 * Decline is simulated by directly invoking the service's private [connectionServicePerformReceiver]
 * via reflection.  Robolectric 4.16 does not reliably route broadcasts registered with
 * [Context.RECEIVER_EXPORTED] when the sender is the same process, so direct invocation is used
 * to keep the tests hermetic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class IncomingCallServiceFullScreenTest {

    private val tracker get() = ForegroundService.connectionTracker
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        tracker.clear()
        // Simulate the app Activity being in the foreground so that DefaultIsolateLaunchPolicy
        // returns IsolateType.MAIN and the Flutter isolate is never launched in tests.
        ActivityLifecycleBroadcaster.setValue(context, Lifecycle.Event.ON_RESUME)
    }

    @After
    fun tearDown() {
        tracker.clear()
    }

    private fun meta(callId: String) = CallMetadata(callId = callId)

    private fun buildLaunchIntent(metadata: CallMetadata): Intent =
        Intent(context, IncomingCallService::class.java).apply {
            action = PushNotificationServiceEnums.IC_INITIALIZE.name
            putExtras(metadata.toBundle())
        }

    /**
     * Creates the service (registers broadcast receiver) and sends IC_INITIALIZE.
     * Returns the service instance so tests can simulate broadcasts via [simulateDecline].
     *
     * Notification building may throw Resources$NotFoundException in the Robolectric environment
     * because library string resources are not always resolvable in library-module unit tests.
     * The tracker update in handleLaunch executes before showNotification(), so the exception
     * does not affect the tracker assertion below.
     */
    private fun launchService(metadata: CallMetadata): IncomingCallService {
        val service = Robolectric.buildService(IncomingCallService::class.java, buildLaunchIntent(metadata))
            .create()
            .get()
        runCatching { service.onStartCommand(buildLaunchIntent(metadata), 0, 1) }
        return service
    }

    /**
     * Simulates a DeclineCall broadcast by directly invoking the service's private receiver.
     *
     * Robolectric 4.16 does not deliver broadcasts registered with [Context.RECEIVER_EXPORTED]
     * to receivers in the same process, so we bypass the OS broadcast bus and call onReceive
     * directly via reflection.  The behaviour under test (tracker mutation in performDeclineCall)
     * is identical.
     */
    private fun simulateDecline(service: IncomingCallService, metadata: CallMetadata) {
        val field = IncomingCallService::class.java
            .getDeclaredField("connectionServicePerformReceiver")
        field.isAccessible = true
        val receiver = field.get(service) as BroadcastReceiver
        val intent = Intent(ConnectionPerform.DeclineCall.name).apply { putExtras(metadata.toBundle()) }
        receiver.onReceive(context, intent)
    }

    // -------------------------------------------------------------------------
    // handleLaunch — tracker population
    // -------------------------------------------------------------------------

    @Test
    fun `handleLaunch adds callId to tracker when full-screen is enabled`() {
        StorageDelegate.Sound.setIncomingCallFullScreen(context, true)
        val metadata = meta("call-fs-on")

        launchService(metadata)

        assertTrue("Tracker must contain callId when full-screen is enabled", tracker.exists("call-fs-on"))
    }

    @Test
    fun `handleLaunch does not add callId to tracker when full-screen is disabled`() {
        StorageDelegate.Sound.setIncomingCallFullScreen(context, false)
        val metadata = meta("call-fs-off")

        launchService(metadata)

        assertFalse("Tracker must not contain callId when full-screen is disabled", tracker.exists("call-fs-off"))
    }

    // -------------------------------------------------------------------------
    // performDeclineCall — tracker cleanup
    // -------------------------------------------------------------------------

    @Test
    fun `performDeclineCall removes callId from tracker`() {
        StorageDelegate.Sound.setIncomingCallFullScreen(context, true)
        val metadata = meta("call-decline")

        val service = launchService(metadata)
        assertTrue("Pre-condition: tracker must have callId after handleLaunch", tracker.exists("call-decline"))

        simulateDecline(service, metadata)

        assertFalse("Tracker must not contain callId after performDeclineCall", tracker.exists("call-decline"))
    }

    @Test
    fun `performDeclineCall is safe when tracker has no entry for callId`() {
        // Full-screen disabled means handleLaunch did not add to tracker.
        StorageDelegate.Sound.setIncomingCallFullScreen(context, false)
        val metadata = meta("call-decline-off")

        val service = launchService(metadata)
        assertFalse(tracker.exists("call-decline-off"))

        // Decline must not throw even when tracker has no entry for this callId.
        simulateDecline(service, metadata)

        assertFalse(tracker.exists("call-decline-off"))
    }
}
