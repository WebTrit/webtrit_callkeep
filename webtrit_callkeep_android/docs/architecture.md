# Architecture

## Plugin responsibilities

`webtrit_callkeep_android` provides the Android implementation of the `webtrit_callkeep` Flutter
plugin. It:

- Registers a self-managed `PhoneAccount` with the Android Telecom framework so the system shows its
  own call UI (lock screen, status bar).
- Reports incoming and outgoing calls to Telecom, bridging all call events to the Flutter delegate
  via Pigeon.
- Hosts a persistent background Flutter isolate for signaling and a transient isolate for
  push-notification call handling.

---

## Layer diagram

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

## Process model

The plugin spans two OS processes:

| Process          | Components                                                                                                          |
|------------------|---------------------------------------------------------------------------------------------------------------------|
| Main app process | `WebtritCallkeepPlugin`, `ForegroundService`, `SignalingIsolateService`, `IncomingCallService`, `ActiveCallService` |
| `:callkeep_core` | `PhoneConnectionService`, `PhoneConnection` (per call), `ConnectionManager`, `PhoneConnectionServiceDispatcher`     |

Each process initializes its own `ContextHolder` singleton:

- Main process: initialized in `WebtritCallkeepPlugin.onAttachedToEngine`
- `:callkeep_core` process: initialized in `PhoneConnectionService.onCreate`

See [ipc.md](ipc.md) for how they communicate.

---

## Component map

### Plugin entry point

| Class                   | Role                                                                                                                                                                                                |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WebtritCallkeepPlugin` | `FlutterPlugin` + `ActivityAware` + `ServiceAware`. Registers all Pigeon `@HostApi` implementations on attach; tears them down on detach. Binds to `ForegroundService` when an Activity is present. |

### Pigeon API implementations (`@HostApi`)

| Class                                               | Pigeon interface                                     | Registered in                        |
|-----------------------------------------------------|------------------------------------------------------|--------------------------------------|
| `ForegroundService`                                 | `PHostApi`                                           | `onServiceConnected` (after binding) |
| `BackgroundSignalingIsolateBootstrapApi`            | `PHostBackgroundSignalingIsolateBootstrapApi`        | `onAttachedToEngine`                 |
| `BackgroundPushNotificationIsolateBootstrapApi`     | `PHostBackgroundPushNotificationIsolateBootstrapApi` | `onAttachedToEngine`                 |
| `SmsReceptionConfigBootstrapApi`                    | `PHostSmsReceptionConfigApi`                         | `onAttachedToEngine`                 |
| `PermissionsApi`                                    | `PHostPermissionsApi`                                | `onAttachedToEngine`                 |
| `DiagnosticsApi`                                    | `PHostDiagnosticsApi`                                | `onAttachedToEngine`                 |
| `SoundApi`                                          | `PHostSoundApi`                                      | `onAttachedToEngine`                 |
| `ConnectionsApi`                                    | `PHostConnectionsApi`                                | `onAttachedToEngine`                 |
| `ActivityControlApi`                                | `PHostActivityControlApi`                            | `onAttachedToActivity`               |
| `SignalingIsolateService`                           | `PHostBackgroundSignalingIsolateApi`                 | `onAttachedToService`                |
| `CallLifecycleHandler` (from `IncomingCallService`) | `PHostBackgroundPushNotificationIsolateApi`          | `onAttachedToService`                |

### Android services

| Service                   | Process          | Type                                         | Purpose                                                                     |
|---------------------------|------------------|----------------------------------------------|-----------------------------------------------------------------------------|
| `ForegroundService`       | main             | Bound                                        | Central bridge: Telecom events → Flutter delegate. Implements `PHostApi`.   |
| `PhoneConnectionService`  | `:callkeep_core` | Started (Telecom-driven)                     | Android `ConnectionService`. Owns `PhoneConnection` objects.                |
| `SignalingIsolateService` | main             | Foreground (`phoneCall`)                     | Long-running service + Flutter background isolate for persistent signaling. |
| `IncomingCallService`     | main             | Foreground (`phoneCall`)                     | Transient service for push-notification incoming call handling.             |
| `ActiveCallService`       | main             | Foreground (`phoneCall\|microphone\|camera`) | Shows active-call notification during a live call.                          |

### Data model

| Class               | Role                                                                                                                                                                                             |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CallMetadata`      | Primary call data carrier. Serializes to/from `Bundle` (not `Parcelable`) to avoid cross-process class-loading issues. Contains `callId`, `handle`, `displayName`, `hasVideo`, audio state, etc. |
| `CallHandle`        | Wraps a phone number or URI with a type (`generic`, `sip`, `email`).                                                                                                                             |
| `AudioDevice`       | Represents an audio endpoint (`earpiece`, `speaker`, `bluetooth`, `wiredHeadset`, …).                                                                                                            |
| `SignalingStatus`   | Enum: `CONNECTING`, `CONNECT`, `DISCONNECTING`, `DISCONNECT`, `FAILURE`.                                                                                                                         |
| `FailureMetadata`   | Carries failure context (type + message) for outgoing call failure reporting.                                                                                                                    |
| `ConnectionPerform` | Enum of IPC event types for the cross-process broadcast bus.                                                                                                                                     |

---

## Key design decisions

### Self-managed PhoneAccount

The plugin registers a `PhoneAccount` with `CAPABILITY_SELF_MANAGED`. This means:

- The system shows its own call UI (lock screen, status bar) without any additional app-side UI
  work.
- The app does **not** need `CALL_PHONE` permission for its own VoIP calls.
- The `ConnectionService` runs in its own process (`:callkeep_core`) to remain alive even when the
  main process is reclaimed.

### Two-process architecture

`PhoneConnectionService` is declared with `android:process=":callkeep_core"` in the manifest. This
was introduced to solve a critical reliability bug (WT-795).

**Root cause:** When the main app process dies (native crash, force-stop, or aggressive OS kill),
the Android `TelecomManager` does not immediately clean up the Binder connection to the
`ConnectionService`. On restart, `TelecomManager.placeCall()` returns success, but the system never
calls `onCreateOutgoingConnection` — the call hangs in "Preparing..." until the Flutter-side timeout
fires. This was reproducible on Itel and Android One devices and required a device reboot to
recover.

**Why process isolation fixes it:** By running `PhoneConnectionService` in `:callkeep_core`, its
lifecycle is independent of the main app. If the main process crashes, the system rebinds to
`PhoneConnectionService` cleanly without encountering stale Binder state. The Telecom framework
manages its connection to `:callkeep_core` separately from anything the main process does.

**Secondary benefit:** The separation also ensures Telecom state (active connections, call audio)
survives the main process being backgrounded or reclaimed by the OS.

All communication from `:callkeep_core` back to the main process uses **local broadcasts** (
`ConnectionServicePerformBroadcaster`). See [ipc.md](ipc.md).

### Flutter isolate callback dispatch

Background services start their own `FlutterEngine` with a Dart entry point (
`_isolatePluginCallbackDispatcher`) that only sets up the Pigeon `@FlutterApi` receiver. User
callback handles are stored in `SharedPreferences` (`StorageDelegate`) and resolved at runtime via
`PluginUtilities.getCallbackFromHandle`.

### Android 14+ boot restriction

Since API 34, `FOREGROUND_SERVICE_TYPE_PHONE_CALL` cannot be started from `BOOT_COMPLETED`. The
workaround is in `WebtritCallkeepPlugin.onAttachedToActivity` — if the signaling service was enabled
and the build is ≥ `UPSIDE_DOWN_CAKE`, it is started explicitly there instead of from the boot
receiver.
