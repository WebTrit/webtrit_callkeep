# webtrit_callkeep_android — Documentation

`webtrit_callkeep_android` exposes the
Android [Telecom framework](https://developer.android.com/guide/topics/connectivity/telecom) to
Flutter as a self-managed `ConnectionService`. It registers a `PhoneAccount`, reports calls to the
system, runs a persistent background signaling service, and handles push-notification-triggered
incoming calls — all bridged to Dart via Pigeon-generated code.

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
    │
    ├── IncomingCallService    ← Transient service for push-notification incoming calls
    │
    └── ActiveCallService      ← Foreground service during an active call
```

---

## Documents

| File                                                         | What you'll find                                                                                 |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| [architecture.md](architecture.md)                           | Plugin responsibilities, layer diagram, process model, component map, key design decisions       |
| [call-triggers.md](call-triggers.md)                         | The 4 ways a call can be initiated (push, signaling, direct, SMS) — entry points and brief flows |
| [call-flows.md](call-flows.md)                               | Step-by-step ASCII flow diagrams for every call scenario                                         |
| [ipc.md](ipc.md)                                             | `ConnectionServicePerformBroadcaster`, `ConnectionPerform` event table, in-process channels      |
| [foreground-service.md](foreground-service.md)               | `ForegroundService` — lifecycle, Pigeon wiring, outgoing retry, hot-restart restoration          |
| [phone-connection-service.md](phone-connection-service.md)   | `PhoneConnectionService` — state machine, `ConnectionManager`, dispatcher, audio                 |
| [signaling-isolate-service.md](signaling-isolate-service.md) | `SignalingIsolateService` — boot resilience, isolate lifecycle, Pigeon API                       |
| [incoming-call-service.md](incoming-call-service.md)         | `IncomingCallService` — push path, notification, isolate policy, answer/decline flow             |
| [active-call-service.md](active-call-service.md)             | `ActiveCallService` — role, foreground types, notification                                       |
| [common-utilities.md](common-utilities.md)                   | `StorageDelegate`, `ContextHolder`, `RetryManager`, `FlutterEngineHelper`, `TelephonyUtils`      |
| [notifications.md](notifications.md)                         | Notification channels, builders, and actions                                                     |
| [pigeon-api.md](pigeon-api.md)                               | Full Pigeon API reference — all `@HostApi` and `@FlutterApi` interfaces                          |

---

## Call trigger types

There are four distinct ways a call can be initiated. See [call-triggers.md](call-triggers.md) for
details on each.

- **Push notification** — FCM/push wakes `IncomingCallService`, which reports the call to Telecom
  and optionally starts a Flutter isolate.
- **Persistent signaling** — A WebSocket message arrives in the `SignalingIsolateService` background
  isolate, which calls `PHostBackgroundSignalingIsolateApi.incomingCall()`.
- **Foreground direct** — The running Flutter app calls `WebtritCallkeepAndroid.startCall()`, routed
  through `ForegroundService`.
- **SMS fallback** (optional) — An SMS matching a configured regex triggers
  `IncomingCallSmsTriggerReceiver`.

---

## Where to start

**New contributor:**

1. [architecture.md](architecture.md) — understand the overall structure
2. [ipc.md](ipc.md) — understand how the two processes communicate
3. [call-triggers.md](call-triggers.md) — see how calls originate

**Debugging a call flow:**

1. [call-triggers.md](call-triggers.md) — identify which trigger applies
2. [call-flows.md](call-flows.md) — follow the full step-by-step trace
3. Service file for the component in question

**Understanding IPC:**

1. [ipc.md](ipc.md) — `ConnectionPerform` events and channel registration
2. [phone-connection-service.md](phone-connection-service.md) — how events are dispatched
3. [foreground-service.md](foreground-service.md) — how events are received and forwarded to Flutter
