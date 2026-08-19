package com.webtrit.callkeep.common

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class OutgoingCallUriTest {
    @Test
    fun `user part carries no digit for any number`() {
        for (number in listOf("112", "911", "999", "0", "1234567890")) {
            val userPart = OutgoingCallUri.of(number).schemeSpecificPart.substringBefore("@")
            assertTrue("user part <$userPart> must contain no digit", userPart.none { it.isDigit() })
        }
    }

    @Test
    fun `emergency-colliding extension is masked to letters`() {
        assertEquals("sip:bbc@portadialer.internal", OutgoingCallUri.of("112").toString())
    }

    @Test
    fun `non-digit dial characters are preserved`() {
        val userPart = OutgoingCallUri.of("+380*100#").schemeSpecificPart.substringBefore("@")
        assertEquals("+dia*baa#", userPart)
    }
}
