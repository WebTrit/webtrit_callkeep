# PhoneConnection

**File**: `kotlin/com/webtrit/callkeep/services/services/connection/PhoneConnection.kt`

**Extends**: Android `Connection`

**Process**: `:callkeep_core`

## Responsibility

`PhoneConnection` represents a **single active call** inside Android Telecom. One instance exists
per call and is owned by `ConnectionManager`. It handles all Telecom-driven callbacks for that
call and dispatches corresponding events to the main process.

## State

| Field            | Type           | Description                                       |
|------------------|----------------|---------------------------------------------------|
| `callId`         | `String`       | Unique call identifier (matches across processes) |
| `metadata`       | `CallMetadata` | Call details (display name, handle, flags)        |
| `hasAnswered`    | `Boolean`      | Whether the user has accepted the call            |
| `isMute`         | `Boolean`      | Current mute state                                |
| `hasVideo`       | `Boolean`      | Whether video is enabled                          |
| Internal `state` | `Int`          | Telecom connection state constant                 |

## Telecom Callback Methods

### `onShowIncomingCallUi()`

Telecom asks the app to display its incoming call UI.

- Acquires wake lock.
- Starts ringtone.
- Shows incoming call notification (via `NotificationManager`).

### `onAnswer(videoState)`

User or app code answers the call.

- Sets state to `STATE_ACTIVE`.
- Stops ringtone, cancels incoming-call notification.
- Dispatches `AnswerCall` broadcast to main process.

### `onReject()` / `onReject(rejectWithMessage, textMessage)`

User or app code declines the call.

- Sets state to `STATE_DISCONNECTED` with cause `CAUSE_REMOTE_USER_HANGUP` (rejected).
- Dispatches `DeclineCall` broadcast to main process.
- Calls `destroy()`.

### `onDisconnect()`

Called by Telecom when the call ends (hang-up from either side).

- Stops ringtone, audio, cancels notifications.
- Sets state to `STATE_DISCONNECTED`.
- Dispatches `HungUp` broadcast to main process.
- Calls `destroy()`.

### `onHold()` / `onUnhold()`

- Updates state to `STATE_HOLDING` / `STATE_ACTIVE`.
- Dispatches `ConnectionHolding` broadcast with new hold state.

### `onPlayDtmfTone(c)` / `onStopDtmfTone()`

- Dispatches `SentDTMF` broadcast.

### `onCallEndpointChanged(endpoint)` (API 34+) / legacy audio device change

- Dispatches `AudioDeviceSet` broadcast with the new endpoint.
- Dispatches `AudioDevicesUpdate` broadcast with full device list.

## Media Methods

### `changeMuteState(muted)`

- Updates `isMute`.
- Sets Telecom audio mute.
- Dispatches `AudioMuting` broadcast.

### `setSpeaker(on)` / `setAudioDevice(device)`

- Routes audio via the Telecom `CallAudioState` or `CallEndpoint` APIs.
- Dispatches `AudioDeviceSet` broadcast.

## Factory Methods

```kotlin
companion object {
    fun createIncomingPhoneConnection(context, callId, metadata): PhoneConnection
    fun createOutgoingPhoneConnection(context, callId, metadata): PhoneConnection
}
```

Incoming connections start in `STATE_RINGING`; outgoing connections start in `STATE_DIALING`.

## Lifecycle Summary

```
Incoming: INITIALIZING -> RINGING -> (onAnswer) ACTIVE -> (onDisconnect) DISCONNECTED
                                  -> (onReject)  DISCONNECTED
Outgoing: DIALING -> (setActive) ACTIVE -> (onDisconnect) DISCONNECTED
```

## Related Components

- [phone-connection-service.md](phone-connection-service.md) — creates and owns this object
- [connection-manager.md](connection-manager.md) — stores this object
- [ipc-broadcasting.md](ipc-broadcasting.md) — events dispatched from callbacks here
