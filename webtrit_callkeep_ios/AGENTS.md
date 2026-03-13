# AGENTS.md — webtrit_callkeep_ios

iOS platform implementation. Two layers: Dart (Flutter side) and Swift (native side, CallKit + PushKit).

---

## Package structure

```
webtrit_callkeep_ios/
├── lib/src/
│   ├── webtrit_callkeep_ios.dart       # WebtritCallkeepIOS — platform impl
│   └── common/
│       ├── callkeep.pigeon.dart        # Pigeon-generated Dart bindings (DO NOT EDIT)
│       └── converters.dart             # Extension methods: Pigeon ↔ platform_interface types
├── pigeons/
│   └── callkeep.messages.dart          # Pigeon input — edit this, then regenerate
└── ios/Classes/                        # Swift implementation
```

---

## Pigeon workflow

1. Edit `pigeons/callkeep.messages.dart`.
2. Run from this package directory:
   ```bash
   flutter pub run pigeon --input pigeons/callkeep.messages.dart
   ```
3. Commit both the input file and all generated output (`callkeep.pigeon.dart`, Swift files under `ios/Classes/`).

**Never manually edit** `lib/src/common/callkeep.pigeon.dart` or Swift files in `ios/Classes/` that are Pigeon-generated.

When adding a converter for a new Pigeon type, add an extension in `lib/src/common/converters.dart`.

---

## iOS call UI behaviour

| App state | UI used |
|---|---|
| Foreground | Flutter-based incoming call screen |
| Background / locked | System CallKit UI |
| Terminated | CallKit via PushKit (VoIP push) wakes the app |

---

## Delegates

| Delegate | How to register | Purpose |
|---|---|---|
| `CallkeepDelegate` | `Callkeep().setDelegate(...)` | All call lifecycle events |
| `PushRegistryDelegate` | `Callkeep().setPushRegistryDelegate(...)` | PushKit VoIP token updates and incoming push |
| `CallkeepLogsDelegate` | `Callkeep().setLogsDelegate(...)` | Forward native logs to Dart |

`PushRegistryDelegate` is iOS-only and must be set before the app enters background if VoIP pushes are expected.

---

## Required iOS entitlements

- **Push Notifications** capability.
- **Background Modes → Voice over IP**.

Without these, `reportNewIncomingCall` will silently fail when the app is in the background.

---

## iOS-only API

`pushTokenForPushTypeVoIP()` — returns the current PushKit VoIP token. Returns `null` if PushKit is not available or the token has not been issued yet.

---

## Key invariants

- There is **no persistent background service** on iOS — all background work goes through CallKit/PushKit.
- The `PushRegistryDelegate` is separate from `CallkeepDelegate`; do not conflate them.
- Never block the main thread in Swift delegate callbacks — call Dart asynchronously via Pigeon.
- iOS minimum deployment target: **iOS 11**.
