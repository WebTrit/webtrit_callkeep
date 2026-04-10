# AGENTS.md — webtrit_callkeep (aggregator)

This package is the **public API surface** for app developers. It contains no platform-specific logic — only thin wrappers that delegate to `WebtritCallkeepPlatform.instance`.

---

## Package structure

```text
lib/src/
├── callkeep.dart                  # Callkeep singleton — core call operations + status stream
├── callkeep_connections.dart      # CallkeepConnections singleton — connection state (Android only)
├── webtrit_callkeep_permissions.dart  # Permission helpers
├── webtrit_callkeep_sound.dart    # Sound playback helpers
├── webtrit_callkeep_logs.dart     # Logs delegate helpers
└── android/
    ├── services/
    │   ├── android_callkeep_services.dart          # AndroidCallkeepServices — static service registry
    │   ├── background_signaling_bootstrap_service.dart
    │   ├── background_signaling_service.dart
    │   ├── background_push_notification_bootstrap_service.dart
    │   └── background_push_notification_service.dart
    └── utils/
        ├── android_callkeep_utils.dart        # AndroidCallkeepUtils — permissions, diagnostics, SMS, activity
        ├── activity_control.dart
        ├── callkeep_diagnostics.dart
        └── sms_bootstrap_reception_config.dart
```

---

## Key classes

### `Callkeep` (singleton)

- `setUp(CallkeepOptions)` / `tearDown()` — initialize/shutdown native integration
- `reportNewIncomingCall` / `startCall` / `answerCall` / `endCall` — call lifecycle
- `setHeld` / `setMuted` / `sendDTMF` / `setAudioDevice` — in-call controls
- `statusStream` / `currentStatus` — `CallkeepStatus` enum stream (uninitialized → configuring → active → terminating)
- `setDelegate(CallkeepDelegate?)` / `setPushRegistryDelegate(PushRegistryDelegate?)` — event callbacks

### `AndroidCallkeepServices` (static, Android only)

Entry point for Android background modes. Always use these static singletons:

- `backgroundSignalingBootstrapService` — configure + start/stop persistent signaling service
- `backgroundSignalingService` — report incoming calls / end calls from signaling isolate
- `backgroundPushNotificationBootstrapService` — configure + trigger push-notification-based calls
- `backgroundPushNotificationService` — end calls from push notification isolate
- `smsReceptionConfig` — **deprecated**, use `AndroidCallkeepUtils.smsReceptionConfig` instead

### `AndroidCallkeepUtils` (static, Android only)

- `activityControl` — lock screen overlay, wake screen, send to background
- `permissions` — request/check `CallkeepPermission` entries
- `diagnostics` — get diagnostic report map
- `smsReceptionConfig` — initialize SMS-triggered call reception

### `CallkeepConnections` (singleton, Android only)

- `getConnection(callId)` / `getConnections()` / `cleanConnections()`
- `updateActivitySignalingStatus(CallkeepSignalingStatus)`
- All methods are no-ops on non-Android platforms (return `null` / empty list)

---

## Rules

- **Do not** add platform-specific imports (`dart:io` Platform checks are acceptable for guard clauses, as in `CallkeepConnections`).
- **Do not** expose Pigeon types — all public API uses types from `webtrit_callkeep_platform_interface`.
- `setSpeaker` is `@Deprecated` — use `setAudioDevice` with a `CallkeepAudioDevice` instead.
- Background service callbacks **must** be top-level functions annotated `@pragma('vm:entry-point')`.
