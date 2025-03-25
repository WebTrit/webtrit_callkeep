# Webtrit CallKeep

**Webtrit CallKeep** is a Flutter plugin that integrates native calling UI on iOS (CallKit) and Android (ConnectionService). It allows your VoIP/WebRTC app to display incoming/outgoing calls using the device’s system UI — even when your app is in the background or terminated.

> **Note**: CallKit is banned in the China App Store. For distribution in China, consider using an alternative mechanism such as standard local notifications with sound.

---

## ✅ Features

- **Incoming Call UI**\
  – On iOS, the system CallKit UI is shown when the app is locked or in the background. When the app is in the foreground, a Flutter-based incoming call screen is used instead.\
  – On Android, all incoming calls are displayed using a Flutter UI combined with the standard push notification interface — native ConnectionService UI is not used.

- **Background Handling**\
  – On iOS, background incoming calls are handled via CallKit and PushKit.\
  – On Android, background call handling is supported either via persistent signaling services or triggered by push notifications (e.g., FCM).

- **Incoming & Outgoing Calls**\
  – Full support for both call directions

- **Call Controls**\
  – Answer, decline, mute, hold, and dial pad (DTMF), with delegate event handling

---

## 📱 Platform Support

| Platform | Minimum |
| -------- | ------- |
| Android  | SDK 26+ |
| iOS      | iOS 11+ |

---

## 🔧 Installation

Add the plugin in your `pubspec.yaml`:

```yaml
dependencies:
  webtrit_callkeep: ^latest_version
```

---

## 🚀 Quick Start

### Initialization (Main Isolate)

```dart
await _callkeep.setUp(
  CallkeepOptions(
    ios: CallkeepIOSOptions(
      localizedName: context.read<PackageInfo>().appName,
      ringtoneSound: Assets.ringtones.incomingCall1,
      ringbackSound: Assets.ringtones.outgoingCall1,
      iconTemplateImageAssetName: Assets.callkeep.iosIconTemplateImage.path,
      maximumCallGroups: 13,
      maximumCallsPerCallGroup: 13,
      supportedHandleTypes: const {CallkeepHandleType.number},
    ),
    android: CallkeepAndroidOptions(
      ringtoneSound: Assets.ringtones.incomingCall1,
      ringbackSound: Assets.ringtones.outgoingCall1,
    ),
  ),
);
```

### Setting Delegates

The plugin allows you to register delegate classes to handle call-related events.

```dart
Callkeep().setDelegate(MyCallkeepDelegate());
Callkeep().setPushRegistryDelegate(MyPushRegistryDelegate()); // iOS only
```

- `setDelegate(...)`: Listen to call events (incoming, answered, ended, etc.) entirely on the Flutter side.
- `setPushRegistryDelegate(...)`: Handle PushKit VoIP push events on iOS only.

---

### Receiving Incoming Call

```dart
await Callkeep().reportNewIncomingCall(
  callId,
  CallkeepHandle.number('+123456789'),
  displayName: 'Caller Name',
  hasVideo: false,
);
```

### Starting Outgoing Call

```dart
await Callkeep().startCall(
  'outgoing-id',
  CallkeepHandle.number('+987654321'),
  displayNameOrContactIdentifier: 'Jane Doe',
  hasVideo: true,
);
```

---

## ⚙️ Android Background Modes

Webtrit CallKeep offers two modes to handle background call signaling in Android. Use the one that suits your scenario.

> 🔎 **Note**: Some Android classes use the `@Keep` annotation to prevent code shrinking and obfuscation. Ensure ProGuard/R8 rules preserve these classes.

---

### ♻️ 1. Push Notification Isolate (One-time)

Ideal for apps that **do not maintain a persistent connection** and instead rely on push notifications to trigger call events.

#### Initialize Callback

Before rendering your app, register the background handler:

```dart
await AndroidCallkeepServices.backgroundPushNotificationBootstrapService.initializeCallback(
  onPushNotificationCallback,
);
```

#### Triggering Incoming Call from FCM or Another Isolate

You can notify the plugin of a new incoming call from within a background handler (e.g., FCM):

```dart
@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
    appNotification.call.id,
    CallkeepHandle.number(appNotification.call.handle),
    displayName: appNotification.call.displayName,
    hasVideo: appNotification.call.hasVideo,
  );
}
```

#### ⭮️ Handling Events in Background

To manage resources and synchronize signaling from push notifications, implement a background callback like this:

```dart
@pragma('vm:entry-point')
Future<void> onPushNotificationCallback(CallkeepPushNotificationSyncStatus status) async {
  await _initializeDependencies();

  switch (status) {
    case CallkeepPushNotificationSyncStatus.synchronizeCallStatus:
      await _backgroundCallEventManager?.onStart();
      break;
    case CallkeepPushNotificationSyncStatus.releaseResources:
      await _backgroundCallEventManager?.close();
      break;
  }
}
```

This ensures your app can rehydrate or tear down background services based on the nature of the push event.

---

### 🔌 2. Background Signaling Isolate (Persistent)

Used for always-on signaling, even in the background.

#### Setup

```dart
await AndroidCallkeepServices.backgroundSignalingBootstrapService.setUp();
```

#### Start and stop the foreground signaling service

```dart
await AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();
await AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService();
```

#### Initialize callbacks for events (e.g., lifecycle)

```dart
await AndroidCallkeepServices.backgroundSignalingBootstrapService.initializeCallback(
  onStart: () => debugPrint('Foreground started'),
  onChangedLifecycle: (state) => debugPrint('Lifecycle: $state'),
);
```

---

### ⭮️ Handling Events in Background

Example delegate implementation:

```dart
class BackgroundIncomingCallEventManager implements CallkeepBackgroundServiceDelegate {
  @override
  void performServiceAnswerCall(String callId) async {
    if (!(await _signalingManager.hasNetworkConnection())) {
      throw Exception('No connection');
    }
    // Proceed with answering
  }

  @override
  void performServiceEndCall(String callId) async {
    await _signalingManager.declineCall(callId);
  }
}
```

---

## 📜 Required Permissions

Ensure the following permissions are declared:

### Android

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.MANAGE_OWN_CALLS`
- `android.permission.BIND_TELECOM_CONNECTION_SERVICE`

### iOS

- Enable **Push Notifications**
- Enable **Background Modes → Voice over IP**

---

## 📃 More Resources

- [Flutter Plugin Development Guide](https://docs.flutter.dev/development/packages-and-plugins/plugin-overview)
- [CallKit Documentation (Apple)](https://developer.apple.com/documentation/callkit)
- [ConnectionService API (Android)](https://developer.android.com/reference/android/telecom/ConnectionService)

---

## 🧑‍💻 Authors

Maintained by the [Webtrit](https://webtrit.com/) team.

---

## Contributing

We welcome contributions from the community! Please follow our contribution guidelines when submitting pull requests.

## License

This project is licensed under the [MIT License](LICENSE).

## Acknowledgments

This project is tested with [BrowserStack](https://www.browserstack.com/).