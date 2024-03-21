package com.webtrit.callkeep.webtrit_callkeep_android.common.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

class TelephonyHelper(var context: Context? = null) {
    fun isEmergencyNumber(number: String): Boolean {
        val telephonyManager = context?.getSystemService(Context.TELEPHONY_SERVICE)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (telephonyManager as TelephonyManager).isEmergencyNumber(number)
        } else {
            PhoneNumberUtils.isEmergencyNumber(number)
        }
    }

    fun isAvailableTelephony(): Boolean {
        val packageManager: PackageManager = context!!.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }
}
