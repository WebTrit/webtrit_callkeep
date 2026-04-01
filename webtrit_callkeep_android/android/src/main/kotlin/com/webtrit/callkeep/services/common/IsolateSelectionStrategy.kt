package com.webtrit.callkeep.services.common

import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.services.broadcaster.ActivityLifecycleState

enum class IsolateType {
    MAIN,
    BACKGROUND,
}

interface IsolateSelectionStrategy {
    fun getIsolateType(): IsolateType
}

/**
 * ActivityStateStrategy determines the isolate type based on the current activity lifecycle state.
 *
 * If the activity is in the ON_RESUME, ON_PAUSE, or ON_STOP state, it returns MAIN isolate type.
 * Otherwise, it returns BACKGROUND isolate type.
 */
class ActivityStateStrategy : IsolateSelectionStrategy {
    override fun getIsolateType(): IsolateType {
        val state = ActivityLifecycleState.currentValue
        return if (state == Lifecycle.Event.ON_RESUME || state == Lifecycle.Event.ON_PAUSE || state == Lifecycle.Event.ON_STOP) {
            IsolateType.MAIN
        } else {
            IsolateType.BACKGROUND
        }
    }
}

/**
 * IsolateSelector is responsible for determining the type of isolate to be used based on the current
 * activity lifecycle state.
 *
 * It provides methods to execute actions based on the isolate type and to check if the current
 * isolate type is background.
 */
object IsolateSelector {
    private const val TAG = "IsolateSelector"

    // Determines the isolate type based on the current activity lifecycle state
    fun getIsolateType(): IsolateType {
        val strategy = ActivityStateStrategy()
        val isolateType = strategy.getIsolateType()
        Log.i(TAG, "IsolateSelector: $strategy -> $isolateType")
        return isolateType
    }

    // Executes the action based on the current isolate type
    inline fun executeBasedOnIsolate(
        mainAction: () -> Unit,
        backgroundAction: () -> Unit,
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
