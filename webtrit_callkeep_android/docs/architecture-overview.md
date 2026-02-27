# Architecture Overview

## Responsibility

`webtrit_callkeep_android` exposes the Android [Telecom framework](https://developer.android.com/guide/topics/connectivity/telecom) to Flutter as a self-managed `ConnectionService`. It:

- Registers a self-managed `PhoneAccount` with the system.
- Reports incoming/outgoing calls to the Telecom framework so the system call UI is shown.
- Runs a persistent background signaling service with its own Flutter isolate.
- Handles incoming calls from push notifications via a transient service.

---

## Layers

```
┌─────────────────────────────────────────────────────────────┐
│                        Flutter / Dart                        │
│  WebtritCallkeepAndroid  ←→  Pigeon-generated code          │
└─────────────────────┬───────────────────────────────────────┘
                      │ BinaryMessenger (method channel)
┌─────────────────────▼───────────────────────────────────────┐
│               WebtritCallkeepPlugin  (Kotlin)                │
│  FlutterPlugin + ActivityAware + ServiceAware                │
│                                                              │
│  Registers all @HostApi impls on the messenger               │
│  Binds to ForegroundService                                  │
│  Bridges isolate services via onAttachedToService            │
└──┬──────────────────┬────────────────────┬───────────────────┘
   │                  │                    │
   ▼                  ▼                    ▼
ForegroundService  SignalingIsolateService  IncomingCallService
(bound, main proc) (foreground, main proc) (foreground, main proc)
   │
   │  local broadcast (ConnectionServicePerformBroadcaster)
   │  cross-process via sendBroadcast
   ▼
PhoneConnectionService          ← :callkeep_core process
(Android ConnectionService)
   │
   ▼
Android Telecom Framework
```

---

## Component Map

### Plugin entry point

| Class | Role |
|---|---|
| `WebtritCallkeepPlugin` | Implements `FlutterPlugin`, `ActivityAware`, `ServiceAware`. Sets up all Pigeon API implementations on `attach`, tears them down on `detach`. Binds to `ForegroundService` when Activity is present. |

### Pigeon API implementations (`@HostApi`)

These classes are instantiated by `WebtritCallkeepPlugin` and registered on the `BinaryMessenger`.

| Class | Pigeon Interface | Registered in |
|---|---|---|
| `ForegroundService` | `PHostApi` | `onServiceConnected` (after binding) |
| `BackgroundSignalingIsolateBootstrapApi` | `PHostBackgroundSignalingIsolateBootstrapApi` | `onAttachedToEngine` |
| `BackgroundPushNotificationIsolateBootstrapApi` | `PHostBackgroundPushNotificationIsolateBootstrapApi` | `onAttachedToEngine` |
| `SmsReceptionConfigBootstrapApi` | `PHostSmsReceptionConfigApi` | `onAttachedToEngine` |
| `PermissionsApi` | `PHostPermissionsApi` | `onAttachedToEngine` |
| `DiagnosticsApi` | `PHostDiagnosticsApi` | `onAttachedToEngine` |
| `SoundApi` | `PHostSoundApi` | `onAttachedToEngine` |
| `ConnectionsApi` | `PHostConnectionsApi` | `onAttachedToEngine` |
| `ActivityControlApi` | `PHostActivityControlApi` | `onAttachedToActivity` |
| `SignalingIsolateService` | `PHostBackgroundSignalingIsolateApi` | `onAttachedToService` |
| `CallLifecycleHandler` (from `IncomingCallService`) | `PHostBackgroundPushNotificationIsolateApi` | `onAttachedToService` |

### Android Services

| Service | Process | Type | Purpose |
|---|---|---|---|
| `ForegroundService` | main | Bound | Bridges Telecom events to Flutter delegate. Implements `PHostApi`. |
| `PhoneConnectionService` | `:callkeep_core` | Started (Telecom-driven) | Android `ConnectionService`. Owns `PhoneConnection` objects. |
| `SignalingIsolateService` | main | Foreground (`phoneCall`) | Long-running service + Flutter background isolate for signaling. |
| `IncomingCallService` | main | Foreground (`phoneCall`) | Transient service for push-notification incoming call handling. |
| `ActiveCallService` | main | Foreground (`phoneCall\|microphone\|camera`) | Shows active-call notification during a live call. |

### Common utilities

| Class | Responsibility |
|---|---|
| `ContextHolder` | Thread-safe singleton app `Context`. Shared across processes (each process initializes its own instance). |
| `ActivityHolder` | Tracks the current `Activity`. Used to launch app or finish from background. |
| `AssetHolder` | Provides access to Flutter asset paths from native code. |
| `StorageDelegate` | `SharedPreferences` wrapper. Persists callback handles, sound paths, service config across process restarts. |
| `TelephonyUtils` | Wraps `TelecomManager` and `TelephonyManager` calls (place call, add incoming call, register phone account, emergency number check). |
| `FlutterEngineHelper` | Creates and manages a background `FlutterEngine` for isolate services. |
| `RetryManager<K>` | Generic exponential-backoff retry with per-key state tracking and configurable `RetryDecider`. |
| `NotificationChannelManager` | Registers all notification channels once on first `setUp()` call. |

### Data model

| Class | Role |
|---|---|
| `CallMetadata` | Primary call data carrier. Serializes to/from `Bundle` (not `Parcelable`) to avoid cross-process class-loading issues. |
| `CallHandle` | Wraps a phone number or URI with a type (`generic`, `sip`, `email`). |
| `AudioDevice` | Represents an audio endpoint (`earpiece`, `speaker`, `bluetooth`, `wiredHeadset`, …). |
| `SignalingStatus` | Enum: `CONNECTING`, `CONNECT`, `DISCONNECTING`, `DISCONNECT`, `FAILURE`. |
| `FailureMetadata` | Carries failure context (type + message) for outgoing call failure reporting. |

---

## Key Design Decisions

### Self-managed PhoneAccount

The plugin registers a `PhoneAccount` with `CAPABILITY_SELF_MANAGED`. This means:
- The system shows its own call UI (lock screen, status bar).
- The app does **not** need `CALL_PHONE` permission for its own VoIP calls.
- The `ConnectionService` runs in its own process (`:callkeep_core`) to remain alive even when the main process is backgrounded.

### Two-process architecture

`PhoneConnectionService` runs in `:callkeep_core` (see `AndroidManifest.xml`). All communication back to the main process happens via **local broadcasts** (`ConnectionServicePerformBroadcaster`). This prevents the OS from killing the system-registered service when the app process is reclaimed.

See [processes-and-ipc.md](processes-and-ipc.md) for details.

### Flutter isolate callback dispatch

Background services start their own `FlutterEngine` with a Dart entry point (`_isolatePluginCallbackDispatcher`) that only sets up the Pigeon `@FlutterApi` receiver. User callback handles are stored in `SharedPreferences` and resolved at runtime via `PluginUtilities.getCallbackFromHandle`.

### Android 14+ boot restriction

Since API 34, `FOREGROUND_SERVICE_TYPE_PHONE_CALL` cannot be started from `BOOT_COMPLETED`. The workaround is in `WebtritCallkeepPlugin.onAttachedToActivity` — if the signaling service was enabled and the build is ≥ `UPSIDE_DOWN_CAKE`, it is started explicitly there.
