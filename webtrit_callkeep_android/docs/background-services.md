# Background Services

Two foreground services operate in the **main process** to handle calls that arrive while the
Flutter app is backgrounded or killed.

---

## IncomingCallService

**File**: `kotlin/com/webtrit/callkeep/services/services/incoming_call/IncomingCallService.kt`

**Annotation**: `@Keep`

**Implements**: `ConnectionEventListener`

**Type**: One-shot foreground service (`foregroundServiceType=phoneCall`)

### Responsibility

Spawned when an FCM push notification (or SMS trigger) announces an incoming call. It:

1. Starts a short-lived Flutter background isolate.
2. Shows the incoming-call notification / system UI.
3. Waits for the user or app code to answer or decline.
4. Exits when the call is answered, declined, or timed out.

### Lifecycle

- Started via:
  - `BackgroundPushNotificationIsolateBootstrapApi.reportNewIncomingCall()` (from Dart or a
      background message handler).
  - `NotificationManager.showIncomingCallNotification()` (from FCM handler code).
- `onCreate()` — calls `startForeground()` with a placeholder notification immediately to avoid
  Android's 10-second ANR window for foreground service start; subscribes to `CallkeepCore`
  events via `CallkeepCore.instance.addConnectionEventListener(this)`.
- `onStartCommand()` — replaces placeholder with the actual incoming-call notification; delegates
  to `IncomingCallHandler` and `CallLifecycleHandler`.
- `onDestroy()` — calls `CallkeepCore.instance.removeConnectionEventListener(this)`.
- `release()` — stops the foreground service (called after call ends or timeout).

### Connection Event Listener

`IncomingCallService` implements `ConnectionEventListener` and receives events routed by
`CallkeepCore`. It only acts on `AnswerCall` — when the system UI or the user taps answer,
`PhoneConnectionService` fires `AnswerCall` which `onConnectionEvent()` forwards to
`CallLifecycleHandler.performAnswerCall()`. `DeclineCall` and `HungUp` are handled via the
`IC_RELEASE_WITH_DECLINE` intent path instead to avoid a double `performEndCall` race.

### Key Handlers (Composition)

| Handler                                                | Responsibility                                                            |
|--------------------------------------------------------|---------------------------------------------------------------------------|
| `IncomingCallHandler`                                  | Initializes the incoming call: registers with Telecom, shows notification |
| `CallLifecycleHandler`                                 | Handles answer/decline events; dispatches to Flutter isolate              |
| `FlutterIsolateCommunicator` / `FlutterIsolateHandler` | Manages the background Flutter isolate lifecycle                          |

### Related Bootstrap API

`BackgroundPushNotificationIsolateBootstrapApi` (registered in `WebtritCallkeepPlugin`):

| Method                                               | Description                                 |
|------------------------------------------------------|---------------------------------------------|
| `initializePushNotificationCallback(callbackHandle)` | Stores Dart entry-point handle              |
| `configureSignalingService(config)`                  | Persists service configuration              |
| `reportNewIncomingCall(callId, metadata)`            | Starts `IncomingCallService` with call data |

---

## ActiveCallService

**File**: `kotlin/com/webtrit/callkeep/services/services/active_call/ActiveCallService.kt`

**Annotation**: `@Keep`

**Type**: Foreground service (`foregroundServiceType=phoneCall|microphone|camera`)

### Responsibility

Displays and manages the **active call notification** for ongoing calls. Supports multiple
simultaneous calls. Updated by `ForegroundService` whenever call state changes (e.g., mute,
hold, call added/ended).

### Lifecycle

- Started by `NotificationManager.showActiveCallNotification()`.
- Stopped by `NotificationManager.tearDown()` when no calls remain.

### Notification Actions

The active call notification offers per-call actions (end, mute, hold) via `PendingIntent`
buttons that send explicit intents back to the service.

---

## Related Components

- [plugin.md](plugin.md) — registers bootstrap APIs on engine attach
- [pigeon-apis.md](pigeon-apis.md) — bootstrap API definitions
- [notifications.md](notifications.md) — notification builders used by these services
- [foreground-service.md](foreground-service.md) — coordinates with `ActiveCallService`
- [callkeep-core.md](callkeep-core.md) — `ConnectionEventListener` API used by `IncomingCallService`
