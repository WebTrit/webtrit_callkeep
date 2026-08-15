package com.webtrit.callkeep.services.services.incoming_call.handlers

import android.app.Service
import android.os.Build
import com.webtrit.callkeep.notifications.IncomingCallNotificationBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
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

    private val handler = IncomingCallHandler(service, notificationBuilder, isolateInitializer)

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
}
