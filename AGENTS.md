# AGENTS.md — Webtrit CallKeep

Central reference for build commands, code standards, and architecture. All Claude/AI agents working in this repo must follow these rules.

---

## Commands

### Install dependencies (all packages)
```bash
flutter pub global activate very_good_cli
very_good packages get --recursive
```

### Format (run inside a package directory)
```bash
dart format --line-length 120 --set-exit-if-changed lib
```

### Analyze
```bash
flutter analyze
```

### Test
```bash
very_good test -j 4 --optimization --coverage --test-randomize-ordering-seed random
```

### Run a single test file
```bash
flutter test test/path/to/test_file.dart
```

### Pigeon code generation
Run from `webtrit_callkeep_android/` or `webtrit_callkeep_ios/` after modifying the pigeon input:
```bash
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```
Generated files (`*.pigeon.dart`, Kotlin/Swift output) must be committed together with the input change.

---

## Code style

- **Quotes**: single quotes everywhere.
- **Line length**: 120 characters (`formatter: page_width: 120` in all `analysis_options.yaml`).
- **Trailing commas**: required on multi-line argument lists and collection literals.
- **Import order** (four groups, alphabetical within each, blank line between groups):
  1. `dart:` SDK imports
  2. `package:flutter/` imports
  3. Third-party `package:` imports
  4. Relative imports
- **Generated files**: never manually edit `*.g.dart`, `*.pigeon.dart`, `*.freezed.dart`. Regenerate via Pigeon or build_runner.
- **Linter**: `very_good_analysis` is active in all packages — fix warnings before committing.

---

## Architecture overview

This is a Flutter **federated plugin** (mono-repo):

```
webtrit_callkeep/                    # Aggregator — no logic, delegates to platform packages
webtrit_callkeep_platform_interface/ # Shared abstract API, models, delegates
webtrit_callkeep_android/            # Android impl (Flutter Dart + Kotlin)
webtrit_callkeep_ios/                # iOS impl (Flutter Dart + Swift)
webtrit_callkeep_linux|macos|web|windows/  # Stubs (no-op implementations)
```

### Data flow

```
Flutter app
    │  setDelegate(CallkeepDelegate)
    │  reportNewIncomingCall / startCall / endCall / ...
    ▼
Callkeep (singleton, webtrit_callkeep)
    │
    ▼
WebtritCallkeepPlatform (platform_interface)
    │
    ├── WebtritCallkeepAndroid  →  Pigeon  →  Kotlin services
    └── WebtritCallkeepIOS      →  Pigeon  →  Swift/CallKit
```

**Flutter → Platform**: `reportNewIncomingCall`, `startCall`, `answerCall`, `endCall`, `setHeld`, `setMuted`, `setSpeaker`, `sendDTMF`, `setAudioDevice`.

**Platform → Flutter** (via `CallkeepDelegate`): `performStartCall`, `performAnswerCall`, `performEndCall`, `performSetHeld`, `performSetMuted`, `performSendDTMF`, `performAudioDeviceSet`, `performAudioDevicesUpdate`, `didActivateAudioSession`, `didDeactivateAudioSession`, `didReset`, `continueStartCallIntent`, `didPushIncomingCall`.

`perform*` methods return `Future<bool>` — return `true` on success, `false` on failure. Returning `false` causes the native side to abort the operation.

### Background isolates (Android only)

Two mutually exclusive modes:

| Mode | Service | Use case |
|---|---|---|
| Push notification | `IncomingCallService` | App uses FCM; one-shot isolate per push |
| Signaling | `SignalingService` | Persistent connection; isolate lives with foreground service |

Background isolate entry points **must** be annotated `@pragma('vm:entry-point')`.

---

## Key invariants

- **Do not** manually edit Pigeon-generated files. Change `pigeons/callkeep.messages.dart` and regenerate.
- **Do not** remove or rename Kotlin/Java classes annotated with `@Keep` — they are exempt from ProGuard/R8 shrinking.
- The `Callkeep` class is a **singleton** — never instantiate it directly; use `Callkeep()`.
- Platform-specific APIs (Android battery mode, SMS reception, activity control) are accessed only through the platform interface, not imported directly from a platform package.
