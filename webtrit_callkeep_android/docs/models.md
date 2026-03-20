# Data Models

**Package**: `com.webtrit.callkeep.models`

---

## CallMetadata

**File**: `kotlin/com/webtrit/callkeep/models/CallMetadata.kt`

The central data class describing a single call. Passed across process boundaries as a `Bundle`
(not as a `Parcelable` — see constraint below).

### Fields

| Field              | Type                | Description                                      |
|--------------------|---------------------|--------------------------------------------------|
| `callId`           | `String`            | Unique call identifier                           |
| `displayName`      | `String?`           | Human-readable caller name                       |
| `handle`           | `CallHandle`        | Phone number or SIP URI                          |
| `hasVideo`         | `Boolean`           | Whether video is enabled                         |
| `audioDevice`      | `AudioDevice?`      | Currently selected audio device                  |
| `audioDevices`     | `List<AudioDevice>` | Available audio devices                          |
| `proximityEnabled` | `Boolean`           | Whether proximity sensor should be active        |
| `hasMute`          | `Boolean`           | Whether mute capability is available             |
| `hasHold`          | `Boolean`           | Whether hold capability is available             |
| `speakerOnVideo`   | `Boolean`           | Auto-route audio to speaker when video is active |
| `ringtonePath`     | `String?`           | Custom ringtone file path (asset cache)          |

### Bundle Serialization

```kotlin
fun toBundle(): Bundle
companion fun fromBundle(bundle: Bundle): CallMetadata
fun mergeWith(other: CallMetadata): CallMetadata
```

`toBundle()` / `fromBundle()` are used instead of `Parcelable`. Android's `system_server`
process attempts to unparcel objects from Telecom `ConnectionRequest` extras, and using
`Parcelable` would cause a `ClassNotFoundException` because the app's classes are not accessible
from `system_server`.

---

## CallHandle

**File**: `kotlin/com/webtrit/callkeep/models/CallHandle.kt`

Wraps a phone number or SIP URI string passed to Android Telecom.

### Fields

| Field   | Type             | Description                                                 |
|---------|------------------|-------------------------------------------------------------|
| `value` | `String`         | Raw handle string (e.g., `+15551234567` or `sip:user@host`) |
| `type`  | `CallHandleType` | `PHONE_NUMBER` or `SIP`                                     |

---

## AudioDevice

**File**: `kotlin/com/webtrit/callkeep/models/AudioDevice.kt`

Represents an audio output endpoint.

### Fields

| Field  | Type              | Description                                                           |
|--------|-------------------|-----------------------------------------------------------------------|
| `name` | `String`          | Display name                                                          |
| `type` | `AudioDeviceType` | `EARPIECE`, `SPEAKER`, `BLUETOOTH`, `WIRED_HEADSET`, `UNKNOWN`        |
| `id`   | `String?`         | Device identifier (API 34+, corresponds to `CallEndpoint.endpointId`) |

---

## SignalingStatus

**File**: `kotlin/com/webtrit/callkeep/models/SignalingStatus.kt`

Tracks the signaling layer connection state.

| Value          | Meaning                                    |
|----------------|--------------------------------------------|
| `CONNECTED`    | Signaling layer is connected to the server |
| `DISCONNECTED` | Signaling layer is not connected           |

---

## NotificationAction

**File**: `kotlin/com/webtrit/callkeep/models/NotificationAction.kt`

Enum of actions a user can perform from the call notification (answer, decline, hang up, etc.).
Used to route `PendingIntent` callbacks from notification buttons.

---

## FailedCallInfo / FailureMetaData

**Files**: `models/FailedCallInfo.kt`, `models/FailureMetaData.kt`

Carry structured error information when a call fails to connect.

| Field    | Description                                                           |
|----------|-----------------------------------------------------------------------|
| `callId` | Call that failed                                                      |
| `reason` | `FailureReason` enum (e.g., `REMOTE_REJECT`, `ERROR`, `UNREGISTERED`) |
| `code`   | Optional numeric error code                                           |

---

## EmergencyNumberException

**File**: `kotlin/com/webtrit/callkeep/models/EmergencyNumberException.kt`

Thrown (and caught, then reported to Dart) when the app attempts to handle an emergency number
(`112`, `911`, etc.) that must be routed to the system dialer instead.

---

## Converters

**File**: `kotlin/com/webtrit/callkeep/models/Converters.kt`

Extension functions and utilities for converting between Pigeon-generated types (`PCallMetadata`,
`PAudioDevice`, etc.) and internal model types (`CallMetadata`, `AudioDevice`, etc.).

These are the only place where Pigeon types are translated to internal types. All business logic
works with internal types.

---

## Related Components

- [phone-connection.md](phone-connection.md) — holds `CallMetadata`
- [connection-tracker.md](connection-tracker.md) — stores `CallMetadata` by callId
- [pigeon-apis.md](pigeon-apis.md) — Pigeon types that are converted via `Converters`
