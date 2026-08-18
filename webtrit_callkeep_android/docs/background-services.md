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

- Started by `NotificationManager.showIncomingCallNotification()`, whose only caller is
  `PhoneConnection.onShowIncomingCallUi()` in the `:callkeep_core` process. (The push path
  gets there indirectly: `reportNewIncomingCall()` routes the call into
  `CallkeepCore.startIncomingCall()`, Telecom registers it, and this service starts from the
  resulting connection callback.)
- `onCreate()` - registers the internal release receiver (see below), subscribes to
  `CallkeepCore` events via `addConnectionEventListener(this)`, and wires the handlers.
  It does **not** promote to foreground yet.
- `onStartCommand(IC_INITIALIZE)` - delegates to `IncomingCallHandler`, which shows the
  ringing notification and promotes the service to foreground (per-call notification id,
  1000+). The call itself was already registered with Telecom by `PhoneConnectionService`
  before this service started. Every `onStartCommand` path returns `START_NOT_STICKY`: if
  the OS kills the service, a restart with a null intent must not re-post a notification
  for a call that is gone.
- The notification's Answer and Decline buttons also enter through `onStartCommand`, as
  service `PendingIntent`s (`NotificationAction.Answer` / `Decline`); Answer additionally
  drops the notification buttons right away.
- Release (the end of the ringing phase) is delivered via an **internal broadcast**
  (`IC_RELEASE_WITH_ANSWER` / `IC_RELEASE_WITH_DECLINE`), not via `onStartCommand`: the
  receiver only lives while the service is alive, so a release arriving after the service
  stopped goes nowhere instead of restarting it.
- Two safety-net timeouts force-stop the service if the normal flow stalls: an independent
  60 s timeout armed at launch, and a 2 s stop timeout armed when the release arrives.
- `onDestroy()` - unsubscribes, stops foreground, and explicitly cancels the current
  notification (on some Samsung builds `stopForeground(REMOVE)` alone leaves it in the
  shade), then tears the isolate down.

### Answered-call notification handoff

Once the call is answered (from the notification button, the system call UI, a headset or a
watch) the ringing notification is replaced in place by its **silent form** - same call, no
buttons - so the user is not offered Answer for a call already taken while the app finishes
starting. When `ActiveCallService` posts the in-progress notification, it broadcasts
`IC_ACTIVE_CALL_VISIBLE`; on receiving it, `IncomingCallService` gives its own notification
up (`stopForeground`) so the shade does not describe the same call twice. The service itself
keeps running - no longer foreground - until the connection is handed over to the app or a
safety-net timeout stops it.

### Connection Event Listener

`IncomingCallService` implements `ConnectionEventListener` and receives events routed by
`CallkeepCore`. It only acts on `AnswerCall` - when the system UI or the user answers,
`PhoneConnectionService` fires `AnswerCall`, which drops the notification buttons and forwards
to `CallLifecycleHandler.performAnswerCall()`. `DeclineCall` and `HungUp` are handled via the
`IC_RELEASE_WITH_DECLINE` broadcast path instead to avoid a double `performEndCall` race.

### Key Handlers (Composition)

| Handler                                                | Responsibility                                                            |
|--------------------------------------------------------|---------------------------------------------------------------------------|
| `IncomingCallHandler`                                  | Owns the incoming-call notification and the foreground promotion          |
| `CallLifecycleHandler`                                 | Handles answer/decline events; dispatches to Flutter isolate              |
| `FlutterIsolateCommunicator` / `FlutterIsolateHandler` | Manages the background Flutter isolate lifecycle                          |

### Related Bootstrap API

`BackgroundPushNotificationIsolateBootstrapApi` (registered in `WebtritCallkeepPlugin`):

| Method                                                            | Description                                 |
|-------------------------------------------------------------------|---------------------------------------------|
| `initializePushNotificationCallback(callbackDispatcher, onNotificationSync)` | Stores the two Dart entry-point handles |
| `reportNewIncomingCall(callId, handle, displayName, hasVideo)`    | Builds `CallMetadata` and routes it into `CallkeepCore.startIncomingCall()` (Telecom registration; `IncomingCallService` starts later, from the connection callback) |

---

## ActiveCallService

**File**: `kotlin/com/webtrit/callkeep/services/services/active_call/ActiveCallService.kt`

**Annotation**: `@Keep`

**Type**: Foreground service (`foregroundServiceType=phoneCall|microphone|camera`)

### Responsibility

Owns the single **active call notification** summarizing every call in progress (one
notification, `ActiveCallNotificationBuilder.NOTIFICATION_ID = 1`). It renders whatever
call list it is started with; the list itself lives in `NotificationManager`'s static
`activeCalls` state in the `:callkeep_core` process (see notifications.md), and this
service only ever sees the copy serialized into each start intent.

### Lifecycle

- Started (and re-started on every change) by `NotificationManager.upsertActiveCallsService()`
  whenever a call is added, removed or reordered: the service receives the full call list in
  the `metadata` intent extra and re-posts the notification.
- Stopped by `NotificationManager` (`context.stopService`) when the call list becomes empty,
  or by `NotificationManager.tearDown()`. The service never stops itself.
- `onStartCommand` promotes to foreground with a type set computed per start: `phoneCall`
  always; `microphone` whenever the permission is granted (deliberately not conditioned on
  audio state - some OEM builds block microphone access with the screen off if the type is
  missing); `camera` when a video call is present and the permission is granted.
- After posting the notification it broadcasts `IC_ACTIVE_CALL_VISIBLE` so
  `IncomingCallService` can give up its now-redundant notification (see above).
- Returns `START_STICKY` for metadata starts (the Decline action returns
  `START_NOT_STICKY`): if the process is killed mid-call, the OS restarts the service.

### Notification action

The notification offers one Hang up button. Its intent carries the first call's bundle in
the extras, but the service **ignores the extras**: it hangs up the first call of its
in-memory list via `CallkeepCore.startHungUpCall(call)`, falling back to
`CallkeepCore.tearDownService()` when that list is empty - which is exactly the state after
a sticky restart.

### Known limitation: sticky restart with a null intent

A `START_STICKY` restart after a main-process kill delivers a **null intent**, so the
restarted instance builds an empty call list and re-posts a half-empty ongoing notification.
Nothing in this process stops that orphaned instance: the service has no `stopSelf()`, the
restart bypasses `NotificationManager` (whose call list lives in `:callkeep_core`) entirely,
and the Hang up fallback (`tearDownService()`) tears down the connection services without
stopping this one. If a live `PhoneConnection` still exists in `:callkeep_core`, its
disconnect empties that list and stops the service cross-process; with no live connection
left (or with `:callkeep_core` killed as well), the notification can only be removed by
force-stopping the app.

---

## Related Components

- [plugin.md](plugin.md) - registers bootstrap APIs on engine attach
- [pigeon-apis.md](pigeon-apis.md) - bootstrap API definitions
- [notifications.md](notifications.md) - notification builders used by these services
- [incoming-call-handling.md](incoming-call-handling.md) - end-to-end incoming-call delivery
- [foreground-service.md](foreground-service.md) - coordinates with `ActiveCallService`
- [callkeep-core.md](callkeep-core.md) - `ConnectionEventListener` API used by `IncomingCallService`
