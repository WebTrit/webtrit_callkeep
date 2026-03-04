# ActiveCallService

**Package:** `com.webtrit.callkeep.services.services.active_call`
**Process:** main
**Type:** Foreground service, `foregroundServiceType="phoneCall|microphone|camera"`

---

## Role

Shows and manages the persistent "active call" notification while a call is in progress. Declaring
the `microphone` and `camera` foreground service types allows the OS to permit audio/video capture
for the duration of the call.

---

## Foreground service types

| Type         | Reason                                                     |
|--------------|------------------------------------------------------------|
| `phoneCall`  | Required to keep a VoIP call active in the background.     |
| `microphone` | Required for continuous microphone access during the call. |
| `camera`     | Required for continuous camera access for video calls.     |

---

## Notification

`ActiveCallNotificationBuilder` builds a persistent notification that:

- Shows the call duration (if provided by the app).
- Shows the caller display name and handle.
- Provides a **Hang up** action button.
- Keeps the app process alive and visible to the user while a call is active.

---

## Start/stop lifecycle

The service is started and stopped in response to `PhoneConnection` state transitions tracked by
`ForegroundService`:

- **Started** when a call transitions to `STATE_ACTIVE` (after answer or after outgoing call is
  established).
- **Stopped** when the last active call disconnects (`STATE_DISCONNECTED`).

This ensures the service is only running while a live call is in progress and not during ringing or
dialing.
