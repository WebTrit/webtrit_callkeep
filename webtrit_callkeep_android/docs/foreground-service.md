# ForegroundService

**File**: `kotlin/com/webtrit/callkeep/services/services/foreground/ForegroundService.kt`

**Extends**: `Service`

**Implements**: `PHostApi` (Pigeon-generated), `ConnectionEventListener`

**Annotation**: `@Keep` (must not be renamed or removed by ProGuard/R8)

## Responsibility

`ForegroundService` is the central coordinator in the **main process**. It:

- Serves as a bound service that the Flutter activity binds to for its lifetime.
- Implements the `PHostApi` Pigeon interface — all call-control commands from Dart arrive here.
- Implements `ConnectionEventListener` — receives call lifecycle events from `CallkeepCore` and
  forwards them to the Flutter layer via `PDelegateFlutterApi`.
- Manages phone account registration and notification channels.
- Bridges Android Telecom (indirect, via `CallkeepCore`) with the Flutter/Dart world.

## Lifecycle

### `onCreate()`

- Calls `CallkeepCore.instance.addConnectionEventListener(this)` to subscribe to all
  `:callkeep_core` events routed through the core's single global receiver.
- Sends `SyncConnectionState` command to `:callkeep_core` — if the service was killed and
  restarted, `:callkeep_core` re-emits current connection state so the main process catches up.

### `onBind(intent)`

- Returns the `IBinder` that `WebtritCallkeepPlugin` uses to obtain the service reference.

### `onDestroy()`

- Calls `CallkeepCore.instance.removeConnectionEventListener(this)` to unsubscribe.
- Tears down audio and notification managers.

## Pigeon Host API Implementation (`PHostApi`)

### Setup / Teardown

| Method                             | Behavior                                                                                                                                    |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `setUp(handle, ringtonePath, ...)` | Registers phone account via `TelephonyUtils`, initializes notification channels (with retry on failure), stores config in `StorageDelegate` |
| `tearDown()`                       | Calls `sendTearDownConnections()`, awaits `TearDownComplete` broadcast, then cleans up connections and notifies Dart                        |

### Call Reporting

| Method                                       | Behavior                                               |
|----------------------------------------------|--------------------------------------------------------|
| `reportNewIncomingCall(callId, meta)`        | `TelephonyUtils.addNewIncomingCall()` + update tracker |
| `reportConnectingOutgoingCall(callId, meta)` | Mark call as pending in tracker                        |
| `reportConnectedOutgoingCall(callId, meta)`  | Mark call as established                               |
| `reportEndCall(callId)`                      | Force-terminate call in tracker and notify Dart        |
| `reportUpdateCall(callId, meta)`             | Update call metadata                                   |

### Call Control

| Method                           | Behavior                                                                                                               |
|----------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `startCall(callId, meta)`        | `CallkeepCore.startOutgoingCall()`                                                                                     |
| `answerCall(callId)`             | Deferred if `PhoneConnection` not yet created (stored in `pendingAnswers`); otherwise `CallkeepCore.startAnswerCall()` |
| `endCall(callId)`                | `CallkeepCore.startHungUpCall()`                                                                                       |
| `setMuted(callId, muted)`        | `CallkeepCore.startMutingCall()`                                                                                       |
| `setHeld(callId, held)`          | `CallkeepCore.startHoldingCall()`                                                                                      |
| `setSpeaker(callId, on)`         | `CallkeepCore.startSpeaker()`                                                                                          |
| `setAudioDevice(callId, device)` | `CallkeepCore.setAudioDevice()`                                                                                        |
| `sendDTMF(callId, digit)`        | `CallkeepCore.startSendDtmf()`                                                                                         |

## Connection Event Listener: `onConnectionEvent()`

Events arrive from `CallkeepCore` via `onConnectionEvent(event, data)`. `CallkeepCore` holds a
single `globalReceiver` that receives all `:callkeep_core` broadcasts and fans them out to every
registered `ConnectionEventListener`. `ForegroundService` does not register its own
`BroadcastReceiver` directly.

**Global events** (received via `ConnectionEventListener`):

| Event                 | Handler                               | Main Action                                                              |
|-----------------------|---------------------------------------|--------------------------------------------------------------------------|
| `DidPushIncomingCall` | `handleCSReportDidPushIncomingCall()` | Promote call in tracker, call `performIncomingCall()` on Dart delegate   |
| `AnswerCall`          | `handleCSReportAnswerCall()`          | `markAnswered()` in tracker, call `performAnswerCall()` on Dart delegate |
| `DeclineCall`         | `handleCSReportDeclineCall()`         | `markTerminated()`, call `performEndCall()`                              |
| `HungUp`              | `handleCSReportDeclineCall()`         | Same as DeclineCall                                                      |
| `ConnectionNotFound`  | `handleCSConnectionNotFound()`        | Synthesize HungUp — `performEndCall()`                                   |
| `AudioMuting`         | Inline                                | Call `performMuteCall()` on Dart delegate                                |
| `AudioDeviceSet`      | Inline                                | Call `performSetAudioDevice()`                                           |
| `AudioDevicesUpdate`  | Inline                                | Call `performUpdateAudioDevices()`                                       |
| `ConnectionHolding`   | Inline                                | Call `performHoldCall()`                                                 |
| `SentDTMF`            | Inline                                | Call `performSendDTMF()`                                                 |

**Per-call dynamic receivers** (registered ad-hoc via `CallkeepCore.registerConnectionEvents()`):

| Event             | Handler                           | Main Action                              |
|-------------------|-----------------------------------|------------------------------------------|
| `OngoingCall`     | `handleCSReportOngoingCall()`     | Promote outgoing call, notify Dart       |
| `OutgoingFailure` | `handleCSReportOutgoingFailure()` | `markTerminated()`, notify Dart          |
| `IncomingFailure` | `handleCSReportIncomingFailure()` | `markTerminated()`, notify Dart          |
| `TearDownComplete`| Inline lambda                     | Completes the `tearDown()` deferred      |

## Duplicate-Notification Guards

To prevent sending the same event to Dart twice (e.g., from both the direct tearDown path and a
stale broadcast), `MainProcessConnectionTracker` maintains guard sets. `ForegroundService` checks
these before dispatching:

- `directNotifiedCallIds` — suppress `HungUp` broadcast if tearDown already notified this call.
- `endCallDispatchedCallIds` — suppress second `performEndCall()` for the same call.

## Related Components

- [callkeep-core.md](callkeep-core.md) — all Telecom commands go through here
- [connection-tracker.md](connection-tracker.md) — state mutated here on broadcast events
- [pigeon-apis.md](pigeon-apis.md) — `PHostApi` and `PDelegateFlutterApi` definitions
- [callkeep-core.md](callkeep-core.md) — `ConnectionEventListener` API and event routing
- [ipc-broadcasting.md](ipc-broadcasting.md) — cross-process broadcast transport
