# Notification System

## Overview

Call notifications are built by four builder classes sharing one abstract base, and their
lifecycle is driven by the services that own them. Each call phase has a dedicated builder;
the dual-process (Telecom) path and the standalone path have separate builder families that
share the same notification channels.

| Phase / path                  | Builder                                     | Channel                                    |
|-------------------------------|---------------------------------------------|--------------------------------------------|
| Incoming call (Telecom path)  | `IncomingCallNotificationBuilder`           | `INCOMING_CALL_NOTIFICATION_CHANNEL_ID`    |
| Active call (Telecom path)    | `ActiveCallNotificationBuilder`             | `ACTIVE_CALL_SERVICE_NOTIFICATION_CHANNEL` |
| Incoming call (standalone)    | `StandaloneIncomingCallNotificationBuilder` | `INCOMING_CALL_NOTIFICATION_CHANNEL_ID`    |
| Active call (standalone)      | `StandaloneActiveCallNotificationBuilder`   | `ACTIVE_CALL_SERVICE_NOTIFICATION_CHANNEL` |

The abstract base `NotificationBuilder` (`kotlin/com/webtrit/callkeep/notifications/NotificationBuilder.kt`)
centralizes the audio/video branching of the incoming-call content (title, description, small
icon), so the ringing, silent and standalone variants stay in sync.

---

## NotificationChannelManager

**File**: `kotlin/com/webtrit/callkeep/managers/NotificationChannelManager.kt`

Registers the three notification channels (and deletes the legacy
`NOTIFICATION_ACTIVE_CALL_CHANNEL_ID` channel left behind by older versions). Called from
`ForegroundService.setUp()` on the Telecom path and from `StandaloneCallService` on the
standalone path.

| Channel ID                                 | Importance | Description                                            |
|--------------------------------------------|------------|--------------------------------------------------------|
| `INCOMING_CALL_NOTIFICATION_CHANNEL_ID`    | `HIGH`     | Heads-up notification with ringtone for incoming calls |
| `ACTIVE_CALL_SERVICE_NOTIFICATION_CHANNEL` | `LOW`      | Persistent silent notification for active calls        |
| `FOREGROUND_CALL_NOTIFICATION_CHANNEL_ID`  | `LOW`      | Standalone-service foreground notification             |

---

## NotificationManager (Manager Facade)

**File**: `kotlin/com/webtrit/callkeep/managers/NotificationManager.kt`

Facade over the two notification-hosting services of the Telecom path. It keeps the list of
active calls in a **static in-memory list** (`activeCalls` in its companion object) - state
that lives only in the main process and is lost when that process dies.

| Method                                   | Action                                                                   |
|------------------------------------------|--------------------------------------------------------------------------|
| `showIncomingCallNotification(metadata)` | Starts `IncomingCallService` with the call data                          |
| `cancelIncomingNotification(answered)`   | Releases `IncomingCallService` via an internal broadcast (answer/decline) |
| `showActiveCallNotification(id, meta)`   | Adds/moves the call to the head of `activeCalls`, then upserts the service |
| `cancelActiveCallNotification(id)`       | Removes the call from `activeCalls`, then upserts the service            |
| `tearDown()`                             | Stops both notification services                                         |

The private `upsertActiveCallsService()` implements the active-call lifecycle: while
`activeCalls` is non-empty it (re)starts `ActiveCallService` with the full list serialized
into the `metadata` intent extra; when the list becomes empty it stops the service. A live
caller therefore never starts the service without metadata.

---

## IncomingCallNotificationBuilder

**File**: `kotlin/com/webtrit/callkeep/notifications/IncomingCallNotificationBuilder.kt`

Builds the notification shown while the phone is ringing, plus its silent variant.

### Ringing variant (`build()`)

- **Style**: `Notification.CallStyle.forIncomingCall(person, declineIntent, answerIntent)`
  (API 31+) or a plain builder with explicit answer/decline action buttons on API 26-30.
- **Insistent**: `FLAG_INSISTENT` is set so the ringtone loops until handled.
- **Full-screen intent**: applied only when enabled by configuration and the full-screen
  intent permission is granted; surfaces the incoming-call activity over the lock screen.
- **Decline**: a service `PendingIntent` back to `IncomingCallService` carrying the call's
  bundle.
- **Answer**: an activity `PendingIntent` through `AnswerCallTrampolineActivity`, so the
  answer also works from the lock screen.
- **Notification ids**: derived per call, starting at 1000 - distinct from the active-call
  notification id.

### Silent variant (`buildSilent()`)

Same call content on the same channel, but with the answer/decline actions and the insistent
flag removed. Shown once the call has been answered, so the user is no longer offered buttons
for a call that is already taken while the app finishes starting.

---

## ActiveCallNotificationBuilder

**File**: `kotlin/com/webtrit/callkeep/notifications/ActiveCallNotificationBuilder.kt`

Builds the single persistent notification for calls in progress. There is always **one**
notification (`NOTIFICATION_ID = 1`) summarizing every active call, not one entry per call.

- **Title**: singular or plural depending on the number of calls.
- **Text**: the caller names of all active calls, joined.
- **Action**: one Hang up button. Its `PendingIntent` targets `ActiveCallService` with the
  `Decline` action and carries the **first** call's bundle in the extras.
- **Behavior**: `setOngoing(true)`, `setOnlyAlertOnce(true)`, media style with the action in
  compact view. As an ongoing FGS notification it cannot be swiped away.

---

## Standalone builders

**Files**: `kotlin/com/webtrit/callkeep/notifications/StandaloneIncomingCallNotificationBuilder.kt`,
`StandaloneActiveCallNotificationBuilder.kt`

Internal builders used by `StandaloneCallService` (the single-process fallback path that does
not register with Telecom). They mirror the ringing and active variants on the same channels;
the standalone answer goes through `StandaloneAnswerTrampolineActivity` and all action intents
target `StandaloneCallService` instead of the dual-process services.

---

## AudioManager

**File**: `kotlin/com/webtrit/callkeep/managers/AudioManager.kt`

Handles ringtone, ringback and call-waiting tone playback, plus device-capability queries.

| Method                        | Description                                                            |
|-------------------------------|------------------------------------------------------------------------|
| `startRingtone(sound)`        | Play incoming-call ringtone (asset cache path or system default) with vibration |
| `stopRingtone()`              | Stop ringtone and vibration                                            |
| `startRingback(asset)`        | Play outgoing ringback tone                                            |
| `stopRingback()`              | Stop ringback                                                          |
| `startCallWaitingTone()`      | Play the call-waiting tone for a second incoming call                  |
| `stopCallWaitingTone()`       | Stop the call-waiting tone                                             |
| `isSupportEarpiese()` etc.    | Query available devices (earpiece, speakerphone, wired headset, Bluetooth) |

---

## Related Components

- [background-services.md](background-services.md) - services that host these notifications
- [incoming-call-handling.md](incoming-call-handling.md) - end-to-end incoming-call delivery
- [phone-connection.md](phone-connection.md) - triggers `showIncomingCallNotification` /
  `showActiveCallNotification` / the cancel calls from connection state changes
- [foreground-service.md](foreground-service.md) - registers the notification channels on setup
