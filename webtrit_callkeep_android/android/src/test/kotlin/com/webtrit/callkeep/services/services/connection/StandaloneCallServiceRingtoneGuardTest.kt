package com.webtrit.callkeep.services.services.connection

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [StandaloneCallService.hasOtherRingingCall], the single decision used both by the
 * ringtone-stop guard (keep the shared ringtone for a still-ringing call when another call ends,
 * WT-1073) and by the answer-path call-waiting promotion.
 *
 * The predicate is fed [StandaloneCallService.ringingIncomingCallIds] (NOT the full call map): an
 * outgoing/dialing call is absent from that set, so it never keeps the ringtone alive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class StandaloneCallServiceRingtoneGuardTest {
    @Test
    fun `keeps ringtone when a second incoming call is still ringing`() {
        // A and C both ringing incoming (none answered); A is ending.
        val result =
            StandaloneCallService.hasOtherRingingCall(
                ringingCallIds = setOf("A", "C"),
                answeredCallIds = emptySet(),
                excludingCallId = "A",
            )
        assertTrue(result)
    }

    @Test
    fun `stops ringtone when the last ringing call ends`() {
        val result =
            StandaloneCallService.hasOtherRingingCall(
                ringingCallIds = setOf("A"),
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
                ringingCallIds = setOf("A", "C"),
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
                ringingCallIds = setOf("A", "B", "C"),
                answeredCallIds = setOf("B"),
                excludingCallId = "A",
            )
        assertTrue(result)
    }

    @Test
    fun `no other call when only the terminating call is present`() {
        val result =
            StandaloneCallService.hasOtherRingingCall(
                ringingCallIds = setOf("A"),
                answeredCallIds = setOf("A"),
                excludingCallId = "A",
            )
        assertFalse(result)
    }

    @Test
    fun `outgoing dialing call is not in the ringing set and does not keep the ringtone`() {
        // Incoming A is declined while outgoing B is dialing. B is tracked in callMetadataMap but
        // NOT in ringingIncomingCallIds, so the guard sees no other ringing call and stops the tone.
        val result =
            StandaloneCallService.hasOtherRingingCall(
                ringingCallIds = setOf("A"),
                answeredCallIds = emptySet(),
                excludingCallId = "A",
            )
        assertFalse(result)
    }

    @Test
    fun `answer-path promotion fires when another incoming call is still ringing`() {
        // A just answered (added to answeredCallIds); C still ringing -> promote C to call-waiting.
        val result =
            StandaloneCallService.hasOtherRingingCall(
                ringingCallIds = setOf("A", "C"),
                answeredCallIds = setOf("A"),
                excludingCallId = "A",
            )
        assertTrue(result)
    }

    @Test
    fun `answer-path promotion does not fire when no other call is ringing`() {
        // A answered, nothing else ringing -> no call-waiting tone.
        val result =
            StandaloneCallService.hasOtherRingingCall(
                ringingCallIds = setOf("A"),
                answeredCallIds = setOf("A"),
                excludingCallId = "A",
            )
        assertFalse(result)
    }
}
