# ForegroundService

**Package:** `com.webtrit.callkeep.services.services.foreground`
**Process:** main
**Type:** Bound service (no `startForegroundService` — only `bindService`)
**Implements:** `PHostApi` (Pigeon `@HostApi`)

---

## Role

The central bridge between the Android Telecom layer and the Flutter main isolate. It is alive as
long as the Flutter Activity is attached to the plugin. It:

1. Receives all `@HostApi` calls from Flutter (start/answer/end call, mute, hold, DTMF, audio
   device, etc.).
2. Translates them into `startService` intents sent to `PhoneConnectionService` (cross-process).
3. Receives `ConnectionServicePerformBroadcaster` events from `PhoneConnectionService` and calls
   back into Flutter via `PDelegateFlutterApi`.

---

## Lifecycle

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

---

## Pigeon wiring

`ForegroundService` implements `PHostApi` directly. It is registered on the `BinaryMessenger` inside
`onServiceConnected` (after binding), not in `onAttachedToEngine`. This means Pigeon calls that
require `ForegroundService` will fail if the Activity is not yet attached.

The `PDelegateFlutterApi` instance is stored on the service and used to push events back to Flutter.
It is created from the same `BinaryMessenger` passed at bind time.

---

## Receiving IPC events

`connectionServicePerformReceiver` is a `BroadcastReceiver` registered in `onCreate` for all
`ConnectionPerform` actions. Each event maps to a `PDelegateFlutterApi` call:

| `ConnectionPerform`      | Flutter callback                                                      |
|--------------------------|-----------------------------------------------------------------------|
| `AnswerCall`             | `performAnswerCall(callId)` + `didActivateAudioSession()`             |
| `DeclineCall` / `HungUp` | `performEndCall(callId)` + `didDeactivateAudioSession()`              |
| `OngoingCall`            | `performStartCall(callId, handle, name, video)`                       |
| `DidPushIncomingCall`    | `didPushIncomingCall(handle, displayName, video, callId, error=null)` |
| `AudioDeviceSet`         | `performAudioDeviceSet(callId, device)`                               |
| `AudioDevicesUpdate`     | `performAudioDevicesUpdate(callId, devices)`                          |
| `AudioMuting`            | `performSetMuted(callId, muted)`                                      |
| `ConnectionHolding`      | `performSetHeld(callId, onHold)`                                      |
| `SentDTMF`               | `performSendDTMF(callId, key)`                                        |
| `OutgoingFailure`        | resolves outgoing callback with `PCallRequestError`                   |
| `ConnectionAdded`        | `connectionTracker.add(callId, metadata)`                             |
| `ConnectionRemoved`      | `connectionTracker.remove(callId)`                                    |

See [ipc.md](ipc.md) for the full IPC event table.

---

## Outgoing call retry

Starting an outgoing call may fail with `SecurityException(CALL_PHONE)` if the self-managed
`PhoneAccount` is not yet registered in Telecom (e.g., right after a cold start). The service uses
`RetryManager` with `CallPhoneSecurityRetryDecider`:

| Attempt | Delay before attempt |
|---------|----------------------|
| 1       | immediate            |
| 2       | 750 ms               |
| 3       | 1125 ms              |
| 4       | 1687 ms              |
| 5       | 2531 ms              |

Maximum 5 attempts; backoff factor ×1.5; cap 5000 ms. Each retry also re-registers the
`PhoneAccount` via `TelephonyUtils.registerPhoneAccount()`.

A global 5-second timeout (`OutgoingCallbacksManager`) ensures the Flutter callback is always
resolved even if the Telecom system never replies.

---

## `onDelegateSet` — state restoration on hot restart

When Flutter reconnects after a hot restart, it calls `onDelegateSet()`. The service iterates all
tracked connections and forces an audio state update:

```kotlin
tracked.forEach { metadata ->
    PhoneConnectionService.forceUpdateAudioState(baseContext, metadata)
}
```

This re-triggers `AudioDeviceSet` / `AudioDevicesUpdate` broadcasts so Flutter's UI is back in sync
with the current call state.
