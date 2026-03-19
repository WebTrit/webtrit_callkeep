package com.webtrit.callkeep.services.services.signaling

import android.os.Build
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.fromBundle
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that the [Lifecycle.Event.fromBundle] guard in [SignalingIsolateService.lifecycleEventReceiver]
 * returns null for missing or invalid extras, preventing a crash in synchronizeSignalingIsolate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SignalingIsolateLifecycleExtrasTest {
    @Test
    fun `fromBundle returns null for null bundle`() {
        assertNull(Lifecycle.Event.fromBundle(null))
    }

    @Test
    fun `fromBundle returns null for empty bundle`() {
        assertNull(Lifecycle.Event.fromBundle(Bundle()))
    }

    @Test
    fun `fromBundle returns null for bundle with invalid lifecycle event name`() {
        val bundle = Bundle().apply { putString("LifecycleEvent", "NOT_A_VALID_EVENT") }
        assertNull(Lifecycle.Event.fromBundle(bundle))
    }
}
