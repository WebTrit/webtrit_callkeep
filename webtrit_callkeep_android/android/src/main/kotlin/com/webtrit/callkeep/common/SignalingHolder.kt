package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import com.webtrit.callkeep.PCallkeepSignalingStatus

@SuppressLint("StaticFieldLeak")
object SignalingHolder {
    private const val TAG = "SignalingHolder"

    private var status: PCallkeepSignalingStatus? = null

    fun getStatus() = status

    fun setStatus(status: PCallkeepSignalingStatus) {
        Log.d(TAG, "SignalingHolder:setStatus: $status")
        this.status = status
    }
}
