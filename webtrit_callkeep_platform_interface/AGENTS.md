# AGENTS.md — webtrit_callkeep_platform_interface

This package defines the **shared contract** between the aggregator and all platform implementations. It contains no platform-specific code.

---

## Role

- Declares the abstract `WebtritCallkeepPlatform` class that every platform package must implement.
- Provides all shared models, enums, and delegate interfaces used across platforms.
- Contains no Flutter plugin registration — only pure Dart.

---

## Key source locations

| Path | Contents |
| --- | --- |
| `lib/src/webtrit_callkeep_platform_interface.dart` | Abstract platform class |
| `lib/src/delegate/callkeep_delegate.dart` | `CallkeepDelegate` — main callback interface |
| `lib/src/delegate/callkeep_android_service_delegate.dart` | `CallkeepBackgroundServiceDelegate` — background isolate callbacks |
| `lib/src/delegate/callkeep_push_registry_delegate.dart` | `PushRegistryDelegate` — iOS PushKit token/call events |
| `lib/src/delegate/callkeep_logs_delegate.dart` | `CallkeepLogsDelegate` — native log forwarding |
| `lib/src/models/` | All shared data classes and enums |
| `lib/src/consts/` | Call path keys/values, call data constants |
| `lib/src/helpers/` | `AndroidPendingCallHandler` and other utilities |

---

## Delegate contracts

### `CallkeepDelegate`

The primary callback interface. The app implements this and passes it via `Callkeep().setDelegate(...)`.

`perform*` methods return `Future<bool>`:

- Return `true` — operation succeeded, native UI proceeds.
- Return `false` — operation failed, native side aborts (e.g., tears down the connection).

Do not add new methods here without updating all platform implementations and the aggregator.

### `CallkeepBackgroundServiceDelegate`

Used inside background isolates (Android only). Methods are `void`, not `Future<bool>` — fire-and-forget from the native side.

### `PushRegistryDelegate`

iOS-only. Handle in a class separate from `CallkeepDelegate` and register via `Callkeep().setPushRegistryDelegate(...)`.

---

## Models

All models use `Equatable` for value equality. Do not override `==`/`hashCode` manually.

Key models:

| Class | Purpose |
| --- | --- |
| `CallkeepHandle` | Represents a phone number or generic handle; use factory constructors (`.number(...)`, `.generic(...)`) |
| `CallkeepOptions` | Top-level config; contains `CallkeepIOSOptions` and `CallkeepAndroidOptions` |
| `CallkeepIncomingCallMetadata` | Metadata passed to background isolate callbacks |
| `CallkeepServiceStatus` | Combines `CallkeepLifecycleEvent` + optional `CallkeepSignalingStatus` |
| `CallkeepConnection` | Snapshot of a tracked connection's state |
| `CallkeepEndCallReason` | Enum for why a call ended (remoteEnded, failed, unanswered, etc.) |
| `CallkeepAudioDevice` | Represents an audio output device with type, id, name |

---

## Adding a new model or enum

1. Create the file in `lib/src/models/`.
2. Export it from `lib/src/models/models.dart`.
3. If it needs a Pigeon equivalent, add it to `pigeons/callkeep.messages.dart` in the platform packages and add a converter extension in `lib/src/common/converters.dart`.

---

## What NOT to put here

- Platform-specific logic (use the platform package).
- Pigeon-generated code (lives in platform packages).
- Any `dart:io` / `dart:html` imports (this must remain pure Dart).
