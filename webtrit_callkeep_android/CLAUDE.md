# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See **[AGENTS.md](AGENTS.md)** for commands, architecture detail, Pigeon workflow, and Android-specific rules.

## Architecture documentation

Detailed per-component docs live in **[docs/](docs/)**. Start with
**[docs/architecture.md](docs/architecture.md)** for the overview and component index. Key docs:

- [docs/dual-process.md](docs/dual-process.md) — process boundaries, IPC design, critical rules
- [docs/call-flows.md](docs/call-flows.md) — incoming, outgoing, teardown flows step by step
- [docs/foreground-service.md](docs/foreground-service.md) — `ForegroundService` internals
- [docs/callkeep-core.md](docs/callkeep-core.md) — `CallkeepCore` facade (use this, not `connectionManager` directly)
- [docs/ipc-broadcasting.md](docs/ipc-broadcasting.md) — full cross-process event catalogue

## Role of this package

`webtrit_callkeep_android` is the **Android platform implementation** of the callkeep plugin. It contains two layers:

- **Dart layer** (`lib/src/`) — `WebtritCallkeepAndroid` registers itself as the platform instance and proxies calls via Pigeon.
- **Kotlin layer** (`android/`) — Android services, Telecom integration, notification management.

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
- Never rename/remove Kotlin classes annotated `@Keep`.
- `PhoneConnectionService` runs in a **separate OS process** (`:callkeep_core`) — no shared in-memory state with main process. Use `CallkeepCore.instance` for all cross-process interactions.
- `CallMetadata` must NOT implement `Parcelable` — use `toBundle()` / `fromBundle()` instead.
- Background isolate entry points need `@pragma('vm:entry-point')`.

## Related packages

| Package | Path |
|---|---|
| Platform interface | `../webtrit_callkeep_platform_interface` |
| Aggregator | `../webtrit_callkeep` |
