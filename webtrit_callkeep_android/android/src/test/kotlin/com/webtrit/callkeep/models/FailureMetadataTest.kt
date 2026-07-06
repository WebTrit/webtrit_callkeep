package com.webtrit.callkeep.models

import android.os.Build
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class FailureMetadataTest {
    @Test
    fun `round-trip preserves UNENTITLED failure type`() {
        val original = FailureMetadata(callMetadata = null, message = "err")
        val restored = FailureMetadata.fromBundle(original.toBundle())
        assertEquals(OutgoingFailureType.UNENTITLED, restored.outgoingFailureType)
    }

    @Test
    fun `round-trip preserves EMERGENCY_NUMBER failure type`() {
        val original = FailureMetadata(
            callMetadata = null,
            message = "emergency",
            outgoingFailureType = OutgoingFailureType.EMERGENCY_NUMBER,
        )
        val restored = FailureMetadata.fromBundle(original.toBundle())
        assertEquals(OutgoingFailureType.EMERGENCY_NUMBER, restored.outgoingFailureType)
    }

    @Test
    fun `unknown string falls back to UNENTITLED`() {
        val bundle = Bundle().apply { putString("FAILURE_OUTGOING_TYPE", "FUTURE_TYPE") }
        val restored = FailureMetadata.fromBundle(bundle)
        assertEquals(OutgoingFailureType.UNENTITLED, restored.outgoingFailureType)
    }

    @Test
    fun `missing key falls back to UNENTITLED`() {
        val restored = FailureMetadata.fromBundle(Bundle())
        assertEquals(OutgoingFailureType.UNENTITLED, restored.outgoingFailureType)
    }
}
