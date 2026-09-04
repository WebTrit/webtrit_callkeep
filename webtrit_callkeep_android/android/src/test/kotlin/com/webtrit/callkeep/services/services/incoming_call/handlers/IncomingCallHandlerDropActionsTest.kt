package com.webtrit.callkeep.services.services.incoming_call.handlers

import android.app.Notification
import android.app.Service
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The answer button is taken away as soon as the call is answered, and the answer can be
 * observed before this service instance has shown a notification of its own - the service may
 * have been killed after the call started ringing and recreated by the button's intent.
 *
 * In that state there is no call to describe and no notification id to reuse, so the request
 * has to be ignored. Everything the transition touches would otherwise throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class IncomingCallHandlerDropActionsTest {
    private val service = mock(Service::class.java)
    private val notificationBuilder = mock(IncomingCallNotificationBuilder::class.java)
    private val isolateInitializer = mock(IsolateInitializer::class.java)
    private val notifier = mock(NotificationManagerCompat::class.java)

    private val handler = IncomingCallHandler(service, notificationBuilder, isolateInitializer, notifier)

    @Test
    fun `dropping the actions before a call was shown is ignored`() {
        handler.dropIncomingCallActions()

        verifyNoInteractions(service)
        verifyNoInteractions(notificationBuilder)
    }

    @Test
    fun `dropping the actions twice before a call was shown stays harmless`() {
        handler.dropIncomingCallActions()
        handler.dropIncomingCallActions()

        verifyNoInteractions(service)
        verifyNoInteractions(notificationBuilder)
    }

    @Test
    fun `releasing the notification before a call was shown is ignored the same way`() {
        handler.releaseIncomingCallNotification()

        verifyNoInteractions(service)
        verifyNoInteractions(notificationBuilder)
    }

    @Test
    fun `releasing the notification after it was shown never manipulates the foreground service`() {
        `when`(notificationBuilder.build()).thenReturn(mock(Notification::class.java))
        `when`(notificationBuilder.buildSilent()).thenReturn(mock(Notification::class.java))

        handler.handle(CallMetadata(callId = "call-1", displayName = "Caller"))
        // Ignore the ringing startForeground done while showing the call; only the release matters.
        clearInvocations(service)

        handler.releaseIncomingCallNotification()

        // The release path updates the notification in place and must never touch the service's
        // foreground state or cancel the notification. Detaching and re-promoting it
        // (stopForeground(DETACH) + cancel + startForeground) left a CallStyle notification
        // standing without a foreground service, which Android 14+ rejects and the process is killed.
        val notificationId = IncomingCallNotificationBuilder.notificationId("call-1")
        verify(notifier).notify(eq(notificationId), any(Notification::class.java))
        verify(notifier, never()).cancel(anyInt())
        verify(service, never()).stopForeground(anyInt())
        verify(service, never()).startForeground(anyInt(), any(Notification::class.java))
        verify(service, never()).startForeground(anyInt(), any(Notification::class.java), anyInt())
    }

    @Test
    fun `dropping the actions after a call was shown updates the notification in place`() {
        `when`(notificationBuilder.build()).thenReturn(mock(Notification::class.java))
        `when`(notificationBuilder.buildSilent()).thenReturn(mock(Notification::class.java))

        handler.handle(CallMetadata(callId = "call-1", displayName = "Caller"))
        clearInvocations(service)

        handler.dropIncomingCallActions()

        // Answer path funnels through the same in-place update: silent notification posted, the
        // foreground service left untouched, nothing cancelled.
        val notificationId = IncomingCallNotificationBuilder.notificationId("call-1")
        verify(notifier).notify(eq(notificationId), any(Notification::class.java))
        verify(notifier, never()).cancel(anyInt())
        verify(service, never()).stopForeground(anyInt())
        verify(service, never()).startForeground(anyInt(), any(Notification::class.java))
        verify(service, never()).startForeground(anyInt(), any(Notification::class.java), anyInt())
    }
}
