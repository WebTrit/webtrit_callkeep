# Common Utilities

Shared infrastructure classes used across services and both OS processes.

---

## `StorageDelegate`

**File:** `common/StorageDelegate.kt`

A `SharedPreferences`-backed singleton that persists configuration across process restarts. Because
both processes (main and `:callkeep_core`) can be killed independently, all configuration that needs
to survive a restart is written here.

Each process can read `StorageDelegate`, but writes are expected from the main process only (via
Pigeon bootstrap APIs).

### Nested configuration objects

| Object                                  | What it persists                                                                                                                                                                                            |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `StorageDelegate.Sound`                 | Ringtone path, ringback tone path.                                                                                                                                                                          |
| `StorageDelegate.SignalingService`      | Callback handle for `_isolatePluginCallbackDispatcher`, wakeup handler handle, notification text (title/body), `enabled` flag, `launchBackgroundIsolateEvenIfAppIsOpen` flag, last known `SignalingStatus`. |
| `StorageDelegate.IncomingCallService`   | Callback handle for `_isolatePluginCallbackDispatcher`, notification handle, `launchBackgroundIsolateEvenIfAppIsOpen` flag.                                                                                 |
| `StorageDelegate.IncomingCallSmsConfig` | SMS trigger prefix, regex pattern. Used by `IncomingCallSmsTriggerReceiver`.                                                                                                                                |

---

## `ContextHolder`

Thread-safe singleton providing the application `Context`. Each OS process initializes its own
instance:

- Main process: initialized in `WebtritCallkeepPlugin.onAttachedToEngine`
- `:callkeep_core` process: initialized in `PhoneConnectionService.onCreate`

Also provides `appUniqueKey` — a per-app identifier used to namespace broadcast action strings,
preventing collisions when multiple apps use the same plugin.

---

## `ActivityHolder`

Tracks the current foreground `Activity`. Used to:

- Launch the app from the background (e.g., when the user taps Answer on a lock-screen
  notification).
- Finish the Activity when a call ends while on the lock screen (`ActivityHolder.finish()`).

---

## `AssetHolder`

Provides access to Flutter asset paths from native code. Used to resolve custom ringtone and
ringback tone file paths registered via `StorageDelegate.Sound`. Each process that needs asset
resolution initializes its own instance.

---

## `RetryManager<K>`

Generic exponential-backoff retry utility with per-key state. Used by `ForegroundService` for
outgoing calls.

Configuration for outgoing calls (`OUTGOING_RETRY_CONFIG`):

| Parameter       | Value                                                                                                  |
|-----------------|--------------------------------------------------------------------------------------------------------|
| Max attempts    | 5                                                                                                      |
| Initial delay   | 750 ms                                                                                                 |
| Backoff factor  | ×1.5                                                                                                   |
| Max delay       | 5000 ms                                                                                                |
| Retry condition | `CallPhoneSecurityRetryDecider` — retries only on `SecurityException` with `CALL_PHONE` in the message |

Usage pattern:

```kotlin
retryManager.run(callId, config) { attempt, onSuccess, onError ->
    // attempt the operation
}
retryManager.cancel(callId)  // cancel on success or permanent failure
retryManager.clear()         // cancel all on service destroy
```

---

## `FlutterEngineHelper`

Creates and manages a background `FlutterEngine` for isolate services (`SignalingIsolateService`,
`IncomingCallService`).

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

The `callbackHandle` is the raw handle of `_isolatePluginCallbackDispatcher` (from
`lib/src/webtrit_callkeep_android.dart`), stored in `StorageDelegate` by the bootstrap APIs before
the service is started.

---

## `TelephonyUtils`

Wraps `TelecomManager` and `TelephonyManager` calls:

| Method                                    | Purpose                                                                                 |
|-------------------------------------------|-----------------------------------------------------------------------------------------|
| `registerPhoneAccount(context, handle)`   | Registers the self-managed `PhoneAccount` with Telecom. Called on startup and on retry. |
| `addNewIncomingCall(context, metadata)`   | Calls `TelecomManager.addNewIncomingCall()` to report an incoming call.                 |
| `placeOutgoingCall(context, uri, extras)` | Calls `TelecomManager.placeCall()` for an outgoing call.                                |
| `isEmergencyNumber(number)`               | Checks if a number is an emergency number before placing a call.                        |

---

## `NotificationChannelManager`

Registers all notification channels on the first `setUp()` call. Channels are only created once;
subsequent calls are no-ops. See [notifications.md](notifications.md) for the full channel list.
