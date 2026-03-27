package com.webtrit.callkeep.services.broadcaster

import com.webtrit.callkeep.models.SignalingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current signaling status and exposes it as a [StateFlow].
 *
 * All writers and readers are in the main process, so a plain in-process
 * [StateFlow] is sufficient — no broadcast transport is needed.
 */
object SignalingStatusState {
    private val _flow = MutableStateFlow<SignalingStatus?>(null)

    val flow: StateFlow<SignalingStatus?> = _flow.asStateFlow()

    val currentValue: SignalingStatus?
        get() = _flow.value

    fun setValue(newValue: SignalingStatus) {
        _flow.value = newValue
    }
}
