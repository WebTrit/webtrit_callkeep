# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See **[AGENTS.md](AGENTS.md)** for commands, architecture detail, Pigeon workflow, and iOS-specific rules.

## Role of this package

`webtrit_callkeep_ios` is the **iOS platform implementation** of the callkeep plugin. It contains two layers:

- **Dart layer** (`lib/src/`) — `WebtritCallkeepIOS` registers itself as the platform instance and proxies calls via Pigeon.
- **Swift layer** (`ios/Classes/`) — CallKit + PushKit integration.

## Commands

```bash
# From this directory
flutter pub get
flutter analyze lib test
dart format --line-length 80 --set-exit-if-changed lib test

# Pigeon regeneration (after editing pigeons/callkeep.messages.dart)
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```

## Critical rules

- Never edit `lib/src/common/callkeep.pigeon.dart` manually — regenerate via Pigeon.
- No persistent background services on iOS — all background handling goes through CallKit/PushKit.
- `PushRegistryDelegate` must be set before the app enters background if VoIP pushes are expected.
- iOS minimum deployment target: **iOS 11**.

## Related packages

| Package | Path |
| --- | --- |
| Platform interface | `../webtrit_callkeep_platform_interface` |
| Aggregator | `../webtrit_callkeep` |
