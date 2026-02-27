# Processes and Inter-Process Communication

## Process Model

The plugin uses two OS processes:

```
┌─────────────────────────────────────────────┐
│              Main process (app)              │
│                                              │
│  WebtritCallkeepPlugin                       │
│  ForegroundService                           │
│  SignalingIsolateService  (+ Flutter engine) │
│  IncomingCallService      (+ Flutter engine) │
│  ActiveCallService                           │
└──────────────────────┬──────────────────────┘
                       │  local broadcast
                       │  (cross-process sendBroadcast)
┌──────────────────────▼──────────────────────┐
│           :callkeep_core process             │
│                                              │
│  PhoneConnectionService                      │
│    └── PhoneConnection (per active call)     │
│    └── ConnectionManager                     │
│    └── PhoneConnectionServiceDispatcher      │
└─────────────────────────────────────────────┘
```

### Why a separate process?

`PhoneConnectionService` is declared with `android:process=":callkeep_core"` in the manifest. The Android Telecom framework binds to `ConnectionService` directly — if the main app process is killed or in the background, the system still needs a live process to manage the call. A separate process ensures the Telecom state machine remains running independently of the app UI.

Each process initializes its own `ContextHolder` singleton:
- Main process: initialized in `WebtritCallkeepPlugin.onAttachedToEngine`
- `:callkeep_core` process: initialized in `PhoneConnectionService.onCreate`

---

## Inter-Process Communication

### Channel: `ConnectionServicePerformBroadcaster`

The only IPC channel between the two processes. Uses Android `sendBroadcast` with app-internal actions (prefixed with the app's unique key from `ContextHolder.appUniqueKey`).

**Direction:** `:callkeep_core` → main process

**Events (`ConnectionPerform` enum):**

| Event | Sent by | Meaning |
|---|---|---|
| `ConnectionAdded` | `PhoneConnectionService` | A new `PhoneConnection` was created. |
| `ConnectionRemoved` | `PhoneConnection.onDisconnect` | A connection was torn down. |
| `DidPushIncomingCall` | `PhoneConnection` (on show UI) | System showed incoming call UI. |
| `AnswerCall` | `PhoneConnection.onAnswer` | User answered the call. |
| `DeclineCall` | `PhoneConnection.onDisconnect` (declined) | User declined. |
| `HungUp` | `PhoneConnection.onDisconnect` (hung up) | Call ended by user or remote. |
| `OngoingCall` | `PhoneConnection.establish` | Outgoing call became active. |
| `AudioMuting` | `PhoneConnection.onMuteStateChanged` | Mute state changed. |
| `ConnectionHolding` | `PhoneConnection.onHold/onUnhold` | Hold state changed. |
| `SentDTMF` | `PhoneConnection.onPlayDtmfTone` | DTMF tone sent. |
| `AudioDeviceSet` | `PhoneConnection.onCallEndpointChanged` | Active audio device changed. |
| `AudioDevicesUpdate` | `PhoneConnection.onAvailableCallEndpointsChanged` | Available devices list changed. |
| `OutgoingFailure` | `PhoneConnectionService.onCreateOutgoingConnectionFailed` | Outgoing call creation failed. |
| `IncomingFailure` | `PhoneConnectionService.onCreateIncomingConnectionFailed` | Incoming call creation failed. |
| `ConnectionNotFound` | `PhoneConnectionServiceDispatcher` | Requested connection not found; coerces to `HungUp`. |

**Payload:** `Bundle` created from `CallMetadata` or `FailureMetadata`.

**Registration:**
```kotlin
// Main process (ForegroundService.onCreate)
ConnectionServicePerformBroadcaster.registerConnectionPerformReceiver(
    ConnectionPerform.entries, baseContext, connectionServicePerformReceiver
)

// :callkeep_core process (via dispatcher)
ConnectionServicePerformBroadcaster.handle.dispatch(context, ConnectionPerform.AnswerCall, metadata.toBundle())
```

### How ForegroundService maps IPC events to Flutter callbacks

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

## In-Process Broadcast Channels

These operate within the main process only.

### `ActivityLifecycleBroadcaster`

| Property | Value |
|---|---|
| Direction | `WebtritCallkeepPlugin` → `SignalingIsolateService` |
| Trigger | `Lifecycle.Event` (ON_START, ON_RESUME, ON_PAUSE, ON_STOP, …) |
| Purpose | `SignalingIsolateService` synchronizes isolate with activity state (e.g., wake-up on foreground) |

### `SignalingStatusBroadcaster`

| Property | Value |
|---|---|
| Direction | Flutter isolate (via `ConnectionsApi`) → `SignalingIsolateService` |
| Trigger | `SignalingStatus` (CONNECTING, CONNECT, DISCONNECTING, DISCONNECT, FAILURE) |
| Purpose | `SignalingIsolateService` passes latest signaling status to isolate wake-up handler |

---

## `ConnectionManager` — In-process state in `:callkeep_core`

`PhoneConnectionService.connectionManager` (a `companion object` field) is the single source of truth for active `PhoneConnection` instances inside the `:callkeep_core` process.

```kotlin
companion object {
    var connectionManager: ConnectionManager = ConnectionManager()
}
```

Key operations:
- `addConnection(callId, connection)` — registers a new connection
- `getConnection(callId)` — looks up by ID
- `getActiveConnection()` — returns non-held active connection
- `isConnectionAlreadyExists(callId)` — duplicate guard
- `isExistsIncomingConnection()` — prevents two simultaneous incoming calls
- `validateConnectionAddition(metadata, onSuccess, onError)` — pre-flight check before calling Telecom

## `MainProcessConnectionTracker` — Mirror in main process

`ForegroundService.connectionTracker` mirrors the set of active calls in the main process. It is updated by `ConnectionAdded` / `ConnectionRemoved` IPC events. This allows `ConnectionsApi.getConnections()` and the lock-screen flag logic in `WebtritCallkeepPlugin.onStateChanged` to read call state without crossing the process boundary.

```kotlin
// Updated from broadcast receiver in ForegroundService
ConnectionPerform.ConnectionAdded.name -> connectionTracker.add(m.callId, m)
ConnectionPerform.ConnectionRemoved.name -> connectionTracker.remove(m.callId)
```
