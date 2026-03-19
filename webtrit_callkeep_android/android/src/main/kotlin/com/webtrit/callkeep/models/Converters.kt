package com.webtrit.callkeep.models

import android.telecom.DisconnectCause
import com.webtrit.callkeep.PAudioDevice
import com.webtrit.callkeep.PAudioDeviceType
import com.webtrit.callkeep.PCallkeepConnection
import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.PCallkeepDisconnectCause
import com.webtrit.callkeep.PCallkeepDisconnectCauseType
import com.webtrit.callkeep.PCallkeepIncomingCallData
import com.webtrit.callkeep.PHandle
import com.webtrit.callkeep.PHandleTypeEnum
import com.webtrit.callkeep.services.services.connection.PhoneConnection

fun PHandle.toCallHandle(): CallHandle {
    return CallHandle(value)
}

fun CallHandle.toPHandle(): PHandle {
    return PHandle(value = number, type = PHandleTypeEnum.NUMBER)
}

fun PhoneConnection.toPConnection(): PCallkeepConnection? {
    val disconnectCause = disconnectCause ?: DisconnectCause(DisconnectCause.UNKNOWN)

    val callkeepStatus = PCallkeepConnectionState.ofRaw(state)
    val callkeepDisconnectCauseType = PCallkeepDisconnectCauseType.ofRaw(disconnectCause.code)

    if (callkeepStatus == null || callkeepDisconnectCauseType == null) {
        return null
    }

    val callkeepDisconnectCause =
        PCallkeepDisconnectCause(
            callkeepDisconnectCauseType,
            disconnectCause.reason ?: "Unknown reason",
        )

    return PCallkeepConnection(callId, callkeepStatus, callkeepDisconnectCause)
}

fun PAudioDevice.toAudioDevice(): AudioDevice {
    return AudioDevice(
        type =
            when (this.type) {
                PAudioDeviceType.EARPIECE -> AudioDeviceType.EARPIECE
                PAudioDeviceType.SPEAKER -> AudioDeviceType.SPEAKER
                PAudioDeviceType.BLUETOOTH -> AudioDeviceType.BLUETOOTH
                PAudioDeviceType.WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                PAudioDeviceType.STREAMING -> AudioDeviceType.STREAMING
                PAudioDeviceType.UNKNOWN -> AudioDeviceType.UNKNOWN
            },
        name = this.name,
        id = this.id,
    )
}

fun AudioDevice.toPAudioDevice(): PAudioDevice {
    return PAudioDevice(
        type =
            when (this.type) {
                AudioDeviceType.EARPIECE -> PAudioDeviceType.EARPIECE
                AudioDeviceType.SPEAKER -> PAudioDeviceType.SPEAKER
                AudioDeviceType.BLUETOOTH -> PAudioDeviceType.BLUETOOTH
                AudioDeviceType.WIRED_HEADSET -> PAudioDeviceType.WIRED_HEADSET
                AudioDeviceType.STREAMING -> PAudioDeviceType.STREAMING
                AudioDeviceType.UNKNOWN -> PAudioDeviceType.UNKNOWN
            },
        name = this.name,
        id = this.id,
    )
}

fun CallMetadata.toPCallkeepIncomingCallData(): PCallkeepIncomingCallData {
    return PCallkeepIncomingCallData(
        callId = callId,
        handle = handle?.toPHandle(),
        displayName = displayName,
        hasVideo = hasVideo ?: false,
    )
}
