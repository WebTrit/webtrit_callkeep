# webtrit_callkeep

A Flutter plugin for native VoIP call UI on iOS and Android.

- **iOS** — integrates with CallKit and PushKit to show the system incoming-call screen and handle
  background VoIP pushes.
- **Android** — integrates with Android Telecom (`ConnectionService`) and Flutter foreground
  services to handle incoming and outgoing calls, including while the app is backgrounded or
  terminated.

> **Note**: CallKit is unavailable in the China App Store. For distribution in China use standard
> local notifications as a fallback.

---

## Package structure

This is a federated plugin. Each package has its own README and `docs/` directory.

| Package | Description |
|---|---|
| [`webtrit_callkeep`](webtrit_callkeep/) | Public API — aggregates the platform implementations |
| [`webtrit_callkeep_platform_interface`](webtrit_callkeep_platform_interface/) | Shared Dart interface and models |
| [`webtrit_callkeep_android`](webtrit_callkeep_android/) | Android implementation (Telecom + foreground services) |
| [`webtrit_callkeep_ios`](webtrit_callkeep_ios/) | iOS implementation (CallKit + PushKit) |

---

## Platform support

| Platform | Minimum version |
|---|---|
| Android | API 26 (Android 8.0) |
| iOS | iOS 11 |

---

## Installation

```yaml
dependencies:
  webtrit_callkeep: ^<version>
```

---

## Quick start

### 1. Initialize

Call `setUp` once after your app is ready (main isolate only).

```dart
await callkeep.setUp(
  CallkeepOptions(
    ios: CallkeepIOSOptions(
      localizedName: 'My App',
      ringtoneSound: 'assets/ringtones/incoming.caf',
      iconTemplateImageAssetName: 'assets/callkeep_icon.png',
      maximumCallGroups: 1,
      maximumCallsPerCallGroup: 1,
      supportedHandleTypes: {CallkeepHandleType.number},
    ),
    android: CallkeepAndroidOptions(
      ringtoneSound: 'assets/ringtones/incoming.mp3',
      ringbackSound: 'assets/ringtones/outgoing.mp3',
    ),
  ),
);
```

### 2. Set delegate

```dart
callkeep.setDelegate(MyCallkeepDelegate());

// iOS only — handle PushKit VoIP tokens and push payloads
callkeep.setPushRegistryDelegate(MyPushRegistryDelegate());
```

### 3. Report an incoming call

```dart
await callkeep.reportNewIncomingCall(
  callId,
  CallkeepHandle.number('+15551234567'),
  displayName: 'John Doe',
  hasVideo: false,
);
```

### 4. Start an outgoing call

```dart
await callkeep.startCall(
  callId,
  CallkeepHandle.number('+15559876543'),
  displayNameOrContactIdentifier: 'Jane Doe',
  hasVideo: false,
);
```

### 5. Tear down

```dart
await callkeep.tearDown();
```

---

## API reference

### Flutter -> Platform

Use these methods to notify the platform about call state changes.

| Method | Description |
|---|---|
| `setUp(options)` | Register phone account, initialize notification channels |
| `tearDown()` | Hang up all calls and release resources |
| `reportNewIncomingCall(callId, handle, ...)` | Register an incoming call with the platform |
| `reportConnectingOutgoingCall(callId, handle, ...)` | Mark outgoing call as connecting |
| `reportConnectedOutgoingCall(callId, handle, ...)` | Mark outgoing call as connected |
| `reportUpdateCall(callId, ...)` | Update call metadata (display name, video, etc.) |
| `reportEndCall(callId, reason)` | Notify platform the call ended |
| `startCall(callId, handle, ...)` | Initiate an outgoing call |
| `answerCall(callId)` | Answer a call programmatically |
| `endCall(callId)` | End a call |
| `setHeld(callId, onHold)` | Put call on hold or resume |
| `setMuted(callId, muted)` | Mute or unmute |
| `setSpeaker(callId, on)` | Toggle speaker |
| `sendDTMF(callId, digit)` | Send a DTMF tone |

### Platform -> Flutter (`CallkeepDelegate`)

Implement `CallkeepDelegate` and pass it to `setDelegate` to receive platform events.

| Method | When it fires |
|---|---|
| `didPushIncomingCall(callId, handle, ..., error)` | Platform has registered the incoming call (or reports an error) |
| `performAnswerCall(callId)` | User answered from system UI |
| `performEndCall(callId)` | User ended from system UI or system terminated the call |
| `performStartCall(callId, handle, ...)` | User initiated outgoing call from system UI (e.g. Siri) |
| `continueStartCallIntent(callId, handle, ...)` | System confirmed outgoing call intent |
| `performSetHeld(callId, onHold)` | User toggled hold from system UI |
| `performSetMuted(callId, muted)` | User toggled mute from system UI |
| `performSendDTMF(callId, digit)` | User sent DTMF from system dial pad |
| `performSetSpeaker(callId, on)` | User toggled speaker from system UI |
| `didActivateAudioSession()` | System activated the audio session |
| `didDeactivateAudioSession()` | System deactivated the audio session |
| `didReset()` | System reset all call state |

`perform*` methods return `Future<bool>`. Return `false` to signal failure — the platform will
terminate the call.

---

## Android background modes

Android requires a running service to handle calls when the app is backgrounded. Two mutually
exclusive modes are supported; choose one per app.

### Push notification isolate (one-shot)

A short-lived Flutter isolate spawned when an FCM (or other) push arrives. Exits after the call
ends.

```dart
// Register the isolate entry-point once (main isolate, before background activity):
await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
    .initializeCallback(onPushNotificationCallback);

// From your FCM background handler:
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
      .reportNewIncomingCall(
    callId,
    CallkeepHandle.number(handle),
    displayName: displayName,
    hasVideo: hasVideo,
  );
}

// The isolate callback:
@pragma('vm:entry-point')
Future<void> onPushNotificationCallback(
  CallkeepPushNotificationSyncStatus status,
  CallkeepIncomingCallMetadata? metadata,
) async {
  await initializeDependencies();
  switch (status) {
    case CallkeepPushNotificationSyncStatus.synchronizeCallStatus:
      await backgroundCallManager.onStart();
    case CallkeepPushNotificationSyncStatus.releaseResources:
      await backgroundCallManager.close();
  }
}
```

### Signaling isolate (persistent)

A long-running Flutter isolate that maintains a permanent signaling connection (e.g. WebSocket).
Survives app background and device reboot (restarted by `ForegroundCallBootReceiver`).

```dart
// Configure (optional — sets the foreground service notification text):
await AndroidCallkeepServices.backgroundSignalingBootstrapService.setUp(
  androidNotificationName: 'My App Calls',
  androidNotificationDescription: 'Required to receive incoming calls',
);

// Register the isolate entry-point:
await AndroidCallkeepServices.backgroundSignalingBootstrapService
    .initializeCallback(onSignalingCallback);

// Start / stop from the main isolate:
await AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();
await AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService();

// The isolate callback:
@pragma('vm:entry-point')
Future<void> onSignalingCallback(
  CallkeepServiceStatus status,
  CallkeepIncomingCallMetadata? metadata,
) async {
  await initializeDependencies();
  await signalingManager.sync(status);
}
```

Inside either isolate, use `CallkeepBackgroundServiceDelegate` to receive answer/end events, and
`BackgroundSignalingService` / `BackgroundPushNotificationService` to report call outcomes back to
the platform.

---

## Android: SMS-triggered incoming call

Android only. Allows triggering an incoming call from a specially formatted SMS — useful when push
delivery is unreliable.

**Required permissions**: `RECEIVE_SMS`, `BROADCAST_SMS`

The SMS must start with the prefix `<#> WEBTRIT:` (hard-coded security filter, do not change).
The rest of the message is parsed by a caller-supplied ICU-compatible regex that captures 4 groups
in order: `callId`, `handle`, `displayName`, `hasVideo`.

```dart
await callkeep.initializeSmsReception(
  messagePrefix: '<#> WEBTRIT:',
  regexPattern: r'your-regex-here',
);
```

Full regex specification: [`docs/sms_trigger_regex_requirements.md`](docs/sms_trigger_regex_requirements.md)

> SMS access is a sensitive Play Store permission. You must justify its use and ensure the regex
> only matches messages from your own backend.

---

## Required permissions

### Android (`AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
<!-- Optional: SMS fallback -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.BROADCAST_SMS" />
```

### iOS (`Info.plist` / Xcode capabilities)

- Enable **Push Notifications** capability
- Enable **Background Modes -> Voice over IP**

---

## Integration tests

The public API is covered by integration tests in
[`webtrit_callkeep/example/integration_test/`](webtrit_callkeep/example/integration_test/).

Tests cover: lifecycle, incoming/outgoing call scenarios, state machine (hold, mute, DTMF),
foreground service timing, background service paths (push + signaling), connection queries,
delegate edge cases, and stress/concurrency scenarios.

```bash
cd webtrit_callkeep/example
flutter test integration_test/<test_file>.dart
```

---

## Example app

[`webtrit_phone`](https://github.com/WebTrit/webtrit_phone) is a reference Flutter VoIP app that
demonstrates real-world usage of this plugin including signaling, media, background call handling,
and full foreground/background workflows.

---

## Resources

- [CallKit documentation (Apple)](https://developer.apple.com/documentation/callkit)
- [ConnectionService API (Android)](https://developer.android.com/reference/android/telecom/ConnectionService)
- [Android architecture docs](webtrit_callkeep_android/docs/architecture.md)

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming, commit message format, and PR
conventions.

## License

[MIT](LICENSE)
