# webtrit_callkeep_ios

iOS implementation of [`webtrit_callkeep`](../webtrit_callkeep/README.md). Integrates CallKit and
PushKit to deliver native call UI and background VoIP push handling.

---

## How it works

| App state | Incoming call UI |
|---|---|
| Foreground | Flutter-based incoming call screen |
| Background / locked | System CallKit UI |
| Terminated | PushKit wakes the app; CallKit UI shown after app initializes |

PushKit delivers a VoIP push before the user interacts with anything, giving the app time to
establish signaling and media before CallKit presents the call.

There are no persistent background services on iOS — all background work goes through
CallKit / PushKit.

---

## Package structure

```
webtrit_callkeep_ios/
├── lib/src/
│   ├── webtrit_callkeep_ios.dart     # WebtritCallkeepIOS — registers as platform instance
│   └── common/
│       ├── callkeep.pigeon.dart      # Pigeon-generated Dart bindings (DO NOT EDIT)
│       └── converters.dart           # Pigeon <-> platform_interface type conversions
├── pigeons/
│   └── callkeep.messages.dart        # Pigeon input — edit this, then regenerate
└── ios/Classes/                       # Swift implementation (CallKit + PushKit)
```

---

## Delegates

| Delegate | Registration | Purpose |
|---|---|---|
| `CallkeepDelegate` | `Callkeep().setDelegate(...)` | Call lifecycle events |
| `PushRegistryDelegate` | `Callkeep().setPushRegistryDelegate(...)` | PushKit VoIP token and incoming push payloads |
| `CallkeepLogsDelegate` | `Callkeep().setLogsDelegate(...)` | Forward native logs to Dart |

`PushRegistryDelegate` must be set before the app enters the background if VoIP pushes are
expected. Failing to do so causes missed pushes.

---

## iOS-only API

```dart
// Returns the current PushKit VoIP token, or null if not yet issued.
final token = await Callkeep().pushTokenForPushTypeVoIP();
```

---

## Required capabilities

In Xcode, enable:

- **Push Notifications**
- **Background Modes -> Voice over IP**

Without these, `reportNewIncomingCall` silently fails when the app is backgrounded.

---

## Code generation

```bash
# From this directory
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```

Never manually edit `lib/src/common/callkeep.pigeon.dart` or Pigeon-generated Swift files under
`ios/Classes/`. Commit both the input file and all generated outputs together.

---

## Build & lint

```bash
flutter pub get
flutter analyze lib test
dart format --line-length 80 --set-exit-if-changed lib test
```

---

## Key invariants

- No persistent background services — all background handling goes through CallKit/PushKit.
- `PushRegistryDelegate` is separate from `CallkeepDelegate`.
- Never block the main thread in Swift delegate callbacks.
- Minimum deployment target: iOS 11.

---

## Related packages

| Package | Description |
|---|---|
| [`webtrit_callkeep`](../webtrit_callkeep/README.md) | Public API aggregator |
| [`webtrit_callkeep_platform_interface`](../webtrit_callkeep_platform_interface/README.md) | Shared interface and models |
| [`webtrit_callkeep_android`](../webtrit_callkeep_android/README.md) | Android implementation |
