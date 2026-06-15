package com.webtrit.callkeep.services.services.connection

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [StandaloneCallService.hasOtherRingingCall], the guard that keeps the shared
 * ringtone playing for a still-ringing call when another incoming call ends (WT-1073).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class StandaloneCallServiceRingtoneGuardTest {
    @Test
    fun `keeps ringtone when a second incoming call is still ringing`() {
        // A and C both ringing (none answered); A is ending.
        val result =
            StandaloneCallService.hasOtherRingingCall(
                callIds = setOf("A", "C"),
                answeredCallIds = emptySet(),
                excludingCallId = "A",
            )
        assertTrue(result)
    }

    @Test
    fun `stops ringtone when the last ringing call ends`() {
        val result =
            StandaloneCallService.hasOtherRingingCall(
                callIds = setOf("A"),
                answeredCallIds = emptySet(),
                excludingCallId = "A",
            )
        assertFalse(result)
    }

    @Test
    fun `the only other call being answered does not count as ringing`() {
        // A is ending; C is already answered (active) -> nothing else ringing.
        val result =
            StandaloneCallService.hasOtherRingingCall(
                callIds = setOf("A", "C"),
                answeredCallIds = setOf("C"),
                excludingCallId = "A",
            )
        assertFalse(result)
    }

    @Test
    fun `another ringing call counts even when one other call is answered`() {
        // A is ending; B answered, C still ringing -> keep the tone.
        val result =
            StandaloneCallService.hasOtherRingingCall(
                callIds = setOf("A", "B", "C"),
                answeredCallIds = setOf("B"),
                excludingCallId = "A",
            )
        assertTrue(result)
    }

    @Test
    fun `no other call when only the terminating call is present`() {
        val result =
            StandaloneCallService.hasOtherRingingCall(
                callIds = setOf("A"),
                answeredCallIds = setOf("A"),
                excludingCallId = "A",
            )
        assertFalse(result)
    }
}
