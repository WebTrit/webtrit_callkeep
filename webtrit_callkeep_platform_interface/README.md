# webtrit_callkeep_platform_interface

The common platform interface for the `webtrit_callkeep` federated plugin. Defines the abstract
API contract, all shared data models, and delegate interfaces that every platform implementation
must satisfy.

---

## Purpose

This package exists so that:

- Platform implementations (`webtrit_callkeep_android`, `webtrit_callkeep_ios`) implement a
  consistent interface without depending on each other.
- The aggregator package (`webtrit_callkeep`) programs against a single abstract type.
- Third-party platform implementations can extend `WebtritCallkeepPlatform` without forking the
  plugin.

---

## Package structure

```
lib/src/
├── webtrit_callkeep_platform_interface.dart  # WebtritCallkeepPlatform abstract class
├── models/                  # All shared data types
│   ├── callkeep_options.dart
│   ├── callkeep_handle.dart
│   ├── callkeep_connection.dart
│   ├── callkeep_audio_device.dart
│   ├── callkeep_end_call_reason.dart
│   ├── callkeep_incoming_call_metadata.dart
│   ├── callkeep_incoming_call_error.dart
│   ├── callkeep_call_request_error.dart
│   ├── callkeep_permission.dart
│   ├── callkeep_special_permission.dart
│   ├── callkeep_special_permission_status.dart
│   ├── callkeep_android_battery_mode.dart
│   ├── callkeep_lifecycle_event.dart
│   ├── callkeep_service_status.dart
│   ├── callkeep_signaling_status.dart
│   ├── callkeep_push_notification_status_sync.dart
│   └── callkeep_log_type.dart
├── delegate/                # Delegate interfaces
│   ├── callkeep_delegate.dart                 # Main call event callbacks
│   ├── callkeep_push_registry_delegate.dart   # iOS PushKit VoIP token/push (iOS only)
│   ├── callkeep_android_service_delegate.dart # Background isolate callbacks (Android only)
│   └── callkeep_logs_delegate.dart            # Native log forwarding
├── helpers/                 # Utility helpers
├── annotation/              # Internal annotations
└── consts/                  # Shared constants
```

---

## Key types

### `CallkeepHandle`

Wraps a phone number or SIP URI:

```dart
CallkeepHandle.number('+15551234567')
CallkeepHandle.sip('user@example.com')
```

### `CallkeepDelegate`

Implement this to receive call lifecycle events from the platform:

| Callback | When it fires |
|---|---|
| `didPushIncomingCall` | Platform registered the incoming call (or reports an error) |
| `performAnswerCall` | User answered from system UI |
| `performEndCall` | User ended from system UI or system terminated the call |
| `performStartCall` | User initiated outgoing call from system UI |
| `continueStartCallIntent` | System confirmed outgoing call intent |
| `performSetHeld` | Hold toggled from system UI |
| `performSetMuted` | Mute toggled from system UI |
| `performSendDTMF` | DTMF sent from system dial pad |
| `performSetSpeaker` | Speaker toggled from system UI |
| `didActivateAudioSession` | System activated the audio session |
| `didDeactivateAudioSession` | System deactivated the audio session |
| `didReset` | System reset all call state |

`perform*` methods return `Future<bool>`. Return `false` to signal failure — the platform will
terminate the call.

### `CallkeepEndCallReason`

Reason passed to `reportEndCall`:
`failed`, `remoteEnded`, `unanswered`, `answeredElsewhere`, `declinedElsewhere`, `missed`

### `CallkeepConnection`

Snapshot of a single call's state (Android only). Contains `callId`, `state`
(`CallkeepConnectionState`), and `metadata`.

---

## Implementing a new platform

Extend `WebtritCallkeepPlatform` and register your implementation:

```dart
class WebtritCallkeepCustom extends WebtritCallkeepPlatform {
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeepCustom();
  }
  // override all abstract methods
}
```

---

## Related packages

| Package | Description |
|---|---|
| [`webtrit_callkeep`](../webtrit_callkeep/README.md) | Public API aggregator |
| [`webtrit_callkeep_android`](../webtrit_callkeep_android/README.md) | Android implementation |
| [`webtrit_callkeep_ios`](../webtrit_callkeep_ios/README.md) | iOS implementation |
