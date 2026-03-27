package com.webtrit.callkeep.services.broadcaster

import com.webtrit.callkeep.models.SignalingStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Holds the current signaling status and exposes changes as a [SharedFlow].
 *
 * All writers and readers are in the main process — no broadcast transport needed.
 *
 * [updates] uses [SharedFlow] with no replay so that every [setValue] call
 * delivers an event to active collectors regardless of whether the value changed.
 * This matches the behaviour of the previous [sendBroadcast]-based approach,
 * which always sent a broadcast on each [setValue] without deduplication.
 */
object SignalingStatusState {
    @Volatile
    private var _currentValue: SignalingStatus? = null

    private val _updates =
        MutableSharedFlow<SignalingStatus>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val updates: SharedFlow<SignalingStatus> = _updates.asSharedFlow()

    val currentValue: SignalingStatus?
        get() = _currentValue

    fun setValue(newValue: SignalingStatus) {
        _currentValue = newValue
        _updates.tryEmit(newValue)
    }
}
