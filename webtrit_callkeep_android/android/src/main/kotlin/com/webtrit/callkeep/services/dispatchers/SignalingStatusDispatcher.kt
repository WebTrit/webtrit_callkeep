package com.webtrit.callkeep.services.dispatchers

import android.annotation.SuppressLint
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.SignalingStatus

@SuppressLint("StaticFieldLeak")
object SignalingStatusDispatcher {
    private const val TAG = "SignalingHolder"

    private var status: SignalingStatus? = null

    fun getStatus() = status

    fun setStatus(status: SignalingStatus) {
        Log.d(TAG, "SignalingHolder:setStatus: $status")
        this.status = status
    }
}
