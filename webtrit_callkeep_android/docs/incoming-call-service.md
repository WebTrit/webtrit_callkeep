# IncomingCallService

**Package:** `com.webtrit.callkeep.services.services.incoming_call`
**Process:** main
**Type:** Foreground service, `foregroundServiceType="phoneCall"`

---

## Role

A transient service that handles incoming calls arriving via push notifications (e.g., FCM). It
shows a high-priority incoming call notification, optionally starts a Flutter background isolate,
and coordinates answer/decline actions with `PhoneConnectionService`.

Unlike `SignalingIsolateService`, this service is short-lived: it starts when a push notification
arrives and stops itself a few seconds after the call is answered or declined.

---

## Lifecycle

```
Start: context.startForegroundService(IC_INITIALIZE intent)

onStartCommand actions:
  IC_INITIALIZE         → handleLaunch(metadata)
                             ├── show incoming call notification (IncomingCallNotificationBuilder)
                             └── IncomingCallHandler.handle(metadata)
                                  └── optionally launch Flutter isolate (DefaultIsolateLaunchPolicy)

  NotificationAction.Answer  → reportAnswerToConnectionService(metadata)
                                   → PhoneConnectionService.startAnswerCall()

  NotificationAction.Decline → reportHungUpToConnectionService(metadata)
                                   → PhoneConnectionService.startHungUpCall()

  IC_RELEASE_WITH_ANSWER  → handleRelease(answered=true)
                                ├── releaseIncomingCallNotification()
                                └── 2-second timer → stopSelf()

  IC_RELEASE_WITH_DECLINE → handleRelease(answered=false)
                                ├── replace notification with "release" notification
                                └── 2-second timer → stopSelf()

ConnectionServicePerformBroadcaster events (while running):
  AnswerCall  → callLifecycleHandler.performAnswerCall()
  DeclineCall → callLifecycleHandler.performEndCall()
  HungUp      → callLifecycleHandler.performEndCall()
```

The 2-second delay before `stopSelf()` on release gives the Flutter isolate time to complete final
cleanup (e.g., notifying the signaling server about the declined call).

---

## Notification

`IncomingCallNotificationBuilder` shows a high-priority notification with:

- Caller display name and handle.
- **Answer** and **Decline** action buttons.
- Full-screen intent for lock screen display.

When the call is released with answer, the notification is dismissed. When released with decline, a
brief "call ended" notification replaces it before the service stops.

---

## Isolate launch policy

`DefaultIsolateLaunchPolicy.shouldLaunch()` returns `true` when:

- The app is **not** in the foreground, **OR**
- `launchBackgroundIsolateEvenIfAppIsOpen` is set in `StorageDelegate.IncomingCallService`.

If the app is already in the foreground, the main Flutter isolate receives the call directly and
there is no need to spin up a second engine.

---

## Pre-populating `connectionTracker` for cold-start answer

When the app is not running and the user taps **Answer** on the notification before the main isolate
has started, `IncomingCallService` pre-populates `ForegroundService.connectionTracker` with the call
metadata. This ensures the lock-screen flag logic in `WebtritCallkeepPlugin.onStateChanged` works
correctly even on a cold start.

---

## Answer/Decline from notification

When the user taps an action button:

1. `IncomingCallService.onStartCommand` receives `NotificationAction.Answer` or
   `NotificationAction.Decline`.
2. It calls `PhoneConnectionService.startAnswerCall()` or `startHungUpCall()`, which sends a
   `startService` intent to `PhoneConnectionService`.
3. `PhoneConnectionService` calls `PhoneConnection.onAnswer()` or `hungUp()`, transitioning the
   connection state.
4. The resulting `ConnectionPerform.AnswerCall` or `DeclineCall` broadcast is received by
   `IncomingCallService` (via its own `ConnectionServicePerformBroadcaster` receiver) and forwarded
   to the Flutter isolate via `callLifecycleHandler`.

See [call-flows.md](call-flows.md#incoming-call--push-notification-path) for the full trace.
