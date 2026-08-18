# ConnectionTracker / MainProcessConnectionTracker

**Files**:

- `kotlin/com/webtrit/callkeep/services/core/ConnectionTracker.kt` (interface)
- `kotlin/com/webtrit/callkeep/services/core/MainProcessConnectionTracker.kt` (implementation)

## Responsibility

`MainProcessConnectionTracker` is a **shadow call-state registry** living in the main-process JVM.
Because `PhoneConnectionService` and its `ConnectionManager` run in the `:callkeep_core` process,
the main process cannot read their in-memory state. The tracker mirrors Telecom connection state so
the main process and Flutter can query call status at any time without an IPC round-trip.

State is updated from two sources:

- **broadcast events** emitted by `PhoneConnectionService` (or delivered in-process by
  `StandaloneCallService` via `CallkeepCore.notifyConnectionEvent`), handled by
  `ForegroundService.onConnectionEvent`;
- **main-process pre-registration**: `addPending` before the backend confirms a call, plus the
  local lifecycle guards.

Access goes through the `CallkeepCore` facade (see [callkeep-core.md](callkeep-core.md));
`MainProcessConnectionTracker.instance` is injected into `InProcessCallkeepCore` and is not used
directly by other components.

## Data Structures

All collections are `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()`.

| Field              | Type                                                  | Description                                                                    |
|--------------------|-------------------------------------------------------|--------------------------------------------------------------------------------|
| `connections`      | `ConcurrentHashMap<String, CallMetadata>`             | Promoted, non-terminated calls with full metadata                              |
| `connectionStates` | `ConcurrentHashMap<String, PCallkeepConnectionState>` | Last known Telecom state per call. Doubles as the "ever seen" marker for derived termination. Entries are retained after termination (as `STATE_DISCONNECTED`) and removed only by `clear()` |
| `pendingCallIds`   | `MutableSet<String>`                                  | Calls sent to Telecom, `PhoneConnection` not yet created                       |
| `answeredCallIds`  | `MutableSet<String>`                                  | Answer guard (calls the user answered). Guard only: the ACTIVE state itself is mirrored via `updateState` |
| `pendingAnswers`   | `MutableSet<String>`                                  | Deferred answers (user pressed answer before `PhoneConnection` existed)        |

There is **no explicit terminated set**. Termination is derived (see below). The former
`terminatedCallIds` set was removed: it blocked callId reuse (e.g. blind transfer-back) and
allowed "terminated AND active at the same time" corruption.

## Callback Guards

These sets suppress duplicate or stale Dart notifications for the same call:

| Guard                            | Reset on `addPending`/`promote` | Purpose                                                                                                       |
|----------------------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------|
| `directNotifiedCallIds`          | yes                             | Calls notified directly via `performEndCall` in `tearDown()`. Suppresses the stale async `HungUp` broadcast that arrives after the next session starts. Consumed on read (`consumeDirectNotified`) |
| `endCallDispatchedCallIds`       | yes                             | Calls for which `performEndCall` was already dispatched (or a `HungUpCall` IPC sent). Prevents a second dispatch. `markEndCallDispatched` returns true only on first mark |
| `endedWithoutFlutterStateCallIds`| **no (sticky)**                 | Calls the app ended while they were never presented in Flutter state (the call==null signaling-hangup path). Read by `reportNewIncomingCall` to reject EVERY stale ghost re-presentation of the dead call. Sticky by design: a stale handshake can replay the dead incoming several times, and a transfer-back reuses a call the app DID know, so its end never lands here. Cleared only by `clear()` on tearDown |

The asymmetry in the reset column is deliberate and load-bearing: resetting the ghost guard on
reuse would let a replayed dead incoming ring again.

(The `IncomingConnectionReported` event is register-only and does not notify the Flutter delegate,
so no app-reported suppression guard is needed. The foreground delegate learns of an incoming call
from its own signaling or from `ReplayIncomingCall` on delegate attach; the Dart `CallBloc`
deduplicates by callId.)

## Derived Termination

```text
isTerminated(callId) =
    connectionStates.containsKey(callId)     # the call was observed at least once
    AND callId not in connections
    AND callId not in pendingCallIds
    AND callId not in pendingAnswers
    AND callId not in answeredCallIds
```

- The `connectionStates` presence requirement prevents false positives for callIds that were never
  tracked: an unknown callId is "unknown", not "terminated" (otherwise `endCall` would misclassify
  it and fire a spurious `performEndCall`).
- Because termination is derived, `addPending`/`promote` immediately resurrect a reused callId:
  `isTerminated` flips back to false the moment the call re-enters an active set. Transfer-back is
  never blocked.

## State Transitions

Exact write sequences as implemented (order matters for the atomicity notes below):

```text
addPending(callId): Boolean
    answeredCallIds        -= callId   # reset per-call lifecycle state from any prior
    pendingAnswers         -= callId   # use of this callId (e.g. transfer-back);
    endCallDispatchedCallIds -= callId # endedWithoutFlutterStateCallIds is NOT reset
    directNotifiedCallIds  -= callId
    return pendingCallIds.add(callId)  # true = this caller owns the pending entry

promote(callId, metadata, state)
    answeredCallIds        -= callId   # same guard reset as addPending, in case the
    pendingAnswers         -= callId   # push-path skipped addPending; NOTE: this also
    endCallDispatchedCallIds -= callId # clears an earlier markAnswered -- adoption
    directNotifiedCallIds  -= callId   # sites must call markAnswered AFTER promote
    connections[callId]     = metadata
    pendingCallIds         -= callId
    connectionStates[callId] = state   # STATE_RINGING incoming / STATE_DIALING outgoing
                                       # / STATE_ACTIVE when adopting an answered call

markAnswered(callId)
    answeredCallIds += callId          # guard only -- does NOT stamp connectionStates

updateState(callId, state)
    if state == DISCONNECTED: return   # terminal state is owned by markTerminated
    connectionStates[callId] = state   # UNCONDITIONAL: not gated on connections
                                       # membership, callable before promote, survives
                                       # the addPending guard reset

updateMetadata(metadata)
    connections.computeIfPresent(callId) { merge }   # no-op while still pending

markTerminated(callId)
    connections     -= callId
    answeredCallIds -= callId
    pendingCallIds  -= callId
    pendingAnswers  -= callId
    connectionStates[callId] = STATE_DISCONNECTED    # entry retained, not removed

removePending(callId)
    pendingCallIds -= callId           # rollback of a failed registration, nothing else

reserveAnswer(callId) / consumeAnswer(callId)
    pendingAnswers += / -= callId      # deferred answer for the broadcast-lag window

drainUnconnectedPendingCallIds(): Set<String>
    snapshot = pendingCallIds.toSet(); pendingCallIds.clear(); return snapshot

clear()
    all eight collections cleared      # end of tearDown, next session starts clean
```

## Query Methods

| Method                     | Returns                                                                     |
|----------------------------|-----------------------------------------------------------------------------|
| `exists(callId)`           | True if `connections` contains the id (promoted and not terminated)         |
| `isPending(callId)`        | True if in `pendingCallIds`                                                 |
| `getPendingCallIds()`      | Non-destructive snapshot of `pendingCallIds`                                |
| `isTerminated(callId)`     | Derived, see formula above                                                  |
| `isAnswered(callId)`       | True if in `answeredCallIds`                                                |
| `get(callId)`              | `CallMetadata?` from `connections`                                          |
| `getAll()`                 | All entries in `connections` (active calls only)                            |
| `getState(callId)`         | `PCallkeepConnectionState?` from `connectionStates`                         |
| `toPCallkeepConnection(id)`| Pigeon connection built from `connections` + `connectionStates`; null if not in `connections` |
| `wasEndedWithoutFlutterState(id)` | Sticky ghost-guard read (not consumed)                               |

## Semantic Invariants

These are the non-obvious behaviors the rest of the plugin depends on. Any refactor of the tracker
must preserve them (most are pinned by `MainProcessConnectionTrackerTest`):

1. **State survives `addPending`.** `updateState` writes are NOT reset by the `addPending` guard
   reset. `reportNewIncomingCall` relies on this for cold-start adoption: after
   `CALL_ID_ALREADY_EXISTS`, `getState() == STATE_ACTIVE` distinguishes "answered via the
   notification while the main process had no UI" from "still ringing".
2. **A state-only callId reads as terminated.** If `updateState` ran for a callId that is in no
   active set (possible: `ConnectionStateChanged` arrives before any registration), the derived
   formula yields `isTerminated == true`. Consumers treat this as "seen and gone".
3. **`markAnswered` alone does not register a call.** After cold-start replay fires `AnswerCall`
   before `promote`, the call has `isAnswered == true`, `exists == false`, and -- because
   `answeredCallIds` membership blocks the derived formula -- `isTerminated == false`.
4. **`promote` clears the answered guard.** Adoption sites must call `markAnswered` AFTER
   `promote`. `ForegroundService.reportNewIncomingCall` has four promote sites: the three
   already-answered adoptions promote with `STATE_ACTIVE` and re-mark answered right after;
   the fourth (`STATE_RINGING`, the broadcast-lag "still ringing in Telecom" path) deliberately
   does NOT mark answered -- the call is still ringing, and marking it would make
   `checkIncomingDuplicate` report it as already answered.
5. **The ghost guard is sticky.** `endedWithoutFlutterStateCallIds` survives `addPending`/`promote`
   and repeated reads; only `clear()` removes it.
6. **`connectionStates` entries live until `clear()`.** Terminated calls keep their
   `STATE_DISCONNECTED` entry for the rest of the session -- it is the "ever seen" marker that
   makes derived termination and the `endCall` re-fire path work.
7. **`addPending` returning true is an ownership token.** `InProcessCallkeepCore.startIncomingCall`
   uses it to arbitrate concurrent registrations of the same callId (push isolate vs foreground
   signaling): only the inserting caller proceeds and owns the rollback duty.

## Thread Safety and Atomicity

**Serialization (current reality).** Every write path funnels to the main thread:

- Pigeon host APIs declare no `TaskQueue`, so all handlers (`ForegroundService` delegate,
  `ConnectionsApi`, `DiagnosticsApi`, `BackgroundPushNotificationIsolateBootstrapApi`,
  `ExternalEngineCallApi`) run on the main looper;
- broadcast receivers run on the main looper;
- `StandaloneCallService` (main process) delivers its events through
  `CallkeepCore.notifyConnectionEvent`, a synchronous in-process call into the same main-thread
  handlers;
- the only background executor on the call path (`PhoneConnection.audioEndpointChangeExecutor`)
  lives in the `:callkeep_core` process and cannot touch this object.

So writes never actually race today. Concurrency between async callbacks is interleaving on one
thread, which the per-collection atomic operations (`add`, `putIfAbsent`, `computeIfPresent`)
handle correctly.

**Known gap (latent).** Each collection is thread-safe on its own, but a logical transition
mutates several collections sequentially with no lock:

| Transition                                   | Collections touched |
|----------------------------------------------|---------------------|
| `addPending`                                 | 5                   |
| `promote`                                    | 7                   |
| `markTerminated`                             | 5                   |
| `clearAndMarkEndCallDispatched` (facade)     | 5 + `ConnectionManager.pendingCallIds` + 1 |
| `clear`                                      | 8                   |

Note on `clearAndMarkEndCallDispatched`: its `PhoneConnectionService.connectionManager.removePending`
call is the ONE sanctioned main-process use of `connectionManager`. The "never call
`connectionManager.*` from the main process" rule (AGENTS.md, [dual-process.md](dual-process.md))
is about connection state, which lives only in the `:callkeep_core` heap. The `pendingCallIds`
pre-registration is different: `checkAndReservePending` populates it in the MAIN-process heap
during `startIncomingCall`, so the main process must also be the one to drop it -- otherwise a
subsequent `reportNewIncomingCall` with the same callId (blind transfer-back) is permanently
rejected as a duplicate.

A hypothetical off-main-thread reader could observe a half-applied transition. The sharpest
example is callId reuse in `addPending`: between the guard resets and `pendingCallIds.add`, the
callId is in no active set while its `connectionStates` entry (from the previous life) still
exists -- so `isTerminated` is transiently true for a call that is being resurrected. `isTerminated`
itself is the most fragile reader: it reads five collections with no snapshot.

This is not exploitable today (see serialization above) but is a trap for any future
off-main-thread read or write path. The planned hardening is to consolidate per-call state into a
single `ConcurrentHashMap<String, CallRecord>` with immutable records and `compute {}` transitions;
any such refactor must preserve every invariant in the previous section -- in particular the sticky
ghost guard (5), the state-only-record semantics (2), and answered-blocks-terminated (3).

`drainUnconnectedPendingCallIds` and `clear` are cross-call operations and are not atomic across
callIds either way; that is accepted.

## Writers and Readers (interaction map)

**Mutations by trigger:**

| Mutation                | Called from                                                                                  |
|-------------------------|----------------------------------------------------------------------------------------------|
| `addPending`            | `InProcessCallkeepCore.startIncomingCall` (owns the entry); `ForegroundService.startCall` (outgoing pre-registration) |
| `promote`               | `ForegroundService`: `IncomingConnectionReported` handler; `OngoingCall` per-call receiver (outgoing, `STATE_DIALING`); four sites in `reportNewIncomingCall` -- three already-answered adoptions (`STATE_ACTIVE`, each followed by `markAnswered`) and the still-ringing broadcast-lag promote (`STATE_RINGING`, no `markAnswered` -- see invariant 4) |
| `markAnswered`          | `AnswerCall` handler (`handleCSReportAnswerCall`); adoption paths (after `promote`); `CallLifecycleHandler.performAnswerCall` fallback when the push isolate is unreachable |
| `updateState`           | `ConnectionStateChanged` handler (source of truth: `PhoneConnection.onStateChanged` in `:callkeep_core`, or `StandaloneCallService` transitions) |
| `updateMetadata`        | `InProcessCallkeepCore.startUpdateCall` (e.g. mid-call hasVideo toggle)                       |
| `markTerminated`        | `reportEndCall` (synchronous, ahead of the `DeclineCall` echo); via `clearAndMarkEndCallDispatched` in the `HungUp`/`DeclineCall`/`ConnectionNotFound` handler, tearDown steps, and the incoming-confirmation timeout |
| `removePending`         | rollback paths: failed/timed-out incoming registration, decline-before-confirmation (`HungUp`/`DeclineCall` handler with a pending incoming callback), failed outgoing, tearDown step 1b, `ForegroundService.onDestroy` |
| `reserveAnswer`/`consumeAnswer` | `answerCall` deferred path / `AnswerCall` handler                                     |
| `drainUnconnectedPendingCallIds` | `tearDown` step 2                                                                    |
| `clear`                 | end of `tearDown` (after TearDownComplete ack or timeout); `ConnectionsApi.cleanConnections`  |
| guard marks             | `tearDown` and `ForegroundService.onDestroy` (directNotified + endCallDispatched for unresolved pending incomings; onDestroy runs WITHOUT a subsequent `clear()`), `endCall`, `reportEndCall` (`MISSED_WHILE_CONNECTING` arms the ghost guard) |

**Reads:**

- `ForegroundService.answerCall` -- `routeAnswerCall` (exists -> answer now, pending -> defer,
  else error);
- `ForegroundService.endCall` -- `isTerminated` re-fire path + `markEndCallDispatched` dedup;
- `ForegroundService.reportNewIncomingCall` -- ghost guard, `checkIncomingDuplicate`, `getState`
  adoption;
- `deliverIncomingToDelegate` -- `isTerminated` suppresses seeding a dead call into CallBloc;
- `syncScreenWakelock` -- `getAll` hasVideo;
- `WebtritCallkeepPlugin` ON_START -- `getAll` + `getPendingCallIds` decide the lock-screen /
  turn-screen-on flags (pending is included to cover the broadcast-lag window);
- `ConnectionsApi.getConnection/getConnections` -- Flutter-facing snapshot;
- `CallDiagnostics` -- `getAll` in the diagnostics report.

## Test Coverage

`MainProcessConnectionTrackerTest` (63 tests) pins the transition table, derived termination,
callId reuse after termination, the cold-start `markAnswered`-without-`promote` family, and the
sticky ghost guard. `InProcessCallkeepCoreTest` (5 tests) covers only the `startIncomingCall`
pending-ownership contract (concurrent duplicate rejection, drain-on-error, drain-on-throw,
drain-at-most-once).

Known pinning gaps -- close them before any consolidation refactor:

- no test asserts invariant 2 (`updateState`-only callId reads as `isTerminated == true`);
- invariant 1 is pinned in a test NAME only: the "survives addPending reset" test asserts the
  unconditional write but never actually performs `updateState` followed by `addPending`;
- the `clearAndMarkEndCallDispatched` composite (and facade delegation in general) has no test
  at all.

## Related Components

- [callkeep-core.md](callkeep-core.md) -- the facade exposing this state to the rest of the main
  process
- [foreground-service.md](foreground-service.md) -- drives state mutations from broadcast handlers
- [ipc-broadcasting.md](ipc-broadcasting.md) -- the events that trigger state transitions
- [dual-process.md](dual-process.md) -- why the shadow exists at all
