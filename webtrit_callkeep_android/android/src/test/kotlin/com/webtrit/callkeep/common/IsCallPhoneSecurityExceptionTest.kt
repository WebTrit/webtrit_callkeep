package com.webtrit.callkeep.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsCallPhoneSecurityExceptionTest {

    @Test
    fun `null returns false`() {
        val e: SecurityException? = null
        assertFalse(e.isCallPhoneSecurityException())
    }

    @Test
    fun `message contains CALL_PHONE permission required returns true`() {
        val e = SecurityException("CALL_PHONE permission required to place call")
        assertTrue(e.isCallPhoneSecurityException())
    }

    @Test
    fun `message contains android permission CALL_PHONE returns true`() {
        val e = SecurityException("requires android.permission.CALL_PHONE")
        assertTrue(e.isCallPhoneSecurityException())
    }

    @Test
    fun `unrelated SecurityException returns false`() {
        val e = SecurityException("Permission denied: reading contacts")
        assertFalse(e.isCallPhoneSecurityException())
    }

    @Test
    fun `empty message returns false`() {
        val e = SecurityException("")
        assertFalse(e.isCallPhoneSecurityException())
    }

    @Test
    fun `null message returns false`() {
        val e = SecurityException(null as String?)
        assertFalse(e.isCallPhoneSecurityException())
    }

    @Test
    fun `message with mixed case does not match — extension is case-sensitive`() {
        val e = SecurityException("call_phone permission required")
        assertFalse(e.isCallPhoneSecurityException())
    }
}
