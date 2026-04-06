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
        val isInCall = runCatching { telecomManager.isInCall }.getOrNull()
        val isInManagedCall = runCatching { telecomManager.isInManagedCall }.getOrNull()
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
         * Returns true if the device supports the Android Telecom framework.
         *
         * Checks the `android.software.telecom` system feature first. If that flag is absent,
         * falls back to inspecting [TelephonyManager.getPhoneType]: any device with a GSM,
         * CDMA, or SIP radio has the Telecom infrastructure available, regardless of whether
         * the OEM advertises the feature flag.
         *
         * This fallback handles known OEM omissions (e.g. ASUS AI2202) where the device is a
         * fully functional phone with Telecom support but does not declare the feature flag in
         * its system build.
         *
         * Devices that return [TelephonyManager.PHONE_TYPE_NONE] (e.g. Wi-Fi-only tablets,
         * Android Go builds) do not have Telecom infrastructure and should use the standalone
         * call path instead.
         */
        fun isTelecomSupported(context: Context): Boolean {
            if (context.packageManager.hasSystemFeature("android.software.telecom")) return true

            // Fallback for OEMs that have Telecom infrastructure but omit the feature flag.
            return try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val phoneType = tm?.phoneType ?: TelephonyManager.PHONE_TYPE_NONE
                val supported = phoneType != TelephonyManager.PHONE_TYPE_NONE
                logger.i("isTelecomSupported: feature flag absent, phoneType=$phoneType — treating Telecom as supported=$supported")
                supported
            } catch (e: Exception) {
                logger.w("isTelecomSupported: fallback check failed (${e.message}), assuming no Telecom support")
                false
            }
        }
    }
}
