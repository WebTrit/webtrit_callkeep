# Inter-Process Communication

The plugin runs across two OS processes. This file documents all communication channels between them
and within the main process.

For the process model and why two processes are used,
see [architecture.md](architecture.md#two-process-architecture).

---

## Cross-process channel: `ConnectionServicePerformBroadcaster`

The **only** IPC channel between the `:callkeep_core` process and the main process. Uses Android
`sendBroadcast` with app-internal action strings prefixed by `ContextHolder.appUniqueKey`.

**Direction:** `:callkeep_core` → main process (one-way only)

**Registration:**

```kotlin
// Main process (ForegroundService.onCreate)
ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
    ConnectionPerform.entries, baseContext, connectionServicePerformReceiver
)

// :callkeep_core process (dispatch example)
ConnectionServicePerformBroadcaster.handle.dispatch(
    context,
    ConnectionPerform.AnswerCall,
    metadata.toBundle()
)
```

**Payload:** `Bundle` created from `CallMetadata` or `FailureMetadata`.

---

## `ConnectionPerform` event table

| Event                 | Sent by                                                   | Meaning                                              |
|-----------------------|-----------------------------------------------------------|------------------------------------------------------|
| `ConnectionAdded`     | `PhoneConnectionService`                                  | A new `PhoneConnection` was created.                 |
| `ConnectionRemoved`   | `PhoneConnection.onDisconnect`                            | A connection was torn down.                          |
| `DidPushIncomingCall` | `PhoneConnection` (on show UI)                            | System showed incoming call UI.                      |
| `AnswerCall`          | `PhoneConnection.onAnswer`                                | User answered the call.                              |
| `DeclineCall`         | `PhoneConnection.onDisconnect` (declined)                 | User declined the call.                              |
| `HungUp`              | `PhoneConnection.onDisconnect` (hung up)                  | Call ended by user or remote party.                  |
| `OngoingCall`         | `PhoneConnection.establish`                               | Outgoing call became active.                         |
| `AudioMuting`         | `PhoneConnection.onMuteStateChanged`                      | Mute state changed.                                  |
| `ConnectionHolding`   | `PhoneConnection.onHold` / `onUnhold`                     | Hold state changed.                                  |
| `SentDTMF`            | `PhoneConnection.onPlayDtmfTone`                          | DTMF tone sent.                                      |
| `AudioDeviceSet`      | `PhoneConnection.onCallEndpointChanged`                   | Active audio device changed (API 34+).               |
| `AudioDevicesUpdate`  | `PhoneConnection.onAvailableCallEndpointsChanged`         | Available devices list changed (API 34+).            |
| `OutgoingFailure`     | `PhoneConnectionService.onCreateOutgoingConnectionFailed` | Outgoing call creation failed.                       |
| `IncomingFailure`     | `PhoneConnectionService.onCreateIncomingConnectionFailed` | Incoming call creation failed.                       |
| `ConnectionNotFound`  | `PhoneConnectionServiceDispatcher`                        | Requested connection not found; coerces to `HungUp`. |

---

## IPC event → Flutter callback mapping

`ForegroundService` receives `ConnectionPerform` broadcasts and translates them into
`PDelegateFlutterApi` calls back to Dart:

```
IPC event              →  PDelegateFlutterApi call
────────────────────────────────────────────────────────
AnswerCall             →  performAnswerCall(callId) + didActivateAudioSession()
DeclineCall / HungUp   →  performEndCall(callId) + didDeactivateAudioSession()
OngoingCall            →  performStartCall(callId, handle, name, video)
DidPushIncomingCall    →  didPushIncomingCall(handle, displayName, video, callId, error=null)
AudioDeviceSet         →  performAudioDeviceSet(callId, device)
AudioDevicesUpdate     →  performAudioDevicesUpdate(callId, devices)
AudioMuting            →  performSetMuted(callId, muted)
ConnectionHolding      →  performSetHeld(callId, onHold)
SentDTMF               →  performSendDTMF(callId, key)
OutgoingFailure        →  resolves outgoing callback with PCallRequestError
ConnectionAdded        →  ForegroundService.connectionTracker.add(callId, metadata)
ConnectionRemoved      →  ForegroundService.connectionTracker.remove(callId)
```

---

## In-process broadcast channels (main process only)

These channels operate entirely within the main app process.

### `ActivityLifecycleBroadcaster`

| Property  | Value                                                                                                         |
|-----------|---------------------------------------------------------------------------------------------------------------|
| Direction | `WebtritCallkeepPlugin` → `SignalingIsolateService`                                                           |
| Trigger   | `Lifecycle.Event` (ON_START, ON_RESUME, ON_PAUSE, ON_STOP, …)                                                 |
| Purpose   | `SignalingIsolateService` synchronizes the Flutter isolate with activity state (e.g., wake-up on foreground). |

### `SignalingStatusBroadcaster`

| Property  | Value                                                                                        |
|-----------|----------------------------------------------------------------------------------------------|
| Direction | Flutter isolate (via `ConnectionsApi`) → `SignalingIsolateService`                           |
| Trigger   | `SignalingStatus` (CONNECTING, CONNECT, DISCONNECTING, DISCONNECT, FAILURE)                  |
| Purpose   | `SignalingIsolateService` passes the latest signaling status to the isolate wake-up handler. |

---

## Connection tracking: two-process mirror

Because the two processes cannot share memory, call state is tracked independently in each:

### `ConnectionManager` (`:callkeep_core`)

`PhoneConnectionService.connectionManager` (a `companion object` field) is the single source of
truth for active `PhoneConnection` instances in the `:callkeep_core` process.

```kotlin
companion object {
    var connectionManager: ConnectionManager = ConnectionManager()
}
```

Key operations:

- `addConnection(callId, connection)` — registers a new connection
- `getConnection(callId)` — looks up by ID
- `getActiveConnection()` — returns the non-held active connection
- `isConnectionAlreadyExists(callId)` — duplicate guard
- `isExistsIncomingConnection()` — prevents two simultaneous incoming calls
- `validateConnectionAddition(metadata, onSuccess, onError)` — pre-flight check before calling
  Telecom

### `MainProcessConnectionTracker` (main process)

`ForegroundService.connectionTracker` mirrors the set of active calls in the main process. It is
updated by `ConnectionAdded` / `ConnectionRemoved` IPC events. This allows:

- `ConnectionsApi.getConnections()` to return live call state without crossing the process boundary.
- `WebtritCallkeepPlugin.onStateChanged` to check whether there is an active call for lock-screen
  flag management.

```kotlin
// Updated from broadcast receiver in ForegroundService
ConnectionPerform.ConnectionAdded.name -> connectionTracker.add(m.callId, m)
ConnectionPerform.ConnectionRemoved.name -> connectionTracker.remove(m.callId)
```
