# Notifications

## Notification Channels

Registered once during `ForegroundService.setUp()` via
`NotificationChannelManager.registerNotificationChannels()`.

| Channel ID constant                       | Purpose                                   | Importance | Sound           | Lockscreen |
|-------------------------------------------|-------------------------------------------|------------|-----------------|------------|
| `INCOMING_CALL_NOTIFICATION_CHANNEL_ID`   | Ringing incoming call                     | HIGH       | Custom ringtone | PUBLIC     |
| `FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID` | Signaling service persistent notification | LOW        | None            | PRIVATE    |
| `NOTIFICATION_ACTIVE_CALL_CHANNEL_ID`     | Active call notification                  | DEFAULT    | None            | PRIVATE    |

---

## Notification Builders

### `IncomingCallNotificationBuilder`

**Notification ID:** `2`
**Used by:** `IncomingCallService`

Builds the incoming call notification shown while the device is ringing.

| Method                       | Result                                                                                                                                                         |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `build(metadata)`            | High-priority ringing notification with Answer / Decline actions. On Android S+ uses `Notification.CallStyle.forIncomingCall()`. FLAG_INSISTENT (repeat ring). |
| `buildSilent(metadata)`      | Same notification but with muted channel — used after the user answers to silence the ring while the call connects.                                            |
| `buildReleaseNotification()` | Brief "releasing" message shown for ~2 seconds when the service is stopping after a decline.                                                                   |

**Full-screen intent:** Points to the app's launch activity so the call UI opens over the lock
screen.

**Notification actions:**

- `Answer` → `IncomingCallService` intent with action `NotificationAction.Answer`
- `Decline` → `IncomingCallService` intent with action `NotificationAction.Decline`

---

### `ActiveCallNotificationBuilder`

**Notification ID:** `1`
**Used by:** `ActiveCallService`

Persistent notification shown during an ongoing call.

| Method            | Result                                                                                  |
|-------------------|-----------------------------------------------------------------------------------------|
| `build(metadata)` | Ongoing call notification with caller name, "Active call" status, and a Hang Up action. |

**Notification actions:**

- `Hang Up` → `ActiveCallService` intent with action `NotificationAction.Decline`

---

### `ForegroundCallNotificationBuilder`

**Notification ID:** `3`
**Used by:** `SignalingIsolateService`

Persistent notification keeping `SignalingIsolateService` alive in the foreground.

| Method             | Result                                                                                                       |
|--------------------|--------------------------------------------------------------------------------------------------------------|
| `build()`          | Low-priority notification with configurable title and description (from `StorageDelegate.SignalingService`). |
| `setTitle(text)`   | Overrides default title.                                                                                     |
| `setContent(text)` | Overrides default description.                                                                               |

**Delete action (user swipes away):** The notification sets a delete intent with action
`ACTION_RESTORE_NOTIFICATION` back to `SignalingIsolateService`. When the user dismisses the
notification, `onStartCommand` detects this and calls `ensureNotification()` to re-post it, keeping
the service alive.

---

## Notification Flow per Call State

```
Incoming call arrives:
    IncomingCallService
        └── IncomingCallNotificationBuilder.build()  [ID=2, HIGH, FLAG_INSISTENT]
                (ringing, Answer + Decline buttons)

User answers:
    IncomingCallService
        └── IncomingCallNotificationBuilder.buildSilent()  [ID=2, muted]
                (call connecting, no more ring)
    ActiveCallService started:
        └── ActiveCallNotificationBuilder.build()  [ID=1]
                (ongoing call, Hang Up button)

Call ends:
    IncomingCallService (if still running):
        └── IncomingCallNotificationBuilder.buildReleaseNotification()  [ID=2]
                (brief "ending call" message, auto-dismissed after 2s)
    ActiveCallService notification cancelled.

Signaling service running (always):
    SignalingIsolateService
        └── ForegroundCallNotificationBuilder.build()  [ID=3, LOW]
                (configurable title/description, persistent)
```

---

## Notification Actions

`NotificationAction` enum with `action` string used as intent action:

| Enum                         | Intent Action                                | Handler                                                                   |
|------------------------------|----------------------------------------------|---------------------------------------------------------------------------|
| `NotificationAction.Answer`  | `<appUniqueKey>Answer_incoming_call_action`  | `IncomingCallService.onStartCommand`                                      |
| `NotificationAction.Decline` | `<appUniqueKey>Decline_incoming_call_action` | `IncomingCallService.onStartCommand` / `ActiveCallService.onStartCommand` |

The action strings are prefixed with `ContextHolder.appUniqueKey` (the app's package name) to avoid
conflicts with other apps on the device.

---

## Permissions Required

| Permission               | Purpose                                                                                                                                           |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST_NOTIFICATIONS`     | Required on Android 13+ to show any notification. Checked in `PermissionsHelper.hasNotificationPermission()` before starting foreground service.  |
| `USE_FULL_SCREEN_INTENT` | Required on Android 14+ to show full-screen incoming call intent. Status exposed via `PHostPermissionsApi.getFullScreenIntentPermissionStatus()`. |
| `VIBRATE`                | Ring pattern vibration during incoming call.                                                                                                      |
| `WAKE_LOCK`              | `SignalingIsolateService` acquires a partial wake lock to keep the CPU alive during call setup.                                                   |
