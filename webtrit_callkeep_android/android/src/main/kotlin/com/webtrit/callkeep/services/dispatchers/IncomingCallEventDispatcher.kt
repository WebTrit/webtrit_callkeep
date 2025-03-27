package com.webtrit.callkeep.services.dispatchers

import android.content.Context
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.IncomingCallService
import com.webtrit.callkeep.services.SignalingIsolateService

interface IncomingCallServiceContract {
    fun answerIncomingCall(context: Context, metadata: CallMetadata)
    fun declineIncomingCall(context: Context, metadata: CallMetadata)
}

object ForegroundCallAdapter : IncomingCallServiceContract {
    override fun answerIncomingCall(context: Context, metadata: CallMetadata) {
        SignalingIsolateService.answerCall(context, metadata)
    }

    override fun declineIncomingCall(context: Context, metadata: CallMetadata) {
        SignalingIsolateService.endCall(context, metadata)
    }
}

object IncomingCallAdapter : IncomingCallServiceContract {
    override fun answerIncomingCall(context: Context, metadata: CallMetadata) {
        if (SignalingIsolateService.isRunning) {
            SignalingIsolateService.answerCall(context, metadata)
        } else {
            IncomingCallService.answer(context, metadata)
        }
    }

    override fun declineIncomingCall(context: Context, metadata: CallMetadata) {
        if (SignalingIsolateService.isRunning) {
            SignalingIsolateService.endCall(context, metadata)
        } else {
            IncomingCallService.hangup(context, metadata)
        }
    }
}

object IncomingCallEventDispatcher {
    const val TAG = "IncomingCallEventDispatcher"

    private fun getActiveService(): IncomingCallServiceContract {
        return if (SignalingIsolateService.isRunning) {
            ForegroundCallAdapter
        } else {
            IncomingCallAdapter
        }
    }

    fun answer(context: Context, metadata: CallMetadata) {
        Log.d(TAG, "answer: $metadata")
        getActiveService().answerIncomingCall(context, metadata)
    }

    fun hungUp(context: Context, metadata: CallMetadata) {
        Log.d(TAG, "hungUp: $metadata")
        getActiveService().declineIncomingCall(context, metadata)
    }
}
