# PhoneConnectionService

**File**: `kotlin/com/webtrit/callkeep/services/services/connection/PhoneConnectionService.kt`

**Extends**: Android `ConnectionService`

**Process**: `:callkeep_core` (declared in `AndroidManifest.xml`)

**Annotation**: `@Keep`

## Responsibility

`PhoneConnectionService` is the Android Telecom `ConnectionService` implementation. It runs in the
`:callkeep_core` OS process and is the only component that interacts with Android Telecom directly.

Its responsibilities:

- Creates and destroys `PhoneConnection` objects on behalf of the Telecom framework.
- Handles per-call lifecycle actions (answer, decline, hang up, establish, update, mute, hold,
  etc.).
- Dispatches call lifecycle and media events to the main process via local broadcasts.
- Receives commands from the main process via explicit `startService` intents.

## AndroidManifest Declaration

```xml

<service android:name=".services.connection.PhoneConnectionService" android:process=":callkeep_core"
    android:exported="false"
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.ConnectionService" />
    </intent-filter>
</service>
```

## Lifecycle Callbacks (Telecom-driven)

### `onCreate()`

- Initializes `ConnectionManager`, `PhoneConnectionServiceDispatcher`, `TelephonyUtils`.
- Registers a broadcast receiver for `NotifyPending` intents (main process pre-registers calls
  before Telecom delivers `onCreateIncomingConnection`).
- Initializes `ActivityWakelockManager` and `ProximitySensorManager`.

### `onCreateIncomingConnection(phoneAccountHandle, request)`

- Creates a `PhoneConnection` for the incoming call.
- Looks up metadata from `ConnectionManager.getPendingMetadata(callId)`.
- If metadata is not yet available (race condition), falls back to extracting from the `request`
  Bundle.
- Calls `performEventHandle(DidPushIncomingCall, ...)` to notify the main process.
- If `ConnectionManager.consumeAnswer(callId)` returns true (deferred answer), calls
  `connection.onAnswer()` immediately.

### `onCreateOutgoingConnection(phoneAccountHandle, request)`

- Creates a `PhoneConnection` for the outgoing call (starts in STATE_DIALING).
- Calls `performEventHandle(OngoingCall, ...)`.

### `onCreateOutgoingConnectionFailed(phoneAccountHandle, request)`

- Calls `performEventHandle(OutgoingFailure, ...)` with the failure reason.

### `onDestroy()`

- Releases wake lock, unregisters broadcast receiver.

## Command Handling (`onStartCommand`)

Explicit `startService` intents arrive here. The intent action is a `ServiceAction` enum value
encoded as a string extra.

| `ServiceAction`       | Description                                                                        |
|-----------------------|------------------------------------------------------------------------------------|
| `TearDownConnections` | Call `hungUp()` on every `PhoneConnection`, then broadcast `TearDownComplete`      |
| `ReserveAnswer`       | Store deferred answer for `callId` (call `ConnectionManager.reserveAnswer()`)      |
| `CleanConnections`    | Clear all connections without hanging up                                           |
| `SyncAudioState`      | Re-emit audio state for all active connections (hot-restart recovery)              |
| `SyncConnectionState` | Re-fire `AnswerCall` broadcast for all answered connections (hot-restart recovery) |
| `AnswerCall`          | Call `PhoneConnection.onAnswer()` for the specified call                           |
| `DeclineCall`         | Call `PhoneConnection.onReject()`                                                  |
| `HungUpCall`          | Call `PhoneConnection.onDisconnect()`                                              |
| `EstablishCall`       | Set connection to STATE_ACTIVE                                                     |
| `UpdateCall`          | Update `PhoneConnection` metadata                                                  |
| `MuteCall`            | `PhoneConnection.changeMuteState()`                                                |
| `HoldCall`            | `PhoneConnection.onHold()` / `onUnhold()`                                          |
| `SpeakerCall`         | Route audio to speaker                                                             |
| `SetAudioDevice`      | Select audio device                                                                |
| `SendDtmf`            | Send DTMF tone                                                                     |
| `NotifyPending`       | Register callId as pending before `onCreateIncomingConnection` arrives             |

## Event Dispatch

All events sent to the main process go through `performEventHandle()`, which calls
`ConnectionServicePerformBroadcaster.handle.dispatch(event, extras)`.

See [ipc-broadcasting.md](ipc-broadcasting.md) for the full event catalogue.

## Key Collaborators

| Class                              | Role                                                                            |
|------------------------------------|---------------------------------------------------------------------------------|
| `ConnectionManager`                | Stores `PhoneConnection` instances and pending/terminated state                 |
| `PhoneConnectionServiceDispatcher` | Routes lifecycle actions to the correct `PhoneConnection`                       |
| `ActivityWakelockManager`          | Acquires/releases wake lock for incoming calls                                  |
| `ProximitySensorManager`           | Manages proximity sensor for in-ear audio routing                               |
| `PhoneConnection`                  | Individual Telecom call object (see [phone-connection.md](phone-connection.md)) |

## Related Components

- [phone-connection.md](phone-connection.md) — individual call object created here
- [connection-manager.md](connection-manager.md) — call registry used here
- [ipc-broadcasting.md](ipc-broadcasting.md) — events dispatched from here
- [dual-process.md](dual-process.md) — explains why this runs in a separate process
