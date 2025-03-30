package com.webtrit.callkeep.services.common

import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.models.SignalingStatus
import com.webtrit.callkeep.services.broadcaster.ActivityLifecycleBroadcaster
import com.webtrit.callkeep.services.broadcaster.SignalingStatusBroadcaster

enum class IsolateType {
    MAIN, BACKGROUND
}

interface IsolateSelectionStrategy {
    fun getIsolateType(): IsolateType
}

/**
 * SignalingStatusStrategy determines the isolate type based on the current signaling status.
 *
 * If the signaling status is CONNECT or CONNECTING, it returns MAIN isolate type.
 * Otherwise, it returns BACKGROUND isolate type.
 */
class SignalingStatusStrategy(private val signalingStatus: SignalingStatus?) : IsolateSelectionStrategy {
    override fun getIsolateType(): IsolateType {
        return if (signalingStatus in listOf(SignalingStatus.CONNECT, SignalingStatus.CONNECTING)) {
            IsolateType.MAIN
        } else {
            IsolateType.BACKGROUND
        }
    }
}

/**
 * ActivityStateStrategy determines the isolate type based on the current activity lifecycle state.
 *
 * If the activity is in the ON_RESUME, ON_PAUSE, or ON_STOP state, it returns MAIN isolate type.
 * Otherwise, it returns BACKGROUND isolate type.
 */
class ActivityStateStrategy : IsolateSelectionStrategy {
    override fun getIsolateType(): IsolateType {
        val state = ActivityLifecycleBroadcaster.currentValue
        return if (state == Lifecycle.Event.ON_RESUME || state == Lifecycle.Event.ON_PAUSE || state == Lifecycle.Event.ON_STOP) {
            IsolateType.MAIN
        } else {
            IsolateType.BACKGROUND
        }
    }
}

/**
 * IsolateSelector is responsible for determining the type of isolate to be used based on the current
 * state of the application and the signaling status.
 *
 * It provides methods to execute actions based on the isolate type and to check if the current
 * isolate type is background.
 */
object IsolateSelector {
    private const val TAG = "IsolateSelector"

    private fun getStrategy(): IsolateSelectionStrategy {
        return SignalingStatusBroadcaster.currentValue?.let { SignalingStatusStrategy(it) } ?: ActivityStateStrategy()
    }

    // Determines the isolate type based on the current strategy
    fun getIsolateType(): IsolateType {
        val strategy = getStrategy()
        val isolateType = strategy.getIsolateType()
        Log.i(TAG, "IsolateSelector: $strategy -> $isolateType")
        return isolateType
    }

    // Executes the action based on the current isolate type
    inline fun executeBasedOnIsolate(
        mainAction: () -> Unit,
        backgroundAction: () -> Unit
    ) {
        when (getIsolateType()) {
            IsolateType.MAIN -> mainAction()
            IsolateType.BACKGROUND -> backgroundAction()
        }
    }

    // Executes the action if the current isolate type is MAIN
    inline fun executeIfBackground(action: () -> Unit) {
        if (getIsolateType() == IsolateType.BACKGROUND) {
            action()
        }
    }
}
