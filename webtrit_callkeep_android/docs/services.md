# Android Services

## ForegroundService

**Package:** `com.webtrit.callkeep.services.services.foreground`
**Process:** main
**Type:** Bound service (no `startForegroundService`, only `bindService`)
**Implements:** `PHostApi` (Pigeon `@HostApi`)

### Role

The central bridge between the Android Telecom layer and the Flutter main isolate. It is alive as long as the Flutter Activity is attached to the plugin. It:

1. Receives all `@HostApi` calls from Flutter (start/answer/end call, mute, hold, DTMF, audio device, etc.).
2. Translates them into `startService` intents to `PhoneConnectionService` (cross-process).
3. Receives `ConnectionServicePerformBroadcaster` events from `PhoneConnectionService` and calls back into Flutter via `PDelegateFlutterApi`.

### Lifecycle

```
WebtritCallkeepPlugin.onAttachedToActivity()
    └── bindService(ForegroundService, BIND_AUTO_CREATE)
            └── ForegroundService.onCreate()
                    └── registers ConnectionServicePerformBroadcaster receiver
            └── ForegroundService.onBind() → returns LocalBinder
            └── onServiceConnected()
                    ├── PHostApi.setUp(messenger, foregroundService)
                    └── foregroundService.flutterDelegateApi = PDelegateFlutterApi(messenger)

WebtritCallkeepPlugin.onDetachedFromActivity()
    └── unbindService()
            └── ForegroundService.onUnbind()
                    └── stopSelf()
            └── ForegroundService.onDestroy()
                    ├── unregisters broadcast receiver
                    ├── retryManager.clear()
                    └── outgoingCallbacksManager.clear()
```

### Outgoing call retry logic

Starting an outgoing call may fail with `SecurityException(CALL_PHONE)` if the self-managed `PhoneAccount` is not yet registered in Telecom (e.g., right after a cold start). The service uses `RetryManager` with `CallPhoneSecurityRetryDecider`:

```
Attempts: up to 5
Delays:   750 ms → 1125 ms → 1687 ms → 2531 ms → 3796 ms (×1.5 backoff, max 5000 ms)
Retry on: SecurityException where message contains "CALL_PHONE"
```

Each retry also re-registers the `PhoneAccount` via `TelephonyUtils.registerPhoneAccount()`.

A global 5-second timeout (`OutgoingCallbacksManager`) ensures the Flutter callback is always resolved even if the Telecom system never replies.

### `onDelegateSet` — state restoration on hot restart

When Flutter reconnects after a hot restart, it calls `onDelegateSet()`. The service iterates all tracked connections and forces an audio state update:

```kotlin
tracked.forEach { metadata ->
    PhoneConnectionService.forceUpdateAudioState(baseContext, metadata)
}
```

This re-triggers `AudioDeviceSet` / `AudioDevicesUpdate` broadcasts so Flutter's UI is in sync.

---

## PhoneConnectionService

**Package:** `com.webtrit.callkeep.services.services.connection`
**Process:** `:callkeep_core`
**Type:** Android `ConnectionService` (system-managed lifecycle)
**Declared permission:** `android.permission.BIND_TELECOM_CONNECTION_SERVICE`

### Role

The Android `ConnectionService` implementation. The Telecom framework binds to it when a call is placed or received. It owns `PhoneConnection` objects and forwards all call events back to the main process via `ConnectionServicePerformBroadcaster`.

### Lifecycle

```
System binds (Telecom) → onCreate()
    ├── ContextHolder.init(applicationContext)  // own process initialization
    ├── isRunning = true
    ├── creates PhoneConnectionServiceDispatcher
    └── creates ActivityWakelockManager + ProximitySensorManager

Incoming call:
    TelecomManager.addNewIncomingCall() → onCreateIncomingConnection()
        ├── duplicate/existing-incoming guard
        ├── PhoneConnection.createIncomingPhoneConnection()
        └── broadcast ConnectionAdded

Outgoing call:
    TelecomManager.placeCall() → onCreateOutgoingConnection()
        ├── duplicate guard
        ├── PhoneConnection.createOutgoingPhoneConnection()
        └── broadcast ConnectionAdded

Actions from main process (via startService intents):
    onStartCommand() → PhoneConnectionServiceDispatcher.dispatch(ServiceAction, metadata)

App swiped away:
    onTaskRemoved() → cleanupResources() → stopSelf()
```

### `PhoneConnection` state machine

```
createIncomingPhoneConnection()  →  STATE_RINGING
createOutgoingPhoneConnection()  →  STATE_DIALING

STATE_RINGING
    ├── onAnswer()    → STATE_ACTIVE  + broadcast AnswerCall
    └── onDisconnect() → STATE_DISCONNECTED + broadcast DeclineCall

STATE_DIALING
    ├── establish()   → STATE_ACTIVE  + broadcast OngoingCall
    └── onDisconnect() → STATE_DISCONNECTED + broadcast HungUp

STATE_ACTIVE
    ├── onHold()      → STATE_HOLDING + broadcast ConnectionHolding
    ├── onDisconnect() → STATE_DISCONNECTED + broadcast HungUp
    ├── onMuteStateChanged() → broadcast AudioMuting
    ├── onCallEndpointChanged()  → broadcast AudioDeviceSet (API 34+)
    └── onAvailableCallEndpointsChanged() → broadcast AudioDevicesUpdate (API 34+)

STATE_HOLDING
    └── onUnhold()    → STATE_ACTIVE + broadcast ConnectionHolding
```

### `PhoneConnectionServiceDispatcher`

Translates `ServiceAction` intents into `PhoneConnection` method calls, handles the `ProximitySensorManager` and `ActivityWakelockManager`, and dispatches `ConnectionLifecycleAction` events (created, changed, destroyed).

### Audio management

- On API 34+: audio routing is handled through Telecom endpoints (`requestCallEndpointChange`).
- On older APIs: `AudioManager.setSpeakerphoneOn()` is used directly.
- `preventAutoSpeakerEnforcement` flag prevents the service from forcing speaker on video-upgrade if the call started as audio-only.

---

## SignalingIsolateService

**Package:** `com.webtrit.callkeep.services.services.signaling`
**Process:** main
**Type:** Foreground service, `foregroundServiceType="phoneCall"`
**Implements:** `PHostBackgroundSignalingIsolateApi`

### Role

A long-lived foreground service that hosts a Flutter background isolate. That isolate maintains a persistent socket/signaling connection with the server. When the signaling layer detects an incoming call, it calls back into this service via Pigeon, which forwards it to `PhoneConnectionService`.

### Lifecycle

```
Start triggers:
  - Android < 14: ForegroundCallBootReceiver on BOOT_COMPLETED / MY_PACKAGE_REPLACED
  - Android ≥ 14: WebtritCallkeepPlugin.onAttachedToActivity (explicit start)
  - WorkManager reschedule: SignalingServiceBootWorker.enqueue() on service destroy

onCreate()
    ├── registers ActivityLifecycleBroadcaster receiver
    ├── registers SignalingStatusBroadcaster receiver
    ├── startForegroundService() — shows persistent notification
    ├── acquires PARTIAL_WAKE_LOCK
    └── FlutterEngineHelper.startOrAttachEngine(callbackDispatcher)

onStartCommand()
    ├── handles ANSWER/DECLINE actions (forwards to PhoneConnectionService)
    ├── ensures notification is visible
    └── FlutterEngineHelper.startOrAttachEngine()

onDestroy()
    ├── unregisters broadcasters
    ├── releases wake lock
    ├── flutterEngineHelper.detachAndDestroyEngine()
    └── if still enabled: SignalingServiceBootWorker.enqueue() — reschedules
```

### Flutter isolate synchronization

When `ActivityLifecycleBroadcaster` or `SignalingStatusBroadcaster` fires, `synchronizeSignalingIsolate()` calls:

```kotlin
isolateSignalingFlutterApi?.onWakeUpBackgroundHandler(
    wakeUpHandler,
    PCallkeepServiceStatus(lifecycleEvent, mainSignalingStatus),
    callData = null
)
```

This wakes the Flutter isolate and delivers lifecycle + signaling status.

### Boot / restart resilience

`SignalingServiceBootWorker` (WorkManager `OneTimeWorkRequest`) is enqueued with a 1-second delay on `onDestroy` or `onTaskRemoved`. If the service was still enabled in `StorageDelegate`, WorkManager restarts it after the delay. `ForegroundCallBootReceiver` handles `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, and `QUICKBOOT_POWERON`.

---

## IncomingCallService

**Package:** `com.webtrit.callkeep.services.services.incoming_call`
**Process:** main
**Type:** Foreground service, `foregroundServiceType="phoneCall"`

### Role

A transient service that handles incoming calls arriving via push notifications (e.g., FCM). It shows a high-priority incoming call notification, optionally starts a Flutter isolate, and coordinates answer/decline with `PhoneConnectionService`.

### Lifecycle

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

The 2-second delay before `stopSelf()` on release allows the Flutter isolate to complete any final cleanup (e.g., notify the signaling server about the declined call).

### Isolate launch policy

`DefaultIsolateLaunchPolicy.shouldLaunch()` returns `true` when:
- The app is NOT in the foreground **OR**
- `launchBackgroundIsolateEvenIfAppIsOpen` is set in `StorageDelegate`.

---

## ActiveCallService

**Package:** `com.webtrit.callkeep.services.services.active_call`
**Process:** main
**Type:** Foreground service, `foregroundServiceType="phoneCall|microphone|camera"`

### Role

Shows and manages the persistent "active call" notification while a call is in progress. Declares `microphone` and `camera` foreground service types so the OS allows audio/video capture.

---

## FlutterEngineHelper

Used by `SignalingIsolateService` and `IncomingCallService` to host a background Flutter isolate.

```kotlin
startOrAttachEngine()
    if (backgroundEngine == null):
        flutterLoader.ensureInitializationComplete()
        backgroundEngine = FlutterEngine(context)
        callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
        engine.dartExecutor.executeDartCallback(DartCallback(assets, entrypoint, callbackInfo))
        engine.serviceControlSurface.attachToService(service, null, false)
    else if (!isEngineAttached):
        engine.serviceControlSurface.attachToService(service, null, false)

detachAndDestroyEngine()
    engine.serviceControlSurface.detachFromService()
    engine.destroy()
    backgroundEngine = null
```

The `callbackHandle` is the raw handle of `_isolatePluginCallbackDispatcher` (from `lib/src/webtrit_callkeep_android.dart`), stored in `StorageDelegate` by the bootstrap APIs before the service is started.
