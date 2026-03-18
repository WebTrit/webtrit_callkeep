# AGENTS.md — webtrit_callkeep_android

Android platform implementation. Two layers: Dart (Flutter side) and Kotlin (native side).

---

## Package structure

```
webtrit_callkeep_android/
├── lib/src/
│   ├── webtrit_callkeep_android.dart   # WebtritCallkeepAndroid — platform impl
│   └── common/
│       ├── callkeep.pigeon.dart        # Pigeon-generated Dart bindings (DO NOT EDIT)
│       └── converters.dart             # Extension methods: Pigeon ↔ platform_interface types
├── pigeons/
│   └── callkeep.messages.dart          # Pigeon input — edit this, then regenerate
└── android/src/main/kotlin/com/webtrit/callkeep/
    ├── WebtritCallkeepPlugin.kt         # Plugin entry point
    ├── services/                        # Android services (see below)
    ├── models/                          # Kotlin data models
    ├── managers/                        # Audio, notification channel management
    └── api/                             # Pigeon-generated Kotlin APIs (DO NOT EDIT)
```

---

## Dual-process architecture

The Android implementation runs in **two separate OS processes**:

| Process | Key class | Role |
|---|---|---|
| Main app process | `ForegroundService` | Hosts Flutter engine; Pigeon host API; bridges Telecom ↔ Flutter |
| `:callkeep_core` | `PhoneConnectionService` | Android Telecom `ConnectionService`; manages `PhoneConnection` objects |

**IPC between processes**: local broadcasts via `CommunicateServiceDispatcher` and `ConnectionServicePerformBroadcaster`.

`MainProcessConnectionTracker` tracks connection state in the main process, updated from broadcasts. It is exposed through the `CallkeepCore` interface.

### CallkeepCore -- the single access point

**All code in the main process must interact with `PhoneConnectionService` exclusively through `CallkeepCore.instance`.**

- State reads: `CallkeepCore.instance.getAll()`, `.exists()`, `.getState()`, etc. -- backed by `MainProcessConnectionTracker`.
- Commands: `CallkeepCore.instance.startAnswerCall()`, `.tearDownService()`, `.sendTearDownConnections()`, etc. -- dispatched via explicit `startService` intents or broadcasts.

**Do NOT call `connectionManager.*` directly from the main process.** After the `:callkeep_core` process split, `connectionManager` in the main JVM heap is an empty object -- any direct call to it becomes a silent no-op that is extremely hard to debug. The only correct path is through `CallkeepCore`.

### IPC events

All cross-process communication uses local broadcasts. Events are grouped by broadcaster:

**CallLifecycleEvent** (`:callkeep_core` -> Main):
`AnswerCall`, `DeclineCall`, `HungUp`, `OngoingCall`, `DidPushIncomingCall`, `OutgoingFailure`, `IncomingFailure`, `ConnectionNotFound`

**CallMediaEvent** (`:callkeep_core` -> Main):
`AudioMuting`, `AudioDeviceSet`, `AudioDevicesUpdate`, `SentDTMF`, `ConnectionHolding`

**CallCommandEvent** (Main -> `:callkeep_core`, with one ack going the other way):

| Event | Direction | Payload | Purpose |
|---|---|---|---|
| `TearDownConnections` | Main -> `:callkeep_core` | -- | Trigger `hungUp()` + `cleanConnections()` on all PhoneConnections |
| `TearDownComplete` | `:callkeep_core` -> Main | -- | Ack that tearDown completed |
| `ReserveAnswer` | Main -> `:callkeep_core` | `callId` | Deferred answer reservation cross-process |
| `CleanConnections` | Main -> `:callkeep_core` | -- | Clear all connections without `hungUp()` |

---

## Android services

| Service | Process | Purpose |
|---|---|---|
| `PhoneConnectionService` | `:callkeep_core` | Telecom integration; creates/destroys `PhoneConnection` |
| `ForegroundService` | main | Active call; Pigeon host; mute/hold/speaker/DTMF |
| `SignalingService` | main | Persistent background signaling isolate (foreground service) |
| `IncomingCallService` | main | One-shot push-notification-triggered call handling |
| `ActiveCallService` | main | Notification for multiple simultaneous calls |

---

## Pigeon workflow

1. Edit `pigeons/callkeep.messages.dart`.
2. Run from this package directory:
   ```bash
   flutter pub run pigeon --input pigeons/callkeep.messages.dart
   ```
3. Commit both the input file and all generated output (`callkeep.pigeon.dart`, Kotlin files under `android/`).

**Never manually edit** `lib/src/common/callkeep.pigeon.dart` or Kotlin files under `android/src/main/kotlin/…/api/`.

When adding a converter for a new Pigeon type, add an extension in `lib/src/common/converters.dart`.

---

## Background modes

Two mutually exclusive modes — choose one per app:

### Push notification isolate (one-shot)
- Entry point: `AndroidCallkeepServices.backgroundPushNotificationBootstrapService`
- Triggered by: FCM or another isolate calling `reportNewIncomingCall` on the bootstrap service
- Callback signature: `Future<void> onCallback(CallkeepPushNotificationSyncStatus, CallkeepIncomingCallMetadata?)`
- Service: `IncomingCallService`

### Signaling isolate (persistent)
- Entry point: `AndroidCallkeepServices.backgroundSignalingBootstrapService`
- Triggered by: app lifecycle changes, service start/stop
- Callback signature: `Future<void> onCallback(CallkeepServiceStatus, CallkeepIncomingCallMetadata?)`
- Service: `SignalingService`

**Both modes**: the background isolate entry point function **must** be annotated `@pragma('vm:entry-point')`. The isolate uses `CallkeepBackgroundServiceDelegate` (`performAnswerCall`, `performEndCall`) for native → Dart events.

---

## SMS-triggered calls

- Android only; requires `RECEIVE_SMS` + `BROADCAST_SMS` permissions.
- Message prefix: `<#> WEBTRIT:` (hard-coded security filter, do not change).
- Regex pattern (4 capture groups in order): `callId`, `handle`, `displayName`, `hasVideo`.
- Configured via `initializeSmsReception(messagePrefix:, regexPattern:)`.
- Full regex spec: `docs/sms_trigger_regex_requirements.md` at repo root.

---

## Android-only APIs (accessed via platform interface)

| API | Purpose |
|---|---|
| `showOverLockscreen` / `wakeScreenOnShow` | Activity control for lock screen |
| `sendToBackground` / `isDeviceLocked` | App window management |
| `getFullScreenIntentPermissionStatus` / `openFullScreenIntentSettings` | Full-screen intent permissions |
| `getBatteryMode` | Battery optimization status |
| `requestPermissions` / `checkPermissionsStatus` | Runtime permission management |
| `getDiagnosticReport` | Debug diagnostics map |
| `getConnection` / `getConnections` / `cleanConnections` | Connection state queries |
| `updateActivitySignalingStatus` | Push signaling status to Telecom |
| `playRingbackSound` / `stopRingbackSound` | Audio control |

---

## Key invariants

- Classes annotated `@Keep` in Kotlin **must not** be renamed or removed — they are referenced by ProGuard/R8 rules.
- All Pigeon host API implementations run on the platform thread; do not block.
- `PhoneConnectionService` runs in a separate process — it cannot share in-memory state with the main process; use IPC.
- **Never call `connectionManager.*` directly from the main process.** Use `CallkeepCore.instance` instead (see Dual-process architecture above).
- Never import Kotlin-layer constants or classes into the Dart layer directly; go through Pigeon.
