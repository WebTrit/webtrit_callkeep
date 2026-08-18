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
first class access. The application context is read per call from `ContextHolder` (initialized in
`Application.onCreate`), so early singleton creation is safe. Swapping the `instance` assignment
is the single point to change IPC strategy without touching call sites.

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
| `addPending(callId)`                | own `startIncomingCall`; outgoing `startCall` pre-registration | Registers pending; true = caller owns the entry     |
| `removePending(callId)`             | registration failure / timeout / tearDown rollback             | Drops the pending entry only                        |
| `promote(callId, meta, state)`      | `IncomingConnectionReported`; `OngoingCall`; adoption paths    | Full registration (resets per-call guards first)    |
| `markAnswered(callId)`              | `AnswerCall` broadcast; adoption paths (after `promote`)       | Answer guard only; no state stamp                   |
| `updateState(callId, state)`        | `ConnectionStateChanged` broadcast                             | Mirrors authoritative state; unconditional; ignores DISCONNECTED |
| `updateMetadata(meta)`              | `startUpdateCall`                                              | Merge into promoted record; no-op while pending     |
| `markTerminated(callId)`            | `reportEndCall`; HungUp/Decline handling                       | Clears active sets; state becomes DISCONNECTED      |
| `clearAndMarkEndCallDispatched(id)` | HungUp/Decline handler, tearDown, confirmation timeout         | `markTerminated` + drops the main-process `ConnectionManager` pending reservation + marks endCallDispatched (true = first dispatch) |
| `reserveAnswer` / `consumeAnswer`   | deferred-answer path / `AnswerCall` handler                    | Deferred answer bookkeeping                         |
| `drainUnconnectedPendingCallIds()`  | `tearDown`                                                     | Snapshot + clear of pending                         |
| `clear()`                           | end of `tearDown`; `cleanConnections`                          | Full per-session reset                              |
| `markDirectNotified` / `consumeDirectNotified` | `tearDown` / HungUp handler                         | Stale-broadcast suppression                         |
| `markEndCallDispatched(id)`         | `endCall`                                                      | performEndCall dedup; true = first mark             |
| `markEndedWithoutFlutterState` / `wasEndedWithoutFlutterState` | `reportEndCall(MISSED_WHILE_CONNECTING)` / `reportNewIncomingCall` | Sticky ghost-re-presentation guard |

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
`OngoingCall`, `OutgoingFailure`, `IncomingFailure`, `TearDownComplete`.

`notifyConnectionEvent` exists for `StandaloneCallService`, which runs in the main process: on
certain OEM devices the system suppresses app-originated `sendBroadcast` calls entirely, so the
standalone backend delivers its lifecycle events as synchronous in-process calls into the same
handlers. Both backends therefore feed the same event pipeline and the same tracker mutations.

## Command Dispatch API

Commands go through `CallServiceRouter`, which picks the backend once at construction:
devices exposing `android.software.telecom` use `PhoneConnectionService` (startService intents
into `:callkeep_core`); devices without it (some tablets, Android Go, certain OEM configs) use
`StandaloneCallService` in the main process.

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
| `tearDownService()`        | Reset backend service state for the next session without hanging up    |
| `sendTearDownConnections()`| Hang up all connections + await `TearDownComplete` ack                 |
| `sendReserveAnswer(callId)`| Deferred answer applied when the connection is created                 |
| `sendCleanConnections()`   | Clear backend connections without individual hangups                   |
| `replayAudioState()`       | One-way pull: re-emit audio state (device + mute) to a fresh delegate  |
| `replayConnectionStates()` | One-way pull: replay connection lifecycle (re-fires `AnswerCall` for answered calls) so a freshly attached delegate / restarted main process is seeded (cold-start race) |

## Related Components

- [connection-tracker.md](connection-tracker.md) -- state storage backend and its invariants
- [foreground-service.md](foreground-service.md) -- implements `ConnectionEventListener`, calls the mutation API from `onConnectionEvent()`
- [background-services.md](background-services.md) -- `IncomingCallService` also implements `ConnectionEventListener` (AnswerCall only)
- [phone-connection-service.md](phone-connection-service.md) -- Telecom backend receiving commands
- [ipc-broadcasting.md](ipc-broadcasting.md) -- broadcast events routed through `globalReceiver`
- [dual-process.md](dual-process.md) -- process topology
