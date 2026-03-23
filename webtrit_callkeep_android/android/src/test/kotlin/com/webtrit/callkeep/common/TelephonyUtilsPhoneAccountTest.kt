package com.webtrit.callkeep.common

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import org.junit.Assert.assertEquals
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
class TelephonyUtilsPhoneAccountTest {
    private lateinit var context: Context
    private lateinit var utils: TelephonyUtils

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Robolectric leaves applicationInfo.nonLocalizedLabel null; set it so
        // TelephonyUtils.getApplicationName() does not throw NPE.
        context.applicationInfo.nonLocalizedLabel = "TestApp"
        utils = TelephonyUtils(context)
    }

    private fun registeredHandles() =
        Shadows
            .shadowOf(context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
            .allPhoneAccounts
            .map { it.accountHandle }

    // -------------------------------------------------------------------------
    // registerPhoneAccount
    // -------------------------------------------------------------------------

    @Test
    fun `registerPhoneAccount makes account visible in TelecomManager`() {
        utils.registerPhoneAccount()
        assertTrue(utils.getPhoneAccountHandle() in registeredHandles())
    }

    // -------------------------------------------------------------------------
    // unregisterPhoneAccount
    // -------------------------------------------------------------------------

    @Test
    fun `unregisterPhoneAccount removes account from TelecomManager`() {
        utils.registerPhoneAccount()
        utils.unregisterPhoneAccount()
        assertEquals(emptyList<Any>(), registeredHandles())
    }

    @Test
    fun `unregisterPhoneAccount on non-existent account does not throw`() {
        // Must be a no-op — no prior registerPhoneAccount() call
        utils.unregisterPhoneAccount()
    }

    @Test
    fun `second unregisterPhoneAccount call is a safe no-op`() {
        utils.registerPhoneAccount()
        utils.unregisterPhoneAccount()
        utils.unregisterPhoneAccount()
    }

    // -------------------------------------------------------------------------
    // register / unregister symmetry
    // -------------------------------------------------------------------------

    @Test
    fun `re-registering after unregister makes account visible again`() {
        utils.registerPhoneAccount()
        utils.unregisterPhoneAccount()
        utils.registerPhoneAccount()
        assertTrue(utils.getPhoneAccountHandle() in registeredHandles())
    }
}
