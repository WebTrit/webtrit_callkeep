package com.webtrit.callkeep.services.dispatchers

import android.content.Context
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.PushNotificationService
import com.webtrit.callkeep.services.SignalingService

interface IncomingCallServiceContract {
    fun answerIncomingCall(context: Context, metadata: CallMetadata)
    fun declineIncomingCall(context: Context, metadata: CallMetadata)
}

object ForegroundCallAdapter : IncomingCallServiceContract {
    override fun answerIncomingCall(context: Context, metadata: CallMetadata) {
        SignalingService.answerCall(context, metadata)
    }

    override fun declineIncomingCall(context: Context, metadata: CallMetadata) {
        SignalingService.endCall(context, metadata)
    }
}

object IncomingCallAdapter : IncomingCallServiceContract {
    override fun answerIncomingCall(context: Context, metadata: CallMetadata) {
        if (SignalingService.isRunning) {
            SignalingService.answerCall(context, metadata)
        } else {
            PushNotificationService.answer(context, metadata)
        }
    }

    override fun declineIncomingCall(context: Context, metadata: CallMetadata) {
        if (SignalingService.isRunning) {
            SignalingService.endCall(context, metadata)
        } else {
            PushNotificationService.hangup(context, metadata)
        }
    }
}

object IncomingCallEventDispatcher {
    const val TAG = "IncomingCallEventDispatcher"

    private fun getActiveService(): IncomingCallServiceContract {
        return if (SignalingService.isRunning) {
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
