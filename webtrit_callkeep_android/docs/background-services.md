# Background Services

Three foreground services operate in the **main process** to handle calls that arrive while the
Flutter app is backgrounded or killed.

---

## SignalingIsolateService

**File**: `kotlin/com/webtrit/callkeep/services/services/signaling/SignalingIsolateService.kt`

**Annotation**: `@Keep`

**Type**: Persistent foreground service (`foregroundServiceType=phoneCall`)

### Responsibility

Hosts a persistent background Flutter isolate that maintains a signaling connection (e.g.
WebSocket) to the server. When the server pushes an incoming call notification, the isolate
triggers the callkeep stack to register the call with Telecom.

### Lifecycle

- Started by `BackgroundSignalingIsolateBootstrapApi.startService()` (called from Dart).
- Automatically restarted on device boot via `ForegroundCallBootReceiver` (listens for
  `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `LOCKED_BOOT_COMPLETED`).
- On Android 14+ (`API >= 34`) the plugin also starts it from `onAttachedToActivity()` if a
  callback has already been registered.
- Stopped by `BackgroundSignalingIsolateBootstrapApi.stopService()`.

### Broadcast Receivers (internal)

- `signalingStatusReceiver` — listens for `SignalingStatusBroadcaster` events (e.g., signaling
  connected/disconnected) and forwards them to the Flutter isolate.
- `lifecycleEventReceiver` — listens for `ActivityLifecycleBroadcaster` events so the isolate
  knows whether the foreground UI is active.

### Flutter Engine

Uses `FlutterEngineHelper` to spin up an isolated Flutter engine with the callback dispatcher
registered by `BackgroundSignalingIsolateBootstrapApi.initializeSignalingServiceCallback()`.

### Related Bootstrap API

`BackgroundSignalingIsolateBootstrapApi` (registered in `WebtritCallkeepPlugin`):

| Method                                               | Description                                             |
|------------------------------------------------------|---------------------------------------------------------|
| `initializeSignalingServiceCallback(callbackHandle)` | Stores the Dart entry-point handle in `StorageDelegate` |
| `configureSignalingService(config)`                  | Persists service configuration                          |
| `startService()`                                     | Starts `SignalingIsolateService`                        |
| `stopService()`                                      | Stops `SignalingIsolateService`                         |

---

## IncomingCallService

**File**: `kotlin/com/webtrit/callkeep/services/services/incoming_call/IncomingCallService.kt`

**Annotation**: `@Keep`

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
  Android's 10-second ANR window for foreground service start.
- `onStartCommand()` — replaces placeholder with the actual incoming-call notification; delegates
  to `IncomingCallHandler` and `CallLifecycleHandler`.
- `release()` — stops the foreground service (called after call ends or timeout).

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

## Boot Receiver

**File**:
`kotlin/com/webtrit/callkeep/services/services/signaling/receivers/ForegroundCallBootReceiver.kt`

**Listens for**: `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
`QUICKBOOT_POWERON`

Starts `SignalingIsolateService` after device reboot if a callback has been registered, ensuring
the signaling connection is re-established automatically.

---

## Related Components

- [plugin.md](plugin.md) — starts `SignalingIsolateService` on attach
- [pigeon-apis.md](pigeon-apis.md) — bootstrap API definitions
- [notifications.md](notifications.md) — notification builders used by these services
- [foreground-service.md](foreground-service.md) — coordinates with `ActiveCallService`
