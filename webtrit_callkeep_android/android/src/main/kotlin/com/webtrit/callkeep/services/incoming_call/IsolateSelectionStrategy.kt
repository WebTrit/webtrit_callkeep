package com.webtrit.callkeep.services.incoming_call

import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.SignalingStatus
import com.webtrit.callkeep.dispatchers.SignalingStatusDispatcher

enum class IsolateType {
    MAIN, BACKGROUND
}

interface IsolateSelectionStrategy {
    fun getIsolateType(): IsolateType
}

class SignalingStatusStrategy(private val signalingStatus: SignalingStatus?) : IsolateSelectionStrategy {
    override fun getIsolateType(): IsolateType {
        return if (signalingStatus in listOf(SignalingStatus.CONNECT, SignalingStatus.CONNECTING)) {
            IsolateType.MAIN
        } else {
            IsolateType.BACKGROUND
        }
    }
}

class ActivityStateStrategy : IsolateSelectionStrategy {
    override fun getIsolateType(): IsolateType {
        val state = ActivityHolder.getActivityState()
        return if (state == Lifecycle.Event.ON_RESUME || state == Lifecycle.Event.ON_PAUSE || state == Lifecycle.Event.ON_STOP) {
            IsolateType.MAIN
        } else {
            IsolateType.BACKGROUND
        }
    }

}

object IsolateSelector {
    private const val TAG = "IsolateSelector"

    private fun getStrategy(): IsolateSelectionStrategy {
        return SignalingStatusDispatcher.currentStatus?.let { SignalingStatusStrategy(it) } ?: ActivityStateStrategy()
    }

    fun getIsolateType(): IsolateType {
        val strategy = getStrategy()
        val isolateType = strategy.getIsolateType()
        Log.i(TAG, "IsolateSelector: $strategy -> $isolateType")
        return isolateType
    }

    inline fun executeBasedOnIsolate(
        mainAction: () -> Unit,
        backgroundAction: () -> Unit
    ) {
        when (getIsolateType()) {
            IsolateType.MAIN -> mainAction()
            IsolateType.BACKGROUND -> backgroundAction()
        }
    }

    inline fun executeIfBackground(action: () -> Unit) {
        if (getIsolateType() == IsolateType.BACKGROUND) {
            action()
        }
    }
}
