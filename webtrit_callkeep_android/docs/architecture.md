# Android Architecture Overview

WebtritCallkeep Android runs across **two OS processes** and consists of several cooperating
services, broadcast-based IPC, and a Pigeon bridge to the Flutter layer.

## Component Index

| Document                                                   | Component                                                                  |
|------------------------------------------------------------|----------------------------------------------------------------------------|
| [dual-process.md](dual-process.md)                         | Dual-process design, process boundaries, IPC contract                      |
| [plugin.md](plugin.md)                                     | `WebtritCallkeepPlugin` — Flutter plugin entry point                       |
| [callkeep-core.md](callkeep-core.md)                       | `CallkeepCore` / `InProcessCallkeepCore` — main-process state facade       |
| [foreground-service.md](foreground-service.md)             | `ForegroundService` — bound service, Pigeon host, Telecom bridge           |
| [connection-tracker.md](connection-tracker.md)             | `MainProcessConnectionTracker` — shadow call-state registry                |
| [phone-connection-service.md](phone-connection-service.md) | `PhoneConnectionService` — Telecom `ConnectionService` in `:callkeep_core` |
| [phone-connection.md](phone-connection.md)                 | `PhoneConnection` — single call object inside Telecom                      |
| [connection-manager.md](connection-manager.md)             | `ConnectionManager` — call registry inside `:callkeep_core`                |
| [ipc-broadcasting.md](ipc-broadcasting.md)                 | `ConnectionServicePerformBroadcaster` — cross-process event bus            |
| [background-services.md](background-services.md)           | `SignalingIsolateService`, `IncomingCallService`, `ActiveCallService`      |
| [pigeon-apis.md](pigeon-apis.md)                           | All Pigeon host and Flutter API definitions                                |
| [models.md](models.md)                                     | `CallMetadata`, `CallHandle`, `AudioDevice`, and other data models         |
| [notifications.md](notifications.md)                       | Notification builders and channel management                               |
| [call-flows.md](call-flows.md)                             | End-to-end flows: incoming call, outgoing call, teardown                   |

## High-Level Diagram

```text
┌──────────────────────────────────────────────────────────────────┐
│  main process                                                    │
│                                                                  │
│  Flutter Engine                                                  │
│    └── WebtritCallkeepPlugin                                     │
│          ├── Pigeon host APIs (ForegroundService)                │
│          └── Bootstrap APIs (Signaling / PushNotification)       │
│                                                                  │
│  ForegroundService  (bound, PHostApi)                            │
│    ├── MainProcessConnectionTracker  (shadow call state)         │
│    └── CallkeepCore  (facade → :callkeep_core)                   │
│                                                                  │
│  SignalingIsolateService  (persistent foreground service)        │
│  IncomingCallService      (one-shot foreground service)          │
│  ActiveCallService        (active call foreground service)       │
│                                                                  │
│  ◄── broadcasts ──────────────────────── broadcasts ──►          │
│  ◄── startService intents ──────────────────────────────►        │
└──────────────────────────────────────────────────────────────────┘
          │ broadcasts / startService            ▲ broadcasts
          ▼                                      │
┌──────────────────────────────────────────────────────────────────┐
│  :callkeep_core process                                          │
│                                                                  │
│  PhoneConnectionService  (Android ConnectionService)            │
│    ├── ConnectionManager  (call registry)                        │
│    ├── PhoneConnection (×N)  (individual Telecom call)           │
│    └── PhoneConnectionServiceDispatcher                          │
└──────────────────────────────────────────────────────────────────┘
          │                                      ▲
          ▼                                      │
┌──────────────────────────────────────────────────────────────────┐
│  Android Telecom / System                                        │
└──────────────────────────────────────────────────────────────────┘
```

## Key Design Constraints

- **Never call `PhoneConnectionService.connectionManager.*` from the main process.** The
  `ConnectionManager` is initialized only in the `:callkeep_core` JVM. Use `CallkeepCore.instance`
  instead.
- **`CallMetadata` must NOT implement `Parcelable`.** The system process (`system_server`) attempts
  to deserialize Parcelables, which causes `ClassNotFoundException`. Use Bundle serialization
  (`toBundle()` / `fromBundle()`) instead.
- **Pigeon files are auto-generated.** Do not edit `Generated.kt` manually. Regenerate with:
  `flutter pub run pigeon --input pigeons/callkeep.messages.dart`
- **Background isolate entry points** must be annotated with `@pragma('vm:entry-point')` on the
  Dart side.
- **Cross-process broadcasts must be app-scoped** (`.setPackage(packageName)`) to prevent
  interception by third-party apps.
