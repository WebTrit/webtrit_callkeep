# webtrit_callkeep_android — Architecture Documentation

This folder contains architecture documentation for the Android implementation of the `webtrit_callkeep` Flutter plugin.

## Documents

| File | Description |
|------|-------------|
| [architecture-overview.md](architecture-overview.md) | High-level system design, layers, and component map |
| [processes-and-ipc.md](processes-and-ipc.md) | Multi-process model and inter-process communication |
| [services.md](services.md) | Detailed description of every Android Service |
| [call-flows.md](call-flows.md) | Step-by-step incoming and outgoing call flows |
| [pigeon-api.md](pigeon-api.md) | Pigeon API surface — all `@HostApi` and `@FlutterApi` interfaces |
| [notifications.md](notifications.md) | Notification channels, builders, and actions |

## Quick orientation

```
Flutter (Dart)
    │  Pigeon (Method Channel)
    ▼
WebtritCallkeepPlugin          ← FlutterPlugin entry point (main process)
    │
    ├── ForegroundService      ← Bound to Activity; bridges Telecom ↔ Flutter
    │       │ local broadcast
    │       ▼
    │   PhoneConnectionService ← Android ConnectionService (:callkeep_core process)
    │
    ├── SignalingIsolateService ← Long-lived foreground service + Flutter background isolate
    │
    └── IncomingCallService    ← Transient service for push-notification incoming calls
```
