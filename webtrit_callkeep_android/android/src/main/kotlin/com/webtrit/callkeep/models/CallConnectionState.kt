package com.webtrit.callkeep.models

import android.telecom.Connection

/**
 * Local (callkeep-owned) representation of a call connection's lifecycle state.
 *
 * Kept independent of the Pigeon-generated `PCallkeepConnectionState`: domain/model code (e.g.
 * [CallMetadata]) uses this type, and conversion to the Pigeon enum happens only at the core/IPC
 * boundary (see MainProcessConnectionTracker). Mirrors the meaningful android.telecom.Connection states.
 *
 * Why not the raw `android.telecom.Connection.STATE_*` ints directly: they are untyped magic numbers,
 * and the no-Telecom StandaloneCallService has no android.telecom.Connection — so a framework int
 * cannot be the shared representation. This owned enum is backend-agnostic and type-safe; Telecom
 * states map in via [fromTelecomState].
 */
enum class CallConnectionState {
    INITIALIZING,
    NEW,
    RINGING,
    DIALING,
    ACTIVE,
    HOLDING,
    DISCONNECTED,
    ;

    companion object {
        /**
         * Map an android.telecom.Connection STATE_* int to the local enum; returns null for states
         * not represented here (e.g. STATE_PULLING_CALL).
         */
        fun fromTelecomState(state: Int): CallConnectionState? =
            when (state) {
                Connection.STATE_INITIALIZING -> INITIALIZING
                Connection.STATE_NEW -> NEW
                Connection.STATE_RINGING -> RINGING
                Connection.STATE_DIALING -> DIALING
                Connection.STATE_ACTIVE -> ACTIVE
                Connection.STATE_HOLDING -> HOLDING
                Connection.STATE_DISCONNECTED -> DISCONNECTED
                else -> null
            }
    }
}
