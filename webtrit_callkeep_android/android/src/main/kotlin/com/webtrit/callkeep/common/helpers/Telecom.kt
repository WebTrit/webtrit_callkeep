package com.webtrit.callkeep.common.helpers

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

import com.webtrit.callkeep.connection.PhoneConnectionService

object Telecom {
    fun getTelecomManager(context: Context): TelecomManager {
        return context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    fun registerPhoneAccount(
        context: Context
    ) {
        val appName: String = getApplicationName(context)
        val phoneAccountBuilder = PhoneAccount.Builder(getPhoneAccountHandle(context), appName)

        phoneAccountBuilder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
        getTelecomManager(context).registerPhoneAccount(phoneAccountBuilder.build())
    }

    fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
        val componentName = ComponentName(context, PhoneConnectionService::class.java)
        val connectionServiceId = getConnectionServiceId(context)
        return PhoneAccountHandle(componentName, connectionServiceId)
    }

    private fun getApplicationName(appContext: Context): String {
        val applicationInfo = appContext.applicationInfo
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else appContext.getString(
            stringId
        )
    }

    private fun getConnectionServiceId(context: Context): String {
        return context.packageName + ".connectionService"
    }
}
