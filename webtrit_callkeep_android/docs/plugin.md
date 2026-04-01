# WebtritCallkeepPlugin

**File**: `kotlin/com/webtrit/callkeep/WebtritCallkeepPlugin.kt`

**Implements**: `FlutterPlugin`, `ActivityAware`, `ServiceAware`, `LifecycleEventObserver`

## Responsibility

`WebtritCallkeepPlugin` is the Flutter plugin entry point. It is instantiated by the Flutter
framework when the plugin is registered and serves as the wiring point between the Dart layer and
all Android-side components.

## Lifecycle

### `onAttachedToEngine(binding)`

Called once when the Flutter engine attaches:

- Stores `applicationContext` in `ContextHolder`.
- Initializes `AssetCacheManager` (copies ringtones from the Flutter asset bundle to device
  storage so services can access them without a Flutter engine).
- Registers bootstrap APIs for background isolates:
  - `BackgroundPushNotificationIsolateBootstrapApi` (push-triggered incoming call service)
  - `SmsReceptionConfigBootstrapApi` (optional SMS fallback)
- Registers `PHostDiagnosticsApi`, `PHostPermissionsApi`, `PHostActivityControlApi`,
  `PHostConnectionsApi`, `PHostSoundApi`.

### `onAttachedToActivity(binding)` / `onReattachedToActivityForConfigChanges(binding)`

Called when an `Activity` is available:

- Saves `ActivityHolder.activity`.
- Adds a `LifecycleObserver` to receive `ON_START` / `ON_STOP` events.
- Binds `ForegroundService` (see below).

### `onDetachedFromActivity()` / `onDetachedFromActivityForConfigChanges()`

- Removes lifecycle observer.
- Unbinds (and optionally stops) `ForegroundService`.

### `onStateChanged(owner, event)`

Responds to `Lifecycle.Event.ON_START`:

- Reads current active-call state from `MainProcessConnectionTracker`.
- Sets or clears `setShowWhenLockedCompat` / `setTurnScreenOnCompat` on the activity window so
  the app appears over the lock screen only when a call is active.

## ForegroundService Binding

```text
bindForegroundService()
    └── context.bindService(ForegroundService, serviceConnection, BIND_AUTO_CREATE)

unbindAndStopForegroundService()
    └── context.unbindService(serviceConnection)
    └── ForegroundService.stopSelf() (if no longer needed)
```

`serviceConnection.onServiceConnected()` stores the `ForegroundService` binder and registers the
remaining Pigeon host API — `PHostApi` — which is implemented by `ForegroundService` itself.

## Lock-Screen Flags

The plugin toggles two `Window` flags depending on whether any non-terminated call exists in
`MainProcessConnectionTracker`:

| Flag                            | Condition             |
|---------------------------------|-----------------------|
| `setShowWhenLockedCompat(true)` | An active call exists |
| `setTurnScreenOnCompat(true)`   | An active call exists |

This ensures the incoming-call or in-call UI surfaces even when the device is locked.

## Related Components

- [foreground-service.md](foreground-service.md) — bound service wired up here
- [background-services.md](background-services.md) — bootstrap APIs wired to services
- [pigeon-apis.md](pigeon-apis.md) — all Pigeon APIs registered here
