# webtrit_callkeep_android

The Android implementation of [`webtrit_callkeep`](https://pub.dev/packages/webtrit_callkeep).

Provides VoIP call management via the
Android [Telecom framework](https://developer.android.com/guide/topics/connectivity/telecom) (
self-managed `ConnectionService`), bridging Flutter/Dart and native Kotlin
through [Pigeon](https://pub.dev/packages/pigeon)-generated code.

---

## Architecture

The plugin spans two OS processes and several Android services:

```
Flutter (Dart)
    │  Pigeon (BinaryMessenger)
    ▼
WebtritCallkeepPlugin          ← FlutterPlugin entry point (main process)
    │
    ├── ForegroundService      ← Bound service; bridges Telecom events ↔ Flutter delegate
    │       │ cross-process local broadcast
    │       ▼
    │   PhoneConnectionService ← Android ConnectionService (:callkeep_core process)
    │       └── Android Telecom Framework
    │
    ├── SignalingIsolateService ← Long-lived foreground service + Flutter background isolate
    ├── IncomingCallService    ← Transient service for push-notification incoming calls
    └── ActiveCallService      ← Foreground service during an active call
```

See [`docs/README.md`](docs/README.md) for the full documentation index.

---

## Documentation

| Doc                                                                    | Description                                                     |
|------------------------------------------------------------------------|-----------------------------------------------------------------|
| [docs/architecture.md](docs/architecture.md)                           | Plugin layers, process model, component map, design decisions   |
| [docs/call-triggers.md](docs/call-triggers.md)                         | The 4 ways a call can be initiated                              |
| [docs/call-flows.md](docs/call-flows.md)                               | Step-by-step call flow diagrams                                 |
| [docs/ipc.md](docs/ipc.md)                                             | Cross-process IPC channel and event table                       |
| [docs/foreground-service.md](docs/foreground-service.md)               | `ForegroundService` lifecycle and Pigeon wiring                 |
| [docs/phone-connection-service.md](docs/phone-connection-service.md)   | `PhoneConnectionService` state machine                          |
| [docs/signaling-isolate-service.md](docs/signaling-isolate-service.md) | `SignalingIsolateService` boot resilience and isolate lifecycle |
| [docs/incoming-call-service.md](docs/incoming-call-service.md)         | `IncomingCallService` push notification path                    |
| [docs/active-call-service.md](docs/active-call-service.md)             | `ActiveCallService` role and lifecycle                          |
| [docs/common-utilities.md](docs/common-utilities.md)                   | `StorageDelegate`, `RetryManager`, `FlutterEngineHelper`, etc.  |
| [docs/notifications.md](docs/notifications.md)                         | Notification channels, builders, and actions                    |
| [docs/pigeon-api.md](docs/pigeon-api.md)                               | Full Pigeon API reference                                       |

---

## Development

### Regenerate Pigeon bindings

After editing `pigeons/callkeep.messages.dart`:

```bash
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```

This regenerates:

- `lib/src/common/callkeep.pigeon.dart` (Dart side)
- `android/src/main/kotlin/com/webtrit/callkeep/Generated.kt` (Kotlin side)

### Run tests

```bash
# Dart
flutter test

# Kotlin (from android/ subdirectory)
cd android && ./gradlew test
```

### Analyze

```bash
flutter analyze
```
