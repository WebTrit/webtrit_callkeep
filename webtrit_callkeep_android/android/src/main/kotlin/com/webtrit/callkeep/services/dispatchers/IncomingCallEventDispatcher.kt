package com.webtrit.callkeep.services.dispatchers

import android.content.Context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.IncomingCallService
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
        if (SignalingService.isRunning.get()) {
            SignalingService.answerCall(context, metadata)
        } else {
            IncomingCallService.answer(context, metadata)
        }
    }

    override fun declineIncomingCall(context: Context, metadata: CallMetadata) {
        if (SignalingService.isRunning.get()) {
            SignalingService.endCall(context, metadata)
        } else {
            IncomingCallService.hangup(context, metadata)
        }
    }
}

object IncomingCallEventDispatcher {
    private fun getActiveService(): IncomingCallServiceContract {
        return if (SignalingService.isRunning.get()) {
            ForegroundCallAdapter
        } else {
            IncomingCallAdapter
        }
    }

    fun answer(context: Context, metadata: CallMetadata) {
        getActiveService().answerIncomingCall(context, metadata)
    }

    fun hungUp(context: Context, metadata: CallMetadata) {
        getActiveService().declineIncomingCall(context, metadata)
    }
}
