package com.webtrit.callkeep.common

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TelephonyUtilsIsTelecomSupportedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private fun setFeatureFlag(enabled: Boolean) {
        Shadows
            .shadowOf(context.packageManager)
            .setSystemFeature("android.software.telecom", enabled)
    }

    private fun setPhoneType(phoneType: Int) {
        Shadows
            .shadowOf(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
            .setPhoneType(phoneType)
    }

    @Test
    fun `returns true when feature flag is present`() {
        setFeatureFlag(true)
        assertTrue(TelephonyUtils.isTelecomSupported(context))
    }

    @Test
    fun `returns true when feature flag is absent but phoneType is non-NONE`() {
        setFeatureFlag(false)
        setPhoneType(TelephonyManager.PHONE_TYPE_GSM)
        assertTrue(TelephonyUtils.isTelecomSupported(context))
    }

    @Test
    fun `returns false when feature flag is absent and phoneType is NONE`() {
        setFeatureFlag(false)
        setPhoneType(TelephonyManager.PHONE_TYPE_NONE)
        assertFalse(TelephonyUtils.isTelecomSupported(context))
    }
}
