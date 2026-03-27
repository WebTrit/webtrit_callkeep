package com.webtrit.callkeep.services.broadcaster

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current activity lifecycle event and exposes it as a [StateFlow].
 *
 * All writers and readers are in the main process, so a plain in-process
 * [StateFlow] is sufficient — no broadcast transport is needed.
 */
object ActivityLifecycleState {
    private val _flow = MutableStateFlow<Lifecycle.Event?>(null)

    val flow: StateFlow<Lifecycle.Event?> = _flow.asStateFlow()

    val currentValue: Lifecycle.Event?
        get() = _flow.value

    fun setValue(newValue: Lifecycle.Event) {
        _flow.value = newValue
    }
}
