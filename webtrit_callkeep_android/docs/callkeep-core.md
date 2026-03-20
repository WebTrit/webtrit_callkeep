# CallkeepCore / InProcessCallkeepCore

**Files**:

- `kotlin/com/webtrit/callkeep/services/core/CallkeepCore.kt` (interface)
- `kotlin/com/webtrit/callkeep/services/core/InProcessCallkeepCore.kt` (implementation)

## Responsibility

`CallkeepCore` is the single facade used by the **main process** for all interactions with the
`:callkeep_core` process. It combines two concerns:

1. **State queries** — reads the shadow call state held in `MainProcessConnectionTracker`.
2. **Command dispatch** — sends `startService` intents or broadcasts to `PhoneConnectionService`.

All main-process code that needs to know call state or trigger a Telecom action goes through
`CallkeepCore.instance`, never through `PhoneConnectionService` directly.

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

## State Mutation API

These are called **from broadcast handlers in `ForegroundService`** when `:callkeep_core` reports
events. They update `MainProcessConnectionTracker`.

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
- [foreground-service.md](foreground-service.md) — calls mutation API from broadcast handlers
- [phone-connection-service.md](phone-connection-service.md) — receives commands dispatched here
- [ipc-broadcasting.md](ipc-broadcasting.md) — broadcast events that drive state mutations
