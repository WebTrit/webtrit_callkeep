package com.webtrit.callkeep.models

import android.os.Build
import android.os.Bundle
import com.webtrit.callkeep.common.CallDataConst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [CallHandle] bundle (de)serialization and the absence propagation
 * introduced for WT-1141: a missing number must surface as `null`, never as a
 * literal placeholder string.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CallHandleTest {
    @Test
    fun `fromBundle returns null for a null bundle`() {
        assertNull(CallHandle.fromBundle(null))
    }

    @Test
    fun `fromBundle returns null when the number key is absent`() {
        assertNull(CallHandle.fromBundle(Bundle()))
    }

    @Test
    fun `fromBundle reads the number when present`() {
        val bundle = Bundle().apply { putString("number", "555001") }

        assertEquals(CallHandle("555001"), CallHandle.fromBundle(bundle))
    }

    @Test
    fun `toBundle and fromBundle round-trip preserves the number`() {
        val original = CallHandle("555002")

        assertEquals(original, CallHandle.fromBundle(original.toBundle()))
    }

    /**
     * A NUMBER sub-bundle present but missing the "number" key must leave
     * [CallMetadata.handle] (and therefore [CallMetadata.number]) null rather than
     * fabricating a placeholder.
     */
    @Test
    fun `fromBundleOrNull leaves handle null when the number key is missing`() {
        val bundle =
            Bundle().apply {
                putString(CallDataConst.CALL_ID, "call-1")
                putBundle(CallDataConst.NUMBER, Bundle())
            }

        val metadata = CallMetadata.fromBundleOrNull(bundle)

        assertNotNull(metadata)
        assertNull(metadata!!.handle)
        assertNull(metadata.number)
    }
}
