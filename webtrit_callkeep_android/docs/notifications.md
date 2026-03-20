# Notification System

## Overview

Notifications are managed by three builder classes and one manager facade. Each call phase has a
dedicated notification builder and Android notification channel.

| Phase                     | Builder                             | Channel           |
|---------------------------|-------------------------------------|-------------------|
| Incoming call (ringing)   | `IncomingCallNotificationBuilder`   | `incoming_call`   |
| Active call (in progress) | `ActiveCallNotificationBuilder`     | `active_call`     |
| Foreground / signaling    | `ForegroundCallNotificationBuilder` | `foreground_call` |

---

## NotificationChannelManager

**File**: `kotlin/com/webtrit/callkeep/managers/NotificationChannelManager.kt`

Creates the three notification channels on first run (Android 8+). Called from
`ForegroundService.setUp()`.

| Channel ID        | Importance | Description                                            |
|-------------------|------------|--------------------------------------------------------|
| `incoming_call`   | `HIGH`     | Heads-up notification with ringtone for incoming calls |
| `active_call`     | `LOW`      | Persistent silent notification for active call         |
| `foreground_call` | `MIN`      | Background foreground-service notification             |

---

## NotificationManager (Manager Facade)

**File**: `kotlin/com/webtrit/callkeep/managers/NotificationManager.kt`

Central facade used by `ForegroundService` to trigger notification service actions.

| Method                                       | Action                                         |
|----------------------------------------------|------------------------------------------------|
| `showIncomingCallNotification(callId, meta)` | Starts `IncomingCallService` with call data    |
| `cancelIncomingNotification(callId)`         | Stops `IncomingCallService` for the given call |
| `showActiveCallNotification(calls)`          | Starts or updates `ActiveCallService`          |
| `cancelActiveCallNotification()`             | Stops `ActiveCallService`                      |
| `tearDown()`                                 | Stops all notification services                |

---

## IncomingCallNotificationBuilder

**File**: `kotlin/com/webtrit/callkeep/notifications/IncomingCallNotificationBuilder.kt`

Builds the incoming call notification shown while the phone is ringing.

### Notification Properties

- **Style**: `CallStyle.forIncomingCall(person, declineIntent, answerIntent)` (API 31+) or a
  custom full-screen intent notification on older versions.
- **Full-screen intent**: Set to an activity / overlay that surfaces over the lock screen.
- **Actions**: Answer button (`NotificationAction.ANSWER`) and Decline button
  (`NotificationAction.DECLINE`).
- **Person**: Populated from `CallMetadata.displayName` and `CallMetadata.handle`.

### PendingIntents

Each action creates a `PendingIntent` pointing back to `IncomingCallService` with the
corresponding `NotificationAction` extra. The service routes these to `CallLifecycleHandler`.

---

## ActiveCallNotificationBuilder

**File**: `kotlin/com/webtrit/callkeep/notifications/ActiveCallNotificationBuilder.kt`

Builds the persistent notification shown during an active call.

### Notification Properties

- **Style**: `CallStyle.forOngoingCall(person, endCallIntent)` (API 31+).
- **Actions**: End call button; optionally mute and hold buttons depending on `CallMetadata` flags
  (`hasMute`, `hasHold`).
- Supports multiple simultaneous calls — one notification entry per call, or a summary.

---

## ForegroundCallNotificationBuilder

**File**: `kotlin/com/webtrit/callkeep/notifications/ForegroundCallNotificationBuilder.kt`

Builds the minimal persistent notification required by Android for foreground services
(`SignalingIsolateService`, `IncomingCallService`, `ActiveCallService`).

- Low-visibility (no sound, no vibration, `MIN` importance).
- Used as the placeholder notification in `IncomingCallService.onCreate()` to satisfy the
  5-second foreground-service start requirement.

---

## AudioManager

**File**: `kotlin/com/webtrit/callkeep/managers/AudioManager.kt`

Handles ringtone and ringback playback.

| Method                        | Description                                                           |
|-------------------------------|-----------------------------------------------------------------------|
| `startRingtone(callId, path)` | Play incoming call ringtone (from asset cache path or system default) |
| `stopRingtone(callId)`        | Stop ringtone                                                         |
| `startRingback(callId)`       | Play outgoing ringback tone                                           |
| `stopRingback(callId)`        | Stop ringback                                                         |
| `setAudioDevice(device)`      | Route audio output                                                    |
| `getAvailableAudioDevices()`  | Query available endpoints                                             |

Ringtone playback uses `RingtoneManager` or a custom `MediaPlayer` depending on the path. Audio
focus is requested before playback and released when done.

---

## Related Components

- [background-services.md](background-services.md) — services that host notifications
- [phone-connection.md](phone-connection.md) — triggers `showIncomingCallNotification` from
  `onShowIncomingCallUi()`
- [foreground-service.md](foreground-service.md) — uses `NotificationManager` to update
  notifications
