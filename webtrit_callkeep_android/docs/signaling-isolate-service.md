# SignalingIsolateService

**Package:** `com.webtrit.callkeep.services.services.signaling`
**Process:** main
**Type:** Foreground service, `foregroundServiceType="phoneCall"`
**Implements:** `PHostBackgroundSignalingIsolateApi`

---

## Role

A long-lived foreground service that hosts a Flutter background isolate. The isolate maintains a
persistent socket/signaling connection with the server. When the signaling layer detects an incoming
call, it calls back into this service via Pigeon, which forwards the call to
`PhoneConnectionService`.

---

## How it starts

| Condition                       | Trigger                                                                                                                                                            |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Android < 14                    | `ForegroundCallBootReceiver` on `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `QUICKBOOT_POWERON`                                              |
| Android ≥ 14 (API 34+)          | `WebtritCallkeepPlugin.onAttachedToActivity` — started explicitly because `FOREGROUND_SERVICE_TYPE_PHONE_CALL` cannot be launched from a boot broadcast on API 34+ |
| Service destroyed while enabled | `SignalingServiceBootWorker` (WorkManager, 1 s delay) re-enqueues the service                                                                                      |

The service is only started if `StorageDelegate.SignalingService.enabled` is `true`.

---

## Lifecycle

```
onCreate()
    ├── registers ActivityLifecycleBroadcaster receiver
    ├── registers SignalingStatusBroadcaster receiver
    ├── startForeground() — shows persistent notification
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
    └── if still enabled: SignalingServiceBootWorker.enqueue() — reschedules restart
```

---

## Flutter isolate synchronization

When `ActivityLifecycleBroadcaster` or `SignalingStatusBroadcaster` fires,
`synchronizeSignalingIsolate()` calls:

```kotlin
isolateSignalingFlutterApi?.onWakeUpBackgroundHandler(
    wakeUpHandler,
    PCallkeepServiceStatus(lifecycleEvent, mainSignalingStatus),
    callData = null
)
```

This wakes the Flutter isolate and delivers the current activity lifecycle state and signaling
status so the isolate can decide whether to reconnect.

---

## Boot resilience

`SignalingServiceBootWorker` is a WorkManager `OneTimeWorkRequest` enqueued with a 1-second delay on
`onDestroy` or `onTaskRemoved`. If the service was still enabled in `StorageDelegate`, WorkManager
restarts it after the delay.

`ForegroundCallBootReceiver` handles:

- `BOOT_COMPLETED`
- `LOCKED_BOOT_COMPLETED`
- `MY_PACKAGE_REPLACED`
- `QUICKBOOT_POWERON`

---

## `IsolateLaunchPolicy`

Controls whether a new Flutter engine should be started. The default policy skips launch if:

- An engine is already running and attached.
- `StorageDelegate.SignalingService.enabled` is `false`.

---

## Pigeon API: `PHostBackgroundSignalingIsolateApi`

| Method                                                | Called by                  | Effect                                             |
|-------------------------------------------------------|----------------------------|----------------------------------------------------|
| `incomingCall(callId, handle, displayName, hasVideo)` | Flutter background isolate | Calls `PhoneConnectionService.startIncomingCall()` |
| `endCall(callId)`                                     | Flutter background isolate | Calls `PhoneConnectionService.startHungUpCall()`   |

See [call-triggers.md](call-triggers.md#2-persistent-signaling-signalingisolateservice) for the full
incoming call flow from this service.
