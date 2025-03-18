package com.webtrit.callkeep.events

import android.content.Context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.IncomingCallService
import com.webtrit.callkeep.services.callkeep.foreground.ForegroundCallService

interface IncomingCallServiceContract {
    fun answerIncomingCall(context: Context, metadata: CallMetadata)
    fun declineIncomingCall(context: Context, metadata: CallMetadata)
}

object ForegroundCallAdapter : IncomingCallServiceContract {
    override fun answerIncomingCall(context: Context, metadata: CallMetadata) {
        ForegroundCallService.answerCall(context, metadata)
    }

    override fun declineIncomingCall(context: Context, metadata: CallMetadata) {
        ForegroundCallService.endCall(context, metadata)
    }
}

object IncomingCallAdapter : IncomingCallServiceContract {
    override fun answerIncomingCall(context: Context, metadata: CallMetadata) {
        if (ForegroundCallService.isRunning.get()) {
            ForegroundCallService.answerCall(context, metadata)
        } else {
            IncomingCallService.answer(context, metadata)
        }
    }

    override fun declineIncomingCall(context: Context, metadata: CallMetadata) {
        if (ForegroundCallService.isRunning.get()) {
            ForegroundCallService.endCall(context, metadata)
        } else {
            IncomingCallService.hangup(context, metadata)
        }
    }
}

object IncomingCallEventDispatcher {
    private fun getActiveService(): IncomingCallServiceContract {
        return if (ForegroundCallService.isRunning.get()) {
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
