# CallkeepCore / InProcessCallkeepCore

**Files**:

- `kotlin/com/webtrit/callkeep/services/core/CallkeepCore.kt` (interface)
- `kotlin/com/webtrit/callkeep/services/core/InProcessCallkeepCore.kt` (implementation)
- `kotlin/com/webtrit/callkeep/services/core/CallServiceRouter.kt` (backend routing)

## Responsibility

`CallkeepCore` is the single facade used by the **main process** for all interactions with the
call backend. It combines three concerns:

1. **State queries and mutations** -- the shadow call state held in
   `MainProcessConnectionTracker` (see [connection-tracker.md](connection-tracker.md)).
2. **Command dispatch** -- commands routed through `CallServiceRouter` to either
   `PhoneConnectionService` (Telecom path, `:callkeep_core` process) or
   `StandaloneCallService` (no-Telecom path, main process). Call sites never know which backend
   is active.
3. **Event routing** -- receives `:callkeep_core` broadcasts via a single lazy internal receiver
   and fans them out to registered `ConnectionEventListener` subscribers; also supports direct
   in-process delivery (`notifyConnectionEvent`) for the standalone backend.

All main-process code that needs to know call state, trigger a call action, or subscribe to
connection events goes through `CallkeepCore.instance`.

## Access Pattern

```kotlin
val core = CallkeepCore.instance
core.startAnswerCall(metadata)
val meta = core.get(callId)
```

`InProcessCallkeepCore.instance` is a process-wide companion-object singleton, created eagerly on
first class access. The application context is read per call from `ContextHolder`, not at
construction time, so early singleton creation itself never throws. There is no `Application`
subclass: `ContextHolder.init` runs at each entry point (plugin attach, `WebtritCallkeep`,
service `onCreate`, receiver `onReceive`; the `:callkeep_core` services init their own copy), and
a CS command issued before any entry point has run throws `IllegalStateException` -- the
synchronous-throw case described under `startIncomingCall` below. Swapping the `instance`
assignment is the single point to change IPC strategy without touching call sites.

Known consumers: `ForegroundService`, `ConnectionsApi`, `WebtritCallkeepPlugin` (lock-screen
flags on ON_START), `BackgroundPushNotificationIsolateBootstrapApi`, `ExternalEngineCallApi`,
`IncomingCallService` + its handlers/controller, `ActiveCallService`,
`IncomingCallSmsTriggerReceiver`, `StandaloneCallService` (event delivery), `CallDiagnostics`.

## State Query API

Backed by `MainProcessConnectionTracker`; exact semantics (derived termination, guard behavior,
invariants) are documented in [connection-tracker.md](connection-tracker.md).

| Method                       | Description                                                          |
|------------------------------|----------------------------------------------------------------------|
| `exists(callId)`             | Promoted, non-terminated connection record exists                    |
| `isPending(callId)`          | Sent to Telecom, `PhoneConnection` not yet created                   |
| `getPendingCallIds()`        | Non-destructive snapshot of pending ids                              |
| `isTerminated(callId)`       | Derived: seen before AND absent from all active sets                 |
| `isAnswered(callId)`         | Answer guard was marked (not the same as STATE_ACTIVE)               |
| `checkIncomingDuplicate(id)` | null = free; `CALL_ID_ALREADY_EXISTS[_AND_ANSWERED]` otherwise       |
| `routeAnswerCall(id)`        | `AnswerImmediately` / `DeferAnswer` / `NotFound` -- encodes the answer-path decision for `ForegroundService.answerCall` |
| `get(callId)` / `getAll()`   | `CallMetadata` snapshot(s) of active calls                           |
| `getState(callId)`           | Last mirrored `PCallkeepConnectionState`                             |
| `toPCallkeepConnection(id)`  | Pigeon connection object, null if not active                         |

## State Mutation API

Mutations are driven mostly from `ForegroundService.onConnectionEvent` as broadcasts arrive from
the backend; the facade adds one composite:

| Method                              | Typical trigger                                                | Effect                                              |
|-------------------------------------|----------------------------------------------------------------|-----------------------------------------------------|
| `addPending(callId)`                | own `startIncomingCall`; outgoing `startCall` pre-registration | Registers pending; resets the four per-call guards first (the sticky ghost guard excepted); true = caller owns the entry |
| `removePending(callId)`             | registration failure / timeout / decline-before-confirmation / failed outgoing / tearDown and `onDestroy` rollback | Drops the pending entry only                        |
| `promote(callId, meta, state)`      | `IncomingConnectionReported`; `OngoingCall`; adoption paths    | Full registration; same guard reset as `addPending` (also clears an earlier `markAnswered`) |
| `markAnswered(callId)`              | `AnswerCall` broadcast; adoption paths (after `promote`); `CallLifecycleHandler` fallback when the push isolate is unreachable | Answer guard only; no state stamp                   |
| `updateState(callId, state)`        | `ConnectionStateChanged` broadcast                             | Mirrors authoritative state; unconditional; ignores DISCONNECTED |
| `markTerminated(callId)`            | `reportEndCall`; HungUp/Decline handling                       | Clears active sets; state becomes DISCONNECTED      |
| `clearAndMarkEndCallDispatched(id)` | HungUp/Decline/`ConnectionNotFound` handler, tearDown, `onDestroy`, confirmation timeout | `markTerminated` + drops the main-process `ConnectionManager` pending reservation + marks endCallDispatched (true = first dispatch) |
| `reserveAnswer` / `consumeAnswer`   | deferred-answer path / `AnswerCall` handler                    | Deferred answer bookkeeping                         |
| `drainUnconnectedPendingCallIds()`  | `tearDown`                                                     | Snapshot + clear of pending                         |
| `clear()`                           | end of `tearDown`; `cleanConnections`                          | Full per-session reset                              |
| `markDirectNotified` / `consumeDirectNotified` | `tearDown` / HungUp handler                         | Stale-broadcast suppression                         |
| `markEndCallDispatched(id)`         | `endCall`                                                      | performEndCall dedup; true = first mark             |
| `markEndedWithoutFlutterState` / `wasEndedWithoutFlutterState` | `reportEndCall(MISSED_WHILE_CONNECTING)` / `reportNewIncomingCall` | Sticky ghost-re-presentation guard |

`clearAndMarkEndCallDispatched` is the one sanctioned main-process touch of
`PhoneConnectionService.connectionManager`: it drops the `pendingCallIds` reservation that
`checkAndReservePending` created in the MAIN-process heap, so a transfer-back with the same
callId is not rejected as a duplicate. The general "never call `connectionManager.*` from the
main process" rule concerns connection state, which exists only in the `:callkeep_core` heap --
see the note in [connection-tracker.md](connection-tracker.md).

(`updateMetadata` is a `ConnectionTracker` member, not part of this facade -- external callers
reach it via `startUpdateCall`.)

## Connection Event Listener API

`InProcessCallkeepCore` holds a single lazy `BroadcastReceiver` (`globalReceiver`) registered on
the first `addConnectionEventListener` call and unregistered when the last listener is removed
(ref-counted). Listeners receive events via `onConnectionEvent(event, data)` on the main thread.

| Method                                | Description                                              |
|---------------------------------------|----------------------------------------------------------|
| `addConnectionEventListener(l)`       | Register a persistent global subscriber                  |
| `removeConnectionEventListener(l)`    | Unregister; tears down globalReceiver when list is empty |
| `registerConnectionEvents(...)`       | Register a temporary per-call dynamic receiver           |
| `unregisterConnectionEvents(...)`     | Unregister a temporary receiver                          |
| `notifyConnectionEvent(event, data)`  | Deliver an event directly to listeners and per-call receivers, bypassing ActivityManager broadcast dispatch |

**Global events** (routed to all `ConnectionEventListener` subscribers):
`IncomingConnectionReported`, `ReplayIncomingCall`, `ConnectionStateChanged`, `DeclineCall`,
`HungUp`, `ConnectionNotFound`, `AnswerCall`, `AudioDeviceSet`, `AudioDevicesUpdate`,
`AudioMuting`, `ConnectionHolding`, `SentDTMF`.

**Per-call dynamic receivers** (registered ad-hoc, not via listener):
`OngoingCall`, `OutgoingFailure` (both in `ForegroundService.startCall`), `TearDownComplete`
(tearDown ack).

**Delivery gap**: `IncomingFailure` is dispatched by `PhoneConnectionService`
(`onCreateIncomingConnectionFailed`) and is excluded from the global listener events, but no
main-process receiver currently registers for it -- the event is dropped. Incoming-failure
handling relies on the `HungUp`/`ConnectionNotFound` path instead.

`notifyConnectionEvent` exists for `StandaloneCallService`, which runs in the main process: on
certain OEM devices the system suppresses app-originated `sendBroadcast` calls entirely, so the
standalone backend delivers its lifecycle events as synchronous in-process calls into the same
handlers. Both backends therefore feed the same event pipeline and the same tracker mutations.

## Command Dispatch API

Commands go through `CallServiceRouter`, which picks the backend once at construction via
`TelephonyUtils.isTelecomSupported`: Telecom is considered supported when the
`android.software.telecom` feature flag is present, OR -- fallback for OEM builds that omit the
flag despite having full Telecom -- when `TelephonyManager.phoneType != PHONE_TYPE_NONE`. Only
devices with no telephony at all (Wi-Fi-only tablets, Android Go builds) route to
`StandaloneCallService` in the main process; everything else uses `PhoneConnectionService`
(startService intents into `:callkeep_core`).

### Call Setup

| Method                                     | Description                                       |
|--------------------------------------------|---------------------------------------------------|
| `startIncomingCall(meta, onSuccess, onError)` | Reserve pending + trigger incoming registration |
| `startOutgoingCall(meta)`                  | Trigger outgoing connection creation              |

`startIncomingCall` owns the `pendingCallIds` reservation: it calls `addPending` first and rejects
a concurrent duplicate registration (push isolate vs foreground signaling for the same callId)
with `CALL_ID_ALREADY_EXISTS` if the entry already exists. On any failure -- logical `onError` or a
synchronous throw from the backend -- it drains the reservation exactly once before propagating.
Synchronous throws bypass `onError` and reach Dart as a Pigeon channel-error; callers that
pre-register state must clean it themselves or rely on their own timeout safety-net
(`ForegroundService.reportNewIncomingCall` does the latter).

### In-Call Control

`startAnswerCall`, `startDeclineCall`, `startHungUpCall`, `startEstablishCall`,
`startUpdateCall` (also merges metadata into the tracker), `startSendDtmfCall`,
`startMutingCall`, `startHoldingCall`, `startSpeaker`, `setAudioDevice` -- all take
`CallMetadata` and are routed to the active backend.

### Service Lifecycle

| Method                     | Description                                                            |
|----------------------------|------------------------------------------------------------------------|
| `tearDownService()`        | Reset backend service state for the next session without hanging up (Telecom: `ServiceAction.TearDown`; standalone: `CleanConnections`) |
| `sendTearDownConnections()`| Hang up all connections + await `TearDownComplete` ack                 |
| `sendReserveAnswer(callId)`| Deferred answer applied when the connection is created                 |
| `sendCleanConnections()`   | Clear backend connections without individual hangups (`ServiceAction.CleanConnections` on both backends) |
| `replayAudioState()`       | One-way pull: re-emit audio state (device + mute) to a fresh delegate  |
| `replayConnectionStates()` | One-way pull seeding a freshly attached delegate / restarted main process (cold-start race). For every live connection re-emits `ConnectionStateChanged` (live states only; restores e.g. STATE_ACTIVE for the already-answered adoption), then `AnswerCall` (callId-only metadata) for answered connections, or `ReplayIncomingCall` (full metadata) for still-ringing ones -- the ONLY path by which a fresh delegate learns of a still-ringing call |

## Related Components

- [connection-tracker.md](connection-tracker.md) -- state storage backend and its invariants
- [foreground-service.md](foreground-service.md) -- implements `ConnectionEventListener`, calls the mutation API from `onConnectionEvent()`
- [background-services.md](background-services.md) -- `IncomingCallService` also implements `ConnectionEventListener` (AnswerCall only)
- [phone-connection-service.md](phone-connection-service.md) -- Telecom backend receiving commands
- [ipc-broadcasting.md](ipc-broadcasting.md) -- broadcast events routed through `globalReceiver`
- [dual-process.md](dual-process.md) -- process topology
