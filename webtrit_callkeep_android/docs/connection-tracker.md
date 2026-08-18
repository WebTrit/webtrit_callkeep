# ConnectionTracker / MainProcessConnectionTracker

**Files**:

- `kotlin/com/webtrit/callkeep/services/core/ConnectionTracker.kt` (interface)
- `kotlin/com/webtrit/callkeep/services/core/MainProcessConnectionTracker.kt` (implementation)
- `kotlin/com/webtrit/callkeep/services/core/CallRecord.kt` (the per-call state record)

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

All per-call state lives in ONE map: `calls: ConcurrentHashMap<String, CallRecord>`, where
`CallRecord` is an immutable data class (its own file, `internal` to the core layer). Every
transition replaces the whole record in a single atomic `compute {}` step, so a reader always
observes a consistent snapshot and never a half-applied transition; different calls never block
each other.

| `CallRecord` field       | Type                          | Description                                                                    |
|--------------------------|-------------------------------|--------------------------------------------------------------------------------|
| `metadata`               | `CallMetadata?`               | Full metadata while the call is registered (promoted); null otherwise. `metadata != null` is what `exists()`/`getAll()` report |
| `pending`                | `Boolean`                     | Sent to Telecom, `PhoneConnection` not yet created. Independent of `metadata`: a push-path re-registration can set it on an already-promoted call, so registration and pending are separate fields, not one exclusive phase |
| `state`                  | `PCallkeepConnectionState?`   | Last known Telecom state. Doubles as the "ever seen" marker for derived termination: it survives termination (as `STATE_DISCONNECTED`) and the `addPending` guard reset |
| `answered`               | `Boolean`                     | Answer guard (the user answered). Guard only: the ACTIVE state itself is mirrored via `updateState` |
| `pendingAnswer`          | `Boolean`                     | Deferred answer (user pressed answer before `PhoneConnection` existed)         |

A record with every field at its default is observationally identical to an absent one, so
records are never removed individually -- they live until `clear()` wipes the session (same
memory profile as the former per-callId `connectionStates` entries).

There is **no explicit terminated flag**. Termination is derived (see below). The former
`terminatedCallIds` set was removed: it blocked callId reuse (e.g. blind transfer-back) and
allowed "terminated AND active at the same time" corruption.

## Callback Guards

These record fields suppress duplicate or stale Dart notifications for the same call:

| `CallRecord` field         | Reset on `addPending`/`promote` | Purpose                                                                                                       |
|----------------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------|
| `directNotified`           | yes                             | Termination notified directly via `performEndCall` in `tearDown()`. Suppresses the stale async `HungUp` broadcast that arrives after the next session starts. Consumed on read (`consumeDirectNotified`) |
| `endCallDispatched`        | yes                             | `performEndCall` was already dispatched (or a `HungUpCall` IPC sent). Prevents a second dispatch. `markEndCallDispatched` returns true only on first mark |
| `endedWithoutFlutterState` | **no (sticky)**                 | The app ended this call while it was never presented in Flutter state (the call==null signaling-hangup path). Read by `reportNewIncomingCall` to reject EVERY stale ghost re-presentation of the dead call. Sticky by design: a stale handshake can replay the dead incoming several times, and a transfer-back reuses a call the app DID know, so its end never lands here. Cleared only by `clear()` on tearDown |

The asymmetry in the reset column is deliberate and load-bearing: resetting the ghost guard on
reuse would let a replayed dead incoming ring again.

(The `IncomingConnectionReported` event is register-only and does not notify the Flutter delegate,
so no app-reported suppression guard is needed. The foreground delegate learns of an incoming call
from its own signaling or from `ReplayIncomingCall` on delegate attach; the Dart `CallBloc`
deduplicates by callId.)

## Derived Termination

```text
isTerminated(callId) =
    rec.state != null        # the call was observed at least once ("ever seen")
    AND rec.metadata == null # not registered
    AND !rec.pending
    AND !rec.pendingAnswer
    AND !rec.answered
```

(false when no record exists at all)

- The `state != null` requirement prevents false positives for callIds that were never
  tracked: an unknown callId is "unknown", not "terminated" (otherwise `endCall` would misclassify
  it and fire a spurious `performEndCall`).
- Because termination is derived, `addPending`/`promote` immediately resurrect a reused callId:
  `isTerminated` flips back to false the moment the call becomes pending or registered again.
  Transfer-back is never blocked.
- The read is one immutable record snapshot -- unlike the former multi-collection implementation,
  the answer can never mix facts from mid-transition.

## State Transitions

Every transition is ONE `calls.compute(callId) { rec.copy(...) }` (absent record = empty
defaults; `computeIfPresent` where absent-is-no-op). What each copy changes:

```text
addPending(callId): Boolean
    copy(pending = true,               # return true when the record was not pending yet:
         answered = false,             # this caller owns the pending entry
         pendingAnswer = false,        # guard reset from any prior use of this callId
         endCallDispatched = false,    # (transfer-back); endedWithoutFlutterState is
         directNotified = false)       # sticky, metadata and state are NOT touched

promote(callId, metadata, state)
    copy(metadata = metadata,          # same guard reset as addPending; NOTE: this also
         pending = false,              # clears an earlier markAnswered -- adoption sites
         state = state,                # must call markAnswered AFTER promote.
         answered = false,             # state: STATE_RINGING incoming / STATE_DIALING
         pendingAnswer = false,        # outgoing / STATE_ACTIVE when adopting an
         endCallDispatched = false,    # answered call
         directNotified = false)

markAnswered(callId)
    copy(answered = true)              # guard only -- does NOT stamp state

updateState(callId, state)
    if state == DISCONNECTED: return   # terminal state is owned by markTerminated
    copy(state = state)                # UNCONDITIONAL: not gated on registration,
                                       # callable before promote, survives the
                                       # addPending guard reset

updateMetadata(metadata)
    computeIfPresent: copy(metadata = merge)   # no-op while not promoted

markTerminated(callId)
    copy(metadata = null,              # one atomic step; guards untouched;
         pending = false,              # state entry retained -- the "ever seen" marker
         answered = false,
         pendingAnswer = false,
         state = STATE_DISCONNECTED)

removePending(callId)
    computeIfPresent: copy(pending = false)    # rollback of a failed registration only

reserveAnswer(callId) / consumeAnswer(callId)
    copy(pendingAnswer = true / false) # deferred answer for the broadcast-lag window;
                                       # consume returns the previous value

drainUnconnectedPendingCallIds(): Set<String>
    for each record with pending: copy(pending = false), collect the id
    # atomic per callId, NOT atomic across callIds (same as before)

clear()
    calls.clear()                      # end of tearDown, next session starts clean
```

## Query Methods

| Method                     | Returns                                                                     |
|----------------------------|-----------------------------------------------------------------------------|
| `exists(callId)`           | True if the record's `metadata` is set (promoted and not terminated)        |
| `isPending(callId)`        | True if the record's `pending` flag is set                                  |
| `getPendingCallIds()`      | Non-destructive snapshot of the ids with `pending` set                      |
| `isTerminated(callId)`     | Derived, see formula above (one record snapshot)                            |
| `isAnswered(callId)`       | True if the record's `answered` flag is set                                 |
| `get(callId)`              | The record's `CallMetadata?`                                                |
| `getAll()`                 | Metadata of all records with `metadata` set (active calls only)             |
| `getState(callId)`         | The record's `PCallkeepConnectionState?`                                    |
| `toPCallkeepConnection(id)`| Pigeon connection built from one record; null unless `metadata` is set      |
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
   before `promote`, the call has `isAnswered == true`, `exists == false`, and -- because the
   `answered` flag blocks the derived formula -- `isTerminated == false`.
4. **`promote` clears the answered guard.** Adoption sites must call `markAnswered` AFTER
   `promote`. `ForegroundService.reportNewIncomingCall` has four promote sites: the three
   already-answered adoptions promote with `STATE_ACTIVE` and re-mark answered right after;
   the fourth (`STATE_RINGING`, the broadcast-lag "still ringing in Telecom" path) deliberately
   does NOT mark answered -- the call is still ringing, and marking it would make
   `checkIncomingDuplicate` report it as already answered.
5. **The ghost guard is sticky.** `endedWithoutFlutterState` survives `addPending`/`promote`
   and repeated reads; only `clear()` removes it.
6. **Records (and their `state`) live until `clear()`.** Terminated calls keep their record with
   `state = STATE_DISCONNECTED` for the rest of the session -- it is the "ever seen" marker that
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

So writes never actually race today; the record model additionally guarantees that even a
genuinely concurrent reader or writer would be safe per call.

**Per-call atomicity (the record model).** Every transition is one `compute {}` on the call's
record: atomic per callId, no global lock, different calls never block each other. A reader
always sees a complete before-or-after snapshot -- the historical hazard of the multi-collection
implementation (e.g. a transfer-back reuse looking transiently terminated between the guard
resets and the pending insert, or `isTerminated` mixing facts from five collections mid-write)
is gone by construction.

What is deliberately NOT atomic:

- **Cross-call operations** (`drainUnconnectedPendingCallIds`, `clear`, the `getAll` /
  `getPendingCallIds` snapshots) iterate per key; each key's step is atomic, the whole sweep is
  not. Same behavior as before; accepted.
- **The facade composite** `clearAndMarkEndCallDispatched` spans two objects: one atomic
  tracker transition (`markTerminated` + the dispatch mark) plus
  `PhoneConnectionService.connectionManager.removePending`. That second call is the ONE
  sanctioned main-process use of `connectionManager`: the "never call `connectionManager.*`
  from the main process" rule (AGENTS.md, [dual-process.md](dual-process.md)) is about
  connection state, which lives only in the `:callkeep_core` heap, while the `pendingCallIds`
  pre-registration is populated in the MAIN-process heap by `checkAndReservePending` during
  `startIncomingCall` -- so the main process must also be the one to drop it, or a subsequent
  `reportNewIncomingCall` with the same callId (blind transfer-back) is permanently rejected as
  a duplicate.

Any further refactor must preserve every invariant in the previous section -- in particular the
sticky ghost guard (5), the state-only-record semantics (2), and answered-blocks-terminated (3).

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

`MainProcessConnectionTrackerTest` (65 tests) pins the transition table, derived termination,
callId reuse after termination, the cold-start `markAnswered`-without-`promote` family, the
sticky ghost guard, and -- added specifically to protect the record consolidation -- the
state-only-reads-as-terminated invariant (2) and the real `updateState` -> `addPending`
cold-start order (invariant 1). `InProcessCallkeepCoreTest` (6 tests) covers the
`startIncomingCall` pending-ownership contract (concurrent duplicate rejection, drain-on-error,
drain-on-throw, drain-at-most-once) and the `clearAndMarkEndCallDispatched` composite
(tracker termination + main-process `ConnectionManager` reservation drop + dispatch dedup).

Each of the three pinning tests was proven to fail -- against both the old multi-collection
implementation and the current record one -- by temporarily breaking the exact line it guards.

## Related Components

- [callkeep-core.md](callkeep-core.md) -- the facade exposing this state to the rest of the main
  process
- [foreground-service.md](foreground-service.md) -- drives state mutations from broadcast handlers
- [ipc-broadcasting.md](ipc-broadcasting.md) -- the events that trigger state transitions
- [dual-process.md](dual-process.md) -- why the shadow exists at all
