# Call Triggers

There are four distinct ways a call can be initiated in `webtrit_callkeep_android`. Each has a
different entry point and lifecycle.

---

## 1. Push notification (`IncomingCallService`)

Used when the app receives a push notification (e.g., FCM) signaling an incoming call. The app
process may not be running.

**Trigger:** Push notification received → app calls
`BackgroundPushNotificationIsolateBootstrapApi.reportNewIncomingCall()` (or a `PendingIntent` starts
`IncomingCallService` directly).

**Entry point:** `IncomingCallService.onStartCommand(IC_INITIALIZE)`

**Flow:**

1. `IncomingCallService` starts as a foreground service and shows a high-priority incoming call
   notification.
2. `PhoneConnectionService.startIncomingCall()` is called, which calls
   `TelecomManager.addNewIncomingCall()`.
3. Telecom calls back `PhoneConnectionService.onCreateIncomingConnection()`, creating a
   `PhoneConnection` in `STATE_RINGING`.
4. `DefaultIsolateLaunchPolicy` decides whether to launch a Flutter background isolate (skipped if
   the app is already in the foreground).
5. When the user answers or declines (via notification buttons or the system call UI), the result
   flows back through `PhoneConnectionService` → `ConnectionServicePerformBroadcaster` →
   `ForegroundService` → Flutter delegate.

See [incoming-call-service.md](incoming-call-service.md)
and [call-flows.md](call-flows.md#incoming-call--push-notification-path).

---

## 2. Persistent signaling (`SignalingIsolateService`)

Used when the app has a long-lived WebSocket connection maintained by the `SignalingIsolateService`
background isolate.

**Trigger:** WebSocket message arrives in the Flutter background isolate → isolate calls
`PHostBackgroundSignalingIsolateApi.incomingCall()` via Pigeon.

**Entry point:** `SignalingIsolateService.incomingCall(callId, handle, displayName, hasVideo)`

**Flow:**

1. The Flutter isolate hosted inside `SignalingIsolateService` detects an incoming call on the
   signaling channel.
2. It calls `PHostBackgroundSignalingIsolateApi.incomingCall()`, which routes to
   `SignalingIsolateService.incomingCall()`.
3. `PhoneConnectionService.startIncomingCall()` is called → `TelecomManager.addNewIncomingCall()`.
4. Telecom calls `PhoneConnectionService.onCreateIncomingConnection()`, creating a `PhoneConnection`
   in `STATE_RINGING` and showing the system call UI.
5. Answer/decline events flow back via `ConnectionServicePerformBroadcaster` → `ForegroundService` →
   Flutter delegate.

See [signaling-isolate-service.md](signaling-isolate-service.md)
and [call-flows.md](call-flows.md#incoming-call--signaling-service-path).

---

## 3. Foreground direct call (Dart API)

Used for **outgoing calls** initiated from the running Flutter app.

**Trigger:**
`WebtritCallkeepAndroid.startCall(callId, handle, displayName, hasVideo, proximityEnabled)` called
from Dart.

**Entry point:** `ForegroundService.startCall()` (via Pigeon `PHostApi.startCall()`)

**Flow:**

1. `ForegroundService` registers a timeout callback via `OutgoingCallbacksManager` (5 s) and starts
   the retry loop via `RetryManager`.
2. `PhoneConnectionService.startOutgoingCall()` calls `TelecomManager.placeCall()`.
3. Telecom calls `PhoneConnectionService.onCreateOutgoingConnection()`, creating a `PhoneConnection`
   in `STATE_DIALING`.
4. When the remote side answers, `PhoneConnection.establish()` fires → `STATE_ACTIVE` → broadcast
   `OngoingCall`.
5. `ForegroundService` resolves the Dart callback and calls
   `PDelegateFlutterApi.performStartCall()`.
6. On `SecurityException(CALL_PHONE)` (PhoneAccount not yet registered), `RetryManager` retries up
   to 5 times with exponential backoff.

See [foreground-service.md](foreground-service.md) and [call-flows.md](call-flows.md#outgoing-call).

---

## 4. SMS fallback (`IncomingCallSmsTriggerReceiver`) — optional

Used as a fallback when push notifications are unreliable or unavailable.

**Trigger:** SMS received matching a configured regex →
`IncomingCallSmsTriggerReceiver.onReceive()`.

**Entry point:** `IncomingCallSmsTriggerReceiver.onReceive(context, intent)`

**Flow:**

1. The receiver parses the SMS body against the regex from `StorageDelegate.IncomingCallSmsConfig`.
2. If it matches, it extracts call metadata and starts `IncomingCallService` (same path as trigger
   #1).

**Note:** Disabled by default. Requires adding `RECEIVE_SMS` permission and
`IncomingCallSmsTriggerReceiver` to the host app's `AndroidManifest.xml`. Configure via
`SmsReceptionConfigBootstrapApi` / `PHostSmsReceptionConfigApi`.
