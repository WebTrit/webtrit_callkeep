package com.webtrit.callkeep.common

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService

class TelephonyUtils(
    private val context: Context,
) {
    fun isEmergencyNumber(number: String): Boolean {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager.isEmergencyNumber(number)
        } else {
            PhoneNumberUtils.isEmergencyNumber(number)
        }
    }

    fun getTelecomManager(): TelecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    fun placeOutgoingCall(
        uri: Uri,
        metadata: CallMetadata,
    ) {
        val extras = buildOutgoingCallExtras(metadata)
        logger.i("placeCall: uri: '$uri', extras: '$extras'")
        getTelecomManager().placeCall(uri, extras)
    }

    fun addNewIncomingCall(metadata: CallMetadata) {
        val telecomManager = getTelecomManager()
        val isInCall =
            try {
                telecomManager.isInCall
            } catch (e: Exception) {
                null
            }
        val isInManagedCall =
            try {
                telecomManager.isInManagedCall
            } catch (e: Exception) {
                null
            }
        logger.i("addNewIncomingCall: callId=${metadata.callId} — before dispatch: isInCall=$isInCall isInManagedCall=$isInManagedCall")
        telecomManager.addNewIncomingCall(
            getPhoneAccountHandle(),
            buildIncomingCallExtras(metadata),
        )
        logger.i("addNewIncomingCall: callId=${metadata.callId} — addNewIncomingCall dispatched to Telecom")
    }

    fun registerPhoneAccount() {
        val appName: String = getApplicationName()
        val phoneAccountBuilder = PhoneAccount.Builder(getPhoneAccountHandle(), appName)

        phoneAccountBuilder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
        getTelecomManager().registerPhoneAccount(phoneAccountBuilder.build())
    }

    fun unregisterPhoneAccount() {
        getTelecomManager().unregisterPhoneAccount(getPhoneAccountHandle())
    }

    fun getPhoneAccountHandle(): PhoneAccountHandle {
        val componentName = ComponentName(context, PhoneConnectionService::class.java)
        val connectionServiceId = getConnectionServiceId()
        return PhoneAccountHandle(componentName, connectionServiceId)
    }

    private fun getApplicationName(): String {
        val applicationInfo = context.applicationInfo
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) {
            applicationInfo.nonLocalizedLabel.toString()
        } else {
            context.getString(stringId)
        }
    }

    private fun getConnectionServiceId(): String = context.packageName + ".connectionService"

    fun buildIncomingCallExtras(metadata: CallMetadata): Bundle =
        Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, getPhoneAccountHandle())
            putBoolean(TelecomManager.METADATA_IN_CALL_SERVICE_RINGING, true)
            putAll(metadata.toBundle())
        }

    fun buildOutgoingCallExtras(metadata: CallMetadata): Bundle =
        Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, getPhoneAccountHandle())
            putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, metadata.toBundle())
        }

    companion object {
        private const val TAG = "TelephonyUtils"

        private val logger = Log(TAG)

        /**
         * Returns true if the device supports the Android Telecom framework
         * (`android.software.telecom` system feature).
         *
         * Devices without this feature (e.g. some tablets, Android Go builds, certain OEM
         * configurations) cannot use [TelecomManager] for call management. Callers should
         * check this before registering a phone account or invoking any Telecom API.
         */
        fun isTelecomSupported(context: Context): Boolean = context.packageManager.hasSystemFeature("android.software.telecom")
    }
}
