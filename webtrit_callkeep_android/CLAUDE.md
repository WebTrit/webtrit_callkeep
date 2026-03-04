# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Overview

`webtrit_callkeep_android` is the Android implementation of the `webtrit_callkeep` Flutter plugin.
It provides VoIP call management via the Android Telecom framework (self-managed
`ConnectionService`), bridging Flutter/Dart and native Kotlin
through [Pigeon](https://pub.dev/packages/pigeon)-generated code.

## Commands

### Flutter (Dart side)

```bash
# Run tests
flutter test

# Run a single test file
flutter test test/src/common/test_callkeep.pigeon.dart

# Analyze
flutter analyze

# Regenerate Pigeon bindings (after modifying pigeons/callkeep.messages.dart)
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```

### Kotlin (Android side)

```bash
# From the android/ subdirectory
cd android
./gradlew assembleDebug
./gradlew test
```

### Code style

- Line width: 120 characters (configured in `analysis_options.yaml`)
- Linter: `very_good_analysis` with minor overrides (`lines_longer_than_80_chars: false`,
  `one_member_abstracts: false`)
- Generated files (`*.g.dart`, `*.pigeon.dart`) are excluded from analysis

## Architecture

> **Detailed docs** are in `docs/`. Start with `docs/README.md` for navigation. Key references:
> - `docs/architecture.md` — component map and design decisions
> - `docs/call-triggers.md` — the 4 ways a call can be initiated
> - `docs/call-flows.md` — end-to-end call flow diagrams
> - `docs/ipc.md` — IPC events and `ConnectionPerform` table
> - `docs/foreground-service.md` — ForegroundService lifecycle and Pigeon wiring
> - `docs/phone-connection-service.md` — PhoneConnectionService state machine
> - `docs/signaling-isolate-service.md` — SignalingIsolateService lifecycle
> - `docs/incoming-call-service.md` — IncomingCallService push notification path
> - `docs/notifications.md` — notification channels, builders, permissions
> - `docs/pigeon-api.md` — full Pigeon API reference

### Flutter ↔ Native IPC: Pigeon

All communication between Dart and Kotlin uses Pigeon-generated code:

- **Source of truth**: `pigeons/callkeep.messages.dart` — defines all message classes and API
  interfaces
- **Generated Dart**: `lib/src/common/callkeep.pigeon.dart`
- **Generated Kotlin**: `android/src/main/kotlin/com/webtrit/callkeep/Generated.kt`
- **Converter extensions**: `lib/src/common/converters.dart` — maps between Pigeon (`P*`) types and
  platform-interface (`Callkeep*`) types

**API direction conventions:**

- `@HostApi` — Dart calls Kotlin (e.g., `PHostApi`, `PHostPermissionsApi`)
- `@FlutterApi` — Kotlin calls Dart (e.g., `PDelegateFlutterApi`,
  `PDelegateBackgroundRegisterFlutterApi`)

### Dart entry point

`lib/src/webtrit_callkeep_android.dart` — `WebtritCallkeepAndroid` implements
`WebtritCallkeepPlatform` (from `webtrit_callkeep_platform_interface`). It holds Pigeon API
instances and wires delegate relays (inner classes like `_CallkeepDelegateRelay`) that adapt
platform-interface callbacks to Pigeon callbacks.

The `_isolatePluginCallbackDispatcher()` function (marked `@pragma('vm:entry-point')`) is the Dart
entry point for background Flutter isolates started by the native side.

### Android process model

The plugin runs across **two OS processes**:

| Process                  | Key components                                                                                 |
|--------------------------|------------------------------------------------------------------------------------------------|
| Main app process         | `WebtritCallkeepPlugin`, `ForegroundService`, `SignalingIsolateService`, `IncomingCallService` |
| `:callkeep_core` process | `PhoneConnectionService` (Android `ConnectionService`)                                         |

**Inter-process communication** between these processes uses local broadcasts (
`ConnectionServicePerformBroadcaster`) with `ConnectionPerform` events as actions. Direction is
always `:callkeep_core` → main process. See `docs/ipc.md` for the full event table.

**Self-managed `PhoneAccount`:** The plugin registers with `CAPABILITY_SELF_MANAGED`. The app does
not need `CALL_PHONE` permission for its own VoIP calls; the system shows its own call UI (lock
screen, status bar). `PhoneConnectionService` runs in `:callkeep_core` so Telecom state persists
even when the main process is backgrounded.

### Native Android service architecture

**`WebtritCallkeepPlugin`** (`FlutterPlugin` + `ActivityAware` + `ServiceAware`) — plugin lifecycle
entry point; wires all Pigeon `@HostApi` implementations to the `BinaryMessenger`; binds to
`ForegroundService` and sets up communication bridges with `SignalingIsolateService` /
`IncomingCallService`.

**`ForegroundService`** (bound service, implements `PHostApi`) — the main bridge between Telecom
events and Flutter. Bound to the activity lifecycle. Receives broadcast events from
`PhoneConnectionService` (cross-process) and translates them into `PDelegateFlutterApi` calls back
to Dart. Manages outgoing call retry logic (`RetryManager`) and timeout handling (
`OutgoingCallbacksManager`).

**`PhoneConnectionService`** (Android `ConnectionService`, `:callkeep_core` process) — handles
system-level call connection lifecycle (`onCreateIncomingConnection`, `onCreateOutgoingConnection`).
Dispatches events back to the main process via `ConnectionServicePerformBroadcaster`. Managed by
`PhoneConnectionServiceDispatcher`.

**`SignalingIsolateService`** (foreground service, `FOREGROUND_SERVICE_TYPE_PHONE_CALL`) —
long-lived service for persistent signaling. Launches a background Flutter isolate via
`FlutterEngineHelper` to maintain a WebSocket/signaling connection. Implements
`PHostBackgroundSignalingIsolateApi` to receive `incomingCall`/`endCall` calls from Dart.

**`IncomingCallService`** (foreground service) — handles incoming calls triggered by push
notifications. Launches a short-lived Flutter isolate, shows the incoming call notification, and
coordinates with `PhoneConnectionService` to register the call with the Telecom framework.

**`ActiveCallService`** — foreground service (`phoneCall|microphone|camera`) for managing an active
in-progress call with a persistent notification.

### `PhoneConnection` state machine

```
createIncomingPhoneConnection()  →  STATE_RINGING
createOutgoingPhoneConnection()  →  STATE_DIALING

STATE_RINGING
    ├── onAnswer()     → STATE_ACTIVE  + broadcast AnswerCall
    └── onDisconnect() → STATE_DISCONNECTED + broadcast DeclineCall

STATE_DIALING
    ├── establish()    → STATE_ACTIVE  + broadcast OngoingCall
    └── onDisconnect() → STATE_DISCONNECTED + broadcast HungUp

STATE_ACTIVE
    ├── onHold()       → STATE_HOLDING + broadcast ConnectionHolding
    ├── onDisconnect() → STATE_DISCONNECTED + broadcast HungUp
    ├── onMuteStateChanged()                → broadcast AudioMuting
    ├── onCallEndpointChanged() (API 34+)   → broadcast AudioDeviceSet
    └── onAvailableCallEndpointsChanged()   → broadcast AudioDevicesUpdate
```

### In-process broadcast channels (main process only)

| Channel                        | Direction                                           | Purpose                                                                                                    |
|--------------------------------|-----------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `ActivityLifecycleBroadcaster` | `WebtritCallkeepPlugin` → `SignalingIsolateService` | Delivers `Lifecycle.Event` (ON_START, ON_RESUME, …) so the isolate can sync with activity state            |
| `SignalingStatusBroadcaster`   | Flutter isolate → `SignalingIsolateService`         | Delivers `SignalingStatus` (CONNECTING, CONNECT, …) so the service can pass it to the next isolate wake-up |

### Common utilities

| Class                        | Responsibility                                                                                                                                                                                            |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ContextHolder`              | Thread-safe singleton app `Context`. Each process initializes its own instance.                                                                                                                           |
| `ActivityHolder`             | Tracks the current `Activity`; used to launch or finish the app from background.                                                                                                                          |
| `AssetHolder`                | Provides access to Flutter asset paths from native code.                                                                                                                                                  |
| `StorageDelegate`            | `SharedPreferences` wrapper. Persists callback handles, sound paths, service config across process restarts. Nested objects: `Sound`, `SignalingService`, `IncomingCallService`, `IncomingCallSmsConfig`. |
| `TelephonyUtils`             | Wraps `TelecomManager` / `TelephonyManager` (place call, add incoming call, register phone account, emergency number check).                                                                              |
| `FlutterEngineHelper`        | Creates and manages a background `FlutterEngine` for isolate services.                                                                                                                                    |
| `RetryManager<K>`            | Generic exponential-backoff retry with per-key state and configurable `RetryDecider`. Used by `ForegroundService` for outgoing calls (up to 5 attempts, 750 ms → 3.8 s).                                  |
| `NotificationChannelManager` | Registers all notification channels once on first `setUp()` call.                                                                                                                                         |

### Configuration persistence

`StorageDelegate` (`common/StorageDelegate.kt`) — `SharedPreferences`-backed singleton used to
persist configuration across process restarts:

- `StorageDelegate.Sound` — ringtone/ringback paths
- `StorageDelegate.SignalingService` — callback handle references, notification text, enabled flag
- `StorageDelegate.IncomingCallService` — push notification callback handles
- `StorageDelegate.IncomingCallSmsConfig` — SMS fallback prefix and regex

### Boot / restart resilience

- `ForegroundCallBootReceiver` handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, etc. to restart
  `SignalingIsolateService`
- `SignalingServiceBootWorker` (WorkManager) re-enqueues the service on destruction if it was
  enabled
- On Android 14+ (API 34), the service cannot start from boot broadcast with
  `FOREGROUND_SERVICE_TYPE_PHONE_CALL`; instead it is started from
  `WebtritCallkeepPlugin.onAttachedToActivity()`

### Key data types

- `CallMetadata` — primary data carrier passed through intents/bundles between processes; contains
  `callId`, `handle`, `displayName`, `hasVideo`, `ringtonePath`, audio state, etc. Serialized
  to/from `Bundle` (not `Parcelable`) to avoid cross-process class-loading issues.
- `ConnectionPerform` (enum) — event types for the cross-process broadcast bus
- `PhoneConnection` — wraps a Telecom `Connection` object with call-state logic
- `ConnectionManager` — tracks active `PhoneConnection` instances in the `:callkeep_core` process
- `MainProcessConnectionTracker` — companion object on `ForegroundService`; mirrors active
  connections in the main process via `ConnectionAdded`/`ConnectionRemoved` IPC events
- `CallHandle` — wraps a phone number or URI with a type (`generic`, `sip`, `email`)
- `FailureMetadata` — carries failure context (type + message) for outgoing call failure reporting

### Optional SMS fallback

`IncomingCallSmsTriggerReceiver` + `SmsReceptionConfigBootstrapApi` support triggering incoming
calls via SMS when push notifications fail. Disabled by default — requires adding `RECEIVE_SMS`
permission and the receiver to the host app's manifest.

## Git conventions

- **Never mention Claude, AI, or any AI assistant** in commit messages or pull request
  titles/bodies.
- Write commits in the imperative mood, focused on what and why, not the tool used.
