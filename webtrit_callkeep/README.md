# webtrit_callkeep

The public-facing package of the `webtrit_callkeep` federated plugin. Add this package to your app
— it aggregates the iOS and Android implementations automatically.

For the full plugin overview, setup guide, and API reference see the
[root README](../README.md).

---

## Package structure

```
webtrit_callkeep/
├── lib/src/
│   ├── callkeep.dart                          # Callkeep — setUp, tearDown, call control
│   ├── callkeep_connections.dart              # Android-only connection state queries
│   ├── webtrit_callkeep_logs.dart             # Log delegate wiring
│   ├── webtrit_callkeep_permissions.dart      # Permission helpers
│   ├── webtrit_callkeep_sound.dart            # Ringback sound control
│   └── android/                              # Android-specific services
│       ├── services/
│       │   ├── background_push_notification_bootstrap_service.dart
│       │   ├── background_push_notification_service.dart
│       │   ├── background_signaling_bootstrap_service.dart
│       │   └── background_signaling_service.dart
│       └── utils/
└── example/
    └── integration_test/                      # Integration tests for both platforms
```

---

## Installation

```yaml
dependencies:
  webtrit_callkeep: ^<version>
```

No additional configuration is needed — the correct platform implementation is selected
automatically via federated plugin endorsement.

---

## Main API (`Callkeep`)

### Lifecycle

```dart
await Callkeep().setUp(CallkeepOptions(...));
await Callkeep().tearDown();
bool ready = Callkeep().isSetUp;
Stream<CallkeepStatus> status = Callkeep().statusStream;
```

### Delegates

```dart
Callkeep().setDelegate(myDelegate);                    // call events
Callkeep().setPushRegistryDelegate(myPRDelegate);      // iOS VoIP push token (iOS only)
Callkeep().setLogsDelegate(myLogsDelegate);            // native log forwarding
```

### Call reporting

```dart
await Callkeep().reportNewIncomingCall(callId, handle, displayName: name, hasVideo: false);
await Callkeep().reportConnectingOutgoingCall(callId, handle);
await Callkeep().reportConnectedOutgoingCall(callId, handle);
await Callkeep().reportUpdateCall(callId, ...);
await Callkeep().reportEndCall(callId, CallkeepEndCallReason.remoteEnded);
```

### Call control

```dart
await Callkeep().startCall(callId, handle, displayNameOrContactIdentifier: name);
await Callkeep().answerCall(callId);
await Callkeep().endCall(callId);
await Callkeep().setHeld(callId, true);
await Callkeep().setMuted(callId, true);
await Callkeep().setSpeaker(callId, true);
await Callkeep().sendDTMF(callId, '5');
```

---

## Android-specific APIs

### Background services

Two mutually exclusive modes for background call handling — see the
[root README](../README.md#android-background-modes) for full setup instructions.

```dart
// Push notification isolate (one-shot)
AndroidCallkeepServices.backgroundPushNotificationBootstrapService

// Signaling isolate (persistent)
AndroidCallkeepServices.backgroundSignalingBootstrapService
```

### Connection state queries (`CallkeepConnections`)

```dart
final conn = await CallkeepConnections.getConnection(callId);
final all  = await CallkeepConnections.getConnections();
await CallkeepConnections.cleanConnections();
await CallkeepConnections.updateActivitySignalingStatus(status);
```

### Permissions and diagnostics

```dart
AndroidCallkeepUtils.requestPermissions([...]);
AndroidCallkeepUtils.checkPermissionsStatus([...]);
AndroidCallkeepUtils.getFullScreenIntentPermissionStatus(); // Android 14+
```

---

## Integration tests

Located in [`example/integration_test/`](example/integration_test/). Cover lifecycle, call
scenarios, state machine, foreground/background service paths, connection queries, delegate edge
cases, and stress scenarios.

```bash
cd example
flutter test integration_test/<test_file>.dart
```

---

## Related packages

| Package | Description |
|---|---|
| [`webtrit_callkeep_platform_interface`](../webtrit_callkeep_platform_interface/README.md) | Shared Dart interface and models |
| [`webtrit_callkeep_android`](../webtrit_callkeep_android/README.md) | Android implementation |
| [`webtrit_callkeep_ios`](../webtrit_callkeep_ios/README.md) | iOS implementation |
