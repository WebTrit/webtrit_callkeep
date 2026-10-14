package com.webtrit.callkeep.services.core

import com.webtrit.callkeep.PCallkeepConnectionState
import com.webtrit.callkeep.models.CallMetadata

/**
 * The complete per-call state of [MainProcessConnectionTracker], one immutable record per callId.
 *
 * Every lifecycle transition replaces the whole record in a single atomic
 * `ConcurrentHashMap.compute` step, so a reader always observes a consistent snapshot and never
 * a half-applied transition. The fields deliberately mirror the independent facts the tracker
 * has always kept — a call CAN be both registered and pending for a moment (a push-path
 * re-registration of a call the app already knows), so [metadata] and [pending] are separate
 * fields, not one exclusive phase.
 *
 * A record with every field at its default is observationally identical to an absent one
 * (all tracker queries return the same answers), so records are never removed individually —
 * they live until [MainProcessConnectionTracker.clear] wipes the session. This also preserves
 * the "ever seen" marker: [state] stays set for a terminated call, which the derived
 * [MainProcessConnectionTracker.isTerminated] and the cold-start adoption in
 * reportNewIncomingCall rely on.
 *
 * The class is internal to the core layer: only [MainProcessConnectionTracker] creates and
 * stores records (its `calls` map is private), everything else reads through the
 * [ConnectionTracker] query API.
 */
internal data class CallRecord(
    // Full metadata while the call is registered (promoted); null otherwise.
    // `metadata != null` is what exists()/getAll() report.
    val metadata: CallMetadata? = null,
    // Registered with Telecom but PhoneConnection not yet created. Independent of
    // `metadata`: a push-path re-registration can set it on an already-promoted call.
    val pending: Boolean = false,
    // Last known Pigeon connection state, mirrored from the real
    // android.telecom.Connection. Doubles as the "ever seen" marker: it survives
    // termination (as STATE_DISCONNECTED) and the addPending guard reset.
    val state: PCallkeepConnectionState? = null,
    // The call has been answered by the user (lifecycle guard for
    // isAnswered/checkIncomingDuplicate; the ACTIVE state itself arrives via updateState).
    val answered: Boolean = false,
    // answerCall was requested before the PhoneConnection was created (deferred answer).
    val pendingAnswer: Boolean = false,
    // endCall() already dispatched a HungUpCall IPC or re-fired performEndCall for a
    // Telecom-terminated call. Prevents duplicate performEndCall.
    val endCallDispatched: Boolean = false,
    // Termination was directly notified via performEndCall in tearDown(). Suppresses the
    // stale async HungUp broadcast that arrives after the new session starts.
    val directNotified: Boolean = false,
    // The app ended this call while it was never presented in Flutter state (the call==null
    // signaling-hangup path). Used by reportNewIncomingCall to reject EVERY stale ghost
    // re-presentation of such a call (a stale handshake can replay the dead incoming several
    // times, so this is a sticky flag, not one-shot). Deliberately NOT reset by
    // addPending/promote — a transfer-back always reuses a call the app DID know, so its end
    // never lands here, making this a semantic discriminator rather than a timing bet.
    // Cleared only via MainProcessConnectionTracker.clear() on tearDown.
    val endedWithoutFlutterState: Boolean = false,
)
