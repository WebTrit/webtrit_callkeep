package com.webtrit.callkeep.services.services.active_call

import android.app.Notification
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
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Characterization tests for the [ActiveCallService] orphan-notification defect
 * (the "Known limitation" in docs/background-services.md).
 *
 * A START_STICKY restart after a process kill delivers a null intent, leaving the
 * service with no calls metadata. It still promotes itself to the foreground, so a
 * half-empty ongoing notification appears in the shade. Nothing external ever stops
 * such an orphaned instance: the NotificationManager static list of active calls
 * lives in the `:callkeep_core` process and is gone after the process death, and the
 * service itself never calls stopSelf. Tapping Hang up routes into the empty branch
 * of the decline handler, which tears down the connection services but does not stop
 * this service either - the notification stays and cannot be swiped away.
 *
 * These tests PIN the current (broken) behavior so the defect is executable and a
 * regression in either direction is visible. The fix for this defect is expected to
 * flip the orphan-path expectations: the service must stop itself and remove the
 * notification instead of staying foreground. The commented-out tests at the bottom
 * of this class already state those fixed expectations; they fail on the current code
 * and are enabled together with the fix.
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

    private fun assertStaysForegroundWithNotification(service: ActiveCallService) {
        val shadow = shadowOf(service)
        assertFalse("service does not stop itself", shadow.isStoppedBySelf)
        assertFalse("foreground is not stopped", shadow.isForegroundStopped)
        assertEquals(ActiveCallNotificationBuilder.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
    }

    @Test
    fun `null-intent restart re-posts an ongoing notification and stays sticky`() {
        // The orphan scenario itself: the OS restarts the service with a null intent,
        // callsMetadata ends up empty, yet the service goes foreground with a
        // half-empty ongoing notification and asks to be restarted again.
        val service = buildService()

        val result = service.onStartCommand(null, 0, 1)

        assertEquals(Service.START_STICKY, result)
        assertStaysForegroundWithNotification(service)
        val notification = shadowOf(service).lastForegroundNotification
        assertNotEquals(
            "the orphan notification is ongoing, so the user cannot swipe it away",
            0,
            notification.flags and Notification.FLAG_ONGOING_EVENT,
        )
    }

    @Test
    fun `start with an explicitly empty metadata list behaves the same as a null intent`() {
        val service = buildService()

        val result = service.onStartCommand(metadataIntent(), 0, 1)

        assertEquals(Service.START_STICKY, result)
        assertStaysForegroundWithNotification(service)
    }

    @Test
    fun `hang up on the orphan notification does not stop the service or remove the notification`() {
        // The user-visible dead end: Decline tapped on the notification re-posted by a
        // null-intent restart. callsMetadata is empty, so the handler falls back to
        // tearDownService - but no teardown path stops ActiveCallService, so the
        // notification survives the only control the user has over it.
        val service = buildService()
        service.onStartCommand(null, 0, 1)

        val result = service.onStartCommand(declineIntent(), 0, 2)

        assertEquals(Service.START_NOT_STICKY, result)
        val shadow = shadowOf(service)
        assertFalse("service keeps running after Hang up", shadow.isStoppedBySelf)
        assertFalse("foreground is never stopped", shadow.isForegroundStopped)
        assertFalse("notification is not removed", shadow.notificationShouldRemoved)
    }

    @Test
    fun `start with calls metadata stays foreground and sticky`() {
        // The healthy path, pinned so the orphan tests cannot be "fixed" by breaking it:
        // with real calls the service must keep its foreground notification and stay
        // sticky; it is stopped later by NotificationManager once calls disconnect.
        val service = buildService()

        val result = service.onStartCommand(metadataIntent("call-1"), 0, 1)

        assertEquals(Service.START_STICKY, result)
        assertStaysForegroundWithNotification(service)
    }

    // TODO: uncomment the tests below together with the restart-path fix and delete the
    // three tests above that pin the defect. These state the FIXED behavior - an instance
    // with no calls must remove its notification and stop itself instead of staying
    // foreground - so on the current code they fail.
    //
    // private fun assertStoppedAndNotificationRemoved(service: ActiveCallService) {
    //     val shadow = shadowOf(service)
    //     assertTrue("service must stop itself", shadow.isStoppedBySelf)
    //     assertTrue("foreground must be stopped", shadow.isForegroundStopped)
    //     assertTrue("notification must be removed", shadow.notificationShouldRemoved)
    // }
    //
    // @Test
    // fun `null-intent restart stops self and removes the notification`() {
    //     val service = buildService()
    //
    //     val result = service.onStartCommand(null, 0, 1)
    //
    //     assertEquals(Service.START_NOT_STICKY, result)
    //     assertStoppedAndNotificationRemoved(service)
    // }
    //
    // @Test
    // fun `start with an explicitly empty metadata list stops self`() {
    //     val service = buildService()
    //
    //     val result = service.onStartCommand(metadataIntent(), 0, 1)
    //
    //     assertEquals(Service.START_NOT_STICKY, result)
    //     assertStoppedAndNotificationRemoved(service)
    // }
    //
    // @Test
    // fun `hang up with no known calls stops self and removes the notification`() {
    //     val service = buildService()
    //     service.onStartCommand(null, 0, 1)
    //
    //     val result = service.onStartCommand(declineIntent(), 0, 2)
    //
    //     assertEquals(Service.START_NOT_STICKY, result)
    //     assertStoppedAndNotificationRemoved(service)
    // }
}
