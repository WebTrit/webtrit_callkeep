# webtrit_callkeep_android

Android implementation of [`webtrit_callkeep`](../webtrit_callkeep). Integrates Android Telecom,
foreground services, and background Flutter isolates to deliver VoIP call management on Android.

---

## Architecture

The implementation runs across **two OS processes**:

| Process          | Key class                | Role                                                        |
|------------------|--------------------------|-------------------------------------------------------------|
| `main`           | `ForegroundService`      | Flutter engine host, Pigeon host API, Telecom bridge        |
| `:callkeep_core` | `PhoneConnectionService` | Android `ConnectionService`, owns `PhoneConnection` objects |

IPC between processes uses app-scoped broadcasts and explicit `startService` intents.
All main-process code that reads call state or sends Telecom commands goes through
`CallkeepCore.instance` — never through `PhoneConnectionService.connectionManager` directly
(it is an empty object in the main JVM heap).

Full architecture documentation lives in [`docs/`](docs/):

| Document                                                             | What it covers                                        |
|----------------------------------------------------------------------|-------------------------------------------------------|
| [docs/architecture.md](docs/architecture.md)                         | Overview, component index, high-level diagram         |
| [docs/dual-process.md](docs/dual-process.md)                         | Process boundaries, IPC design                        |
| [docs/call-flows.md](docs/call-flows.md)                             | Incoming, outgoing, teardown flows step by step       |
| [docs/foreground-service.md](docs/foreground-service.md)             | `ForegroundService` — Pigeon host, broadcast receiver |
| [docs/phone-connection-service.md](docs/phone-connection-service.md) | `PhoneConnectionService` — Telecom integration        |
| [docs/ipc-broadcasting.md](docs/ipc-broadcasting.md)                 | Cross-process event catalogue                         |
| [docs/pigeon-apis.md](docs/pigeon-apis.md)                           | All Pigeon host and Flutter APIs                      |

---

## Background modes

Two mutually exclusive modes for handling calls while the app is backgrounded:

### Push notification isolate (one-shot)

Triggered by an FCM message. A short-lived Flutter isolate handles the incoming call, then exits.

- Entry point: `AndroidCallkeepServices.backgroundPushNotificationBootstrapService`
- Callback:
  `Future<void> onCallback(CallkeepPushNotificationSyncStatus, CallkeepIncomingCallMetadata?)`
- Service: `IncomingCallService`

### Signaling isolate (persistent)

A long-running Flutter isolate maintains a signaling connection (WebSocket) to the server.

- Entry point: `AndroidCallkeepServices.backgroundSignalingBootstrapService`
- Callback: `Future<void> onCallback(CallkeepServiceStatus, CallkeepIncomingCallMetadata?)`
- Service: `SignalingIsolateService`

Both modes require the background isolate entry-point function to be annotated
`@pragma('vm:entry-point')`.

---

## SMS-triggered calls

Android only. Requires `RECEIVE_SMS` + `BROADCAST_SMS` permissions.

- Message prefix: `<#> WEBTRIT:` (hard-coded security filter, do not change).
- Regex pattern (4 capture groups): `callId`, `handle`, `displayName`, `hasVideo`.
- Configured via `initializeSmsReception(messagePrefix:, regexPattern:)`.

---

## Integration tests

The public API is covered by integration tests located in
[`../webtrit_callkeep/example/integration_test/`](../webtrit_callkeep/example/integration_test/).

| Test file                                  | What it covers                                                                              |
|--------------------------------------------|---------------------------------------------------------------------------------------------|
| `callkeep_lifecycle_test.dart`             | `setUp` / `tearDown` state machine, `isSetUp`, `statusStream` transitions                   |
| `callkeep_call_scenarios_test.dart`        | Incoming call answer, decline, hang-up, hold/unhold, mute/unmute, DTMF                      |
| `callkeep_state_machine_test.dart`         | Full answer-hold-mute-unmute-unhold-end sequence, two-call hold swap                        |
| `callkeep_foreground_service_test.dart`    | Main-process signaling path: answer/end timing, deduplication, cold-start adoption          |
| `callkeep_background_services_test.dart`   | Push notification isolate and signaling isolate paths, cross-path deduplication             |
| `callkeep_connections_test.dart`           | `getConnection`, `getConnections`, `cleanConnections`, `updateActivitySignalingStatus`      |
| `callkeep_delegate_edge_cases_test.dart`   | `setDelegate(null)` mid-call, delegate swap, `didPushIncomingCall`, audio session callbacks |
| `callkeep_client_scenarios_test.dart`      | `answerCall` idempotency, ringback sound, async `performEndCall` contract, signaling race   |
| `callkeep_reportendcall_reasons_test.dart` | All `CallkeepEndCallReason` values via `reportEndCall`                                      |
| `callkeep_stress_test.dart`                | Concurrent duplicate reports, rapid tearDown, spam scenarios                                |

Run on a connected device or emulator from the example app directory:

```bash
cd ../webtrit_callkeep/example
flutter test integration_test/<test_file>.dart
```

---

## Code generation

Pigeon generates type-safe Kotlin/Dart bindings from a single source file.

```bash
# From this directory
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```

- **Never manually edit** `lib/src/common/callkeep.pigeon.dart` or the Kotlin files under
  `android/src/main/kotlin/.../api/`.
- Commit both the input file and all generated outputs together.

---

## Build & lint

```bash
# From this directory
flutter pub get
flutter analyze lib test
dart format --line-length 80 --set-exit-if-changed lib test
```

---

## Key invariants

- Classes annotated `@Keep` in Kotlin must not be renamed or removed (ProGuard/R8 rules reference
  them).
- `CallMetadata` must NOT implement `Parcelable` — `system_server` would fail to deserialize it.
  Use `toBundle()` / `fromBundle()` instead.
- Never call `PhoneConnectionService.connectionManager.*` from the main process.
  Use `CallkeepCore.instance`.
- All Pigeon host API implementations run on the platform thread — do not block.
- Never import Kotlin-layer constants or classes into the Dart layer directly; go through Pigeon.

---

## Related packages

| Package            | Path                                                                               |
|--------------------|------------------------------------------------------------------------------------|
| Platform interface | [`../webtrit_callkeep_platform_interface`](../webtrit_callkeep_platform_interface) |
| Aggregator         | [`../webtrit_callkeep`](../webtrit_callkeep)                                       |
| iOS implementation | [`../webtrit_callkeep_ios`](../webtrit_callkeep_ios)                               |

See [AGENTS.md](AGENTS.md) for contribution guidelines, detailed architecture notes, and
Android-specific rules.
