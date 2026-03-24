# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See **[AGENTS.md](AGENTS.md)** for commands, code style, and architecture detail.

## Role of this package

`webtrit_callkeep` is the **user-facing aggregator** package. App developers import only this package — it re-exports everything from `webtrit_callkeep_platform_interface` and delegates all calls to the platform implementation.

## Key singletons

| Class | Access | Purpose |
| --- | --- | --- |
| `Callkeep` | `Callkeep()` | Core call operations + status stream |
| `CallkeepConnections` | `CallkeepConnections()` | Connection state queries (Android only) |
| `AndroidCallkeepServices` | static | Bootstrap Android background services |
| `AndroidCallkeepUtils` | static | Permissions, diagnostics, SMS config, activity control |

All singletons delegate to `WebtritCallkeepPlatform.instance` — never call platform package APIs directly from app code.

## Commands

```bash
# From this directory
flutter pub get
flutter analyze lib test
dart format --line-length 80 --set-exit-if-changed lib test
flutter test
```

## Related packages

| Package | Path |
| --- | --- |
| Platform interface | `../webtrit_callkeep_platform_interface` |
| Android impl | `../webtrit_callkeep_android` |
| iOS impl | `../webtrit_callkeep_ios` |
