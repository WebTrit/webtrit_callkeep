package com.webtrit.callkeep.services.services.active_call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.models.NotificationAction
import com.webtrit.callkeep.notifications.ActiveCallNotificationBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for the [ActiveCallService] empty-metadata guard.
 *
 * A START_STICKY restart after a process kill delivers a null intent, leaving
 * [ActiveCallService] with no calls metadata. NotificationManager tracks active calls in its
 * own static list (reset by the process death), so nothing external ever stops such an
 * orphaned instance: without the guard its ongoing FGS notification stays in the shade,
 * cannot be swiped away, and the Hang up action has no visible effect.
 *
 * The guard: after satisfying the startForeground contract, an instance with no calls
 * removes its notification and stops itself; the empty branch of hungUpCall does the same
 * on top of the existing tearDownService fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ActiveCallServiceRestartTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildService(): ActiveCallService = Robolectric.buildService(ActiveCallService::class.java).create().get()

    private fun metadataIntent(vararg callIds: String): Intent =
        Intent(context, ActiveCallService::class.java).apply {
            putParcelableArrayListExtra(
                "metadata",
                ArrayList<Bundle>(callIds.map { CallMetadata(callId = it).toBundle() }),
            )
        }

    private fun declineIntent(): Intent =
        Intent(context, ActiveCallService::class.java).apply {
            action = NotificationAction.Decline.action
        }

    @Test
    fun `null-intent restart stops self and removes the notification`() {
        val service = buildService()

        val result = service.onStartCommand(null, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        val shadow = shadowOf(service)
        assertTrue("service must stop itself", shadow.isStoppedBySelf)
        assertTrue("foreground must be stopped", shadow.isForegroundStopped)
        assertTrue("notification must be removed", shadow.notificationShouldRemoved)
    }

    @Test
    fun `start with an explicitly empty metadata list stops self`() {
        val service = buildService()

        val result = service.onStartCommand(metadataIntent(), 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        val shadow = shadowOf(service)
        assertTrue(shadow.isStoppedBySelf)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.notificationShouldRemoved)
    }

    @Test
    fun `start with calls metadata stays foreground and sticky`() {
        val service = buildService()

        val result = service.onStartCommand(metadataIntent("call-1"), 0, 1)

        assertEquals(Service.START_STICKY, result)
        val shadow = shadowOf(service)
        assertFalse("service must keep running", shadow.isStoppedBySelf)
        assertFalse("foreground must stay", shadow.isForegroundStopped)
        assertEquals(ActiveCallNotificationBuilder.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
    }

    @Test
    fun `hang up with no known calls stops self and removes the notification`() {
        // Decline tapped on a notification re-posted by a null-intent restart: callsMetadata
        // is empty, tearDownService alone does not stop this service.
        val service = buildService()

        val result = service.onStartCommand(declineIntent(), 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        val shadow = shadowOf(service)
        assertTrue("service must stop itself", shadow.isStoppedBySelf)
        assertTrue("foreground must be stopped", shadow.isForegroundStopped)
        assertTrue("notification must be removed", shadow.notificationShouldRemoved)
    }

    @Test
    fun `hang up with a known call keeps the service running`() {
        // The normal path: the hangup is routed to the connection service; this service is
        // stopped later by NotificationManager once the connection reports disconnect.
        val service = buildService()
        service.onStartCommand(metadataIntent("call-1"), 0, 1)

        val result = service.onStartCommand(declineIntent(), 0, 2)

        assertEquals(Service.START_NOT_STICKY, result)
        val shadow = shadowOf(service)
        assertFalse("service must keep running until the connection disconnects", shadow.isStoppedBySelf)
        assertFalse("foreground must stay", shadow.isForegroundStopped)
    }
}
