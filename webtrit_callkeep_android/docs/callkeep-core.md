# CallkeepCore / InProcessCallkeepCore

**Files**:

- `kotlin/com/webtrit/callkeep/services/core/CallkeepCore.kt` (interface)
- `kotlin/com/webtrit/callkeep/services/core/InProcessCallkeepCore.kt` (implementation)

## Responsibility

`CallkeepCore` is the single facade used by the **main process** for all interactions with the
`:callkeep_core` process. It combines three concerns:

1. **State queries** — reads the shadow call state held in `MainProcessConnectionTracker`.
2. **Command dispatch** — sends `startService` intents or broadcasts to `PhoneConnectionService`.
3. **Event routing** — receives all `:callkeep_core` broadcasts via a single internal receiver and
   fans them out to registered `ConnectionEventListener` subscribers.

All main-process code that needs to know call state, trigger a Telecom action, or subscribe to
connection events goes through `CallkeepCore.instance`.

## Access Pattern

```kotlin
val core = CallkeepCore.instance
core.startIncomingCall(context, metadata)
val meta = core.get(callId)
```

`InProcessCallkeepCore` is a singleton; `instance` is set during `ForegroundService.onCreate()`.

## State Query API

| Method                 | Description                                                |
|------------------------|------------------------------------------------------------|
| `exists(callId)`       | True if call is tracked (pending, active, or terminated)   |
| `isPending(callId)`    | Call sent to Telecom but `PhoneConnection` not yet created |
| `isTerminated(callId)` | Call has ended                                             |
| `isAnswered(callId)`   | Call is in STATE_ACTIVE                                    |
| `get(callId)`          | Returns `CallMetadata` or null                             |
| `getAll()`             | All non-terminated calls                                   |
| `getState(callId)`     | `PCallkeepConnectionState` snapshot                        |

State is backed by `MainProcessConnectionTracker`;
see [connection-tracker.md](connection-tracker.md).

## Connection Event Listener API

`InProcessCallkeepCore` holds a single lazy `BroadcastReceiver` (`globalReceiver`) that listens
to all `:callkeep_core` broadcasts. It is registered on the first `addConnectionEventListener`
call and unregistered when the last listener is removed (ref-counted). All registered listeners
receive events via `onConnectionEvent(event, data)` on the main thread.

```kotlin
// Subscribe (e.g. in Service.onCreate())
CallkeepCore.instance.addConnectionEventListener(this)

// Unsubscribe (e.g. in Service.onDestroy())
CallkeepCore.instance.removeConnectionEventListener(this)
```

| Method                                | Description                                              |
|---------------------------------------|----------------------------------------------------------|
| `addConnectionEventListener(l)`       | Register a persistent global subscriber                  |
| `removeConnectionEventListener(l)`    | Unregister; tears down globalReceiver when list is empty |
| `registerConnectionEvents(...)`       | Register a temporary per-call dynamic receiver           |
| `unregisterConnectionEvents(...)`     | Unregister a temporary receiver                          |

**Global events** (routed to all `ConnectionEventListener` subscribers):
`DidPushIncomingCall`, `DeclineCall`, `HungUp`, `ConnectionNotFound`, `AnswerCall`,
`AudioDeviceSet`, `AudioDevicesUpdate`, `AudioMuting`, `ConnectionHolding`, `SentDTMF`.

**Per-call dynamic receivers** (registered ad-hoc, not via listener):
`OngoingCall`, `OutgoingFailure`, `IncomingFailure`, `TearDownComplete`.

## State Mutation API

These are called **from `onConnectionEvent()` in `ForegroundService`** when `:callkeep_core`
reports events via `CallkeepCore`. They update `MainProcessConnectionTracker`.

| Method                             | Triggered by                       | Effect                          |
|------------------------------------|------------------------------------|---------------------------------|
| `addPending(callId)`               | `NotifyPending` intent from CS     | Registers call as pending       |
| `promote(callId, metadata, state)` | `DidPushIncomingCall` broadcast    | Full registration with metadata |
| `markAnswered(callId)`             | `AnswerCall` broadcast             | Transitions to STATE_ACTIVE     |
| `markHeld(callId, onHold)`         | `ConnectionHolding` broadcast      | Updates hold state              |
| `markTerminated(callId)`           | `HungUp` / `DeclineCall` broadcast | Moves to terminated set         |

## Command Dispatch API

These send intents / broadcasts to `PhoneConnectionService` in `:callkeep_core`.

### Call Setup

| Method                         | Mechanism             | Description                          |
|--------------------------------|-----------------------|--------------------------------------|
| `startIncomingCall(ctx, meta)` | `startService` intent | Trigger `onCreateIncomingConnection` |
| `startOutgoingCall(ctx, meta)` | `startService` intent | Trigger `onCreateOutgoingConnection` |

### In-Call Control

| Method                                | Mechanism             | Description                  |
|---------------------------------------|-----------------------|------------------------------|
| `startAnswerCall(ctx, callId)`        | `startService` intent | Answer specific call         |
| `startDeclineCall(ctx, callId)`       | `startService` intent | Decline call                 |
| `startHungUpCall(ctx, callId)`        | `startService` intent | Hang up call                 |
| `startEstablishCall(ctx, callId)`     | `startService` intent | Mark outgoing as established |
| `startUpdateCall(ctx, callId, meta)`  | `startService` intent | Update call metadata         |
| `startMutingCall(ctx, callId, muted)` | `startService` intent | Toggle mute                  |
| `startHoldingCall(ctx, callId, held)` | `startService` intent | Toggle hold                  |
| `startSpeaker(ctx, callId, on)`       | `startService` intent | Toggle speaker               |
| `setAudioDevice(ctx, callId, device)` | `startService` intent | Select audio device          |

### Service Lifecycle

| Method                           | Mechanism                                     | Description                                      |
|----------------------------------|-----------------------------------------------|--------------------------------------------------|
| `tearDownService(ctx)`           | `startService` intent (`CleanConnections`)    | Reset without hanging up                         |
| `sendTearDownConnections(ctx)`   | `startService` intent (`TearDownConnections`) | Hang up all + await `TearDownComplete` broadcast |
| `sendReserveAnswer(ctx, callId)` | `startService` intent                         | Deferred answer for pending call                 |
| `sendSyncAudioState(ctx)`        | `startService` intent                         | Re-emit audio state after hot-restart            |
| `sendSyncConnectionState(ctx)`   | `startService` intent                         | Re-emit connection state after hot-restart       |

## Related Components

- [connection-tracker.md](connection-tracker.md) — state storage backend
- [foreground-service.md](foreground-service.md) — implements `ConnectionEventListener`, calls mutation API from `onConnectionEvent()`
- [background-services.md](background-services.md) — `IncomingCallService` also implements `ConnectionEventListener`
- [phone-connection-service.md](phone-connection-service.md) — receives commands dispatched here
- [ipc-broadcasting.md](ipc-broadcasting.md) — broadcast events routed through `globalReceiver`
