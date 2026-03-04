# PhoneConnectionService

**Package:** `com.webtrit.callkeep.services.services.connection`
**Process:** `:callkeep_core`
**Type:** Android `ConnectionService` (system-managed lifecycle)
**Declared permission:** `android.permission.BIND_TELECOM_CONNECTION_SERVICE`

---

## Role

The Android `ConnectionService` implementation. The Telecom framework binds to it when a call is
placed or received. It:

- Owns all `PhoneConnection` objects (one per active call) via `ConnectionManager`.
- Creates incoming/outgoing connections in response to Telecom callbacks.
- Forwards all call events back to the main process via `ConnectionServicePerformBroadcaster`.
- Receives action intents from the main process (via `startService`) and dispatches them to the
  appropriate `PhoneConnection`.

---

## Service lifecycle

```
System binds (Telecom) → onCreate()
    ├── ContextHolder.init(applicationContext)  // own process initialization
    ├── isRunning = true
    ├── creates PhoneConnectionServiceDispatcher
    └── creates ActivityWakelockManager + ProximitySensorManager

Incoming call:
    TelecomManager.addNewIncomingCall() → onCreateIncomingConnection()
        ├── duplicate/existing-incoming guard (via ConnectionManager)
        ├── PhoneConnection.createIncomingPhoneConnection()
        └── broadcast ConnectionPerform.ConnectionAdded

Outgoing call:
    TelecomManager.placeCall() → onCreateOutgoingConnection()
        ├── duplicate guard
        ├── PhoneConnection.createOutgoingPhoneConnection()
        └── broadcast ConnectionPerform.ConnectionAdded

Actions from main process (via startService intents):
    onStartCommand() → PhoneConnectionServiceDispatcher.dispatch(ServiceAction, metadata)

App swiped away:
    onTaskRemoved() → cleanupResources() → stopSelf()
```

---

## `PhoneConnection` state machine

```
createIncomingPhoneConnection()  →  STATE_RINGING
createOutgoingPhoneConnection()  →  STATE_DIALING

STATE_RINGING
    ├── onAnswer()     → STATE_ACTIVE  + broadcast AnswerCall
    └── onDisconnect() → STATE_DISCONNECTED + broadcast DeclineCall

STATE_DIALING
    ├── establish()    → STATE_ACTIVE  + broadcast OngoingCall
    └── onDisconnect() → STATE_DISCONNECTED + broadcast HungUp

STATE_ACTIVE
    ├── onHold()       → STATE_HOLDING + broadcast ConnectionHolding
    ├── onDisconnect() → STATE_DISCONNECTED + broadcast HungUp
    ├── onMuteStateChanged()                → broadcast AudioMuting
    ├── onCallEndpointChanged()  (API 34+)  → broadcast AudioDeviceSet
    └── onAvailableCallEndpointsChanged()   → broadcast AudioDevicesUpdate

STATE_HOLDING
    └── onUnhold()     → STATE_ACTIVE + broadcast ConnectionHolding
```

All broadcasts are sent via `ConnectionServicePerformBroadcaster` to the main process.
See [ipc.md](ipc.md) for the full event table.

---

## `ConnectionManager`

The single source of truth for active `PhoneConnection` instances in the `:callkeep_core` process.
Stored as a `companion object` on `PhoneConnectionService`:

```kotlin
companion object {
    var connectionManager: ConnectionManager = ConnectionManager()
}
```

Key operations:

| Method                                                     | Purpose                                           |
|------------------------------------------------------------|---------------------------------------------------|
| `addConnection(callId, connection)`                        | Registers a new connection.                       |
| `getConnection(callId)`                                    | Looks up by call ID.                              |
| `getActiveConnection()`                                    | Returns the non-held active connection.           |
| `isConnectionAlreadyExists(callId)`                        | Duplicate guard before creating a new connection. |
| `isExistsIncomingConnection()`                             | Prevents two simultaneous incoming calls.         |
| `validateConnectionAddition(metadata, onSuccess, onError)` | Pre-flight check before calling Telecom.          |

---

## `PhoneConnectionServiceDispatcher`

Receives `ServiceAction` intents sent from the main process via `startService` and translates them
into `PhoneConnection` method calls. Also manages `ProximitySensorManager` and
`ActivityWakelockManager` lifecycle.

Actions dispatched (selected):

| `ServiceAction`         | Effect                                         |
|-------------------------|------------------------------------------------|
| `AnswerCall`            | `connection.answer()`                          |
| `DeclineCall`           | `connection.hungUp()`                          |
| `HoldCall`              | `connection.hold()`                            |
| `UnholdCall`            | `connection.unhold()`                          |
| `MuteCall`              | `connection.mute(muted)`                       |
| `SendDTMF`              | `connection.sendDtmf(key)`                     |
| `AudioDeviceSet`        | `connection.setAudioDevice(device)`            |
| `ForceUpdateAudioState` | Re-broadcasts current audio device/list state. |

---

## Audio management

- **API 34+:** Audio routing uses Telecom endpoints (`requestCallEndpointChange`). Device changes
  are reported via `onCallEndpointChanged` / `onAvailableCallEndpointsChanged` callbacks.
- **API < 34:** `AudioManager.setSpeakerphoneOn()` is used directly.
- `preventAutoSpeakerEnforcement` flag prevents the service from forcing speaker on a video call
  upgrade if the call originally started as audio-only.
