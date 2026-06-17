# Pigeon APIs

**Generated file**: `kotlin/com/webtrit/callkeep/Generated.kt` (DO NOT EDIT)

**Source**: `pigeons/callkeep.messages.dart`

**Regeneration**:

```dart
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```

Pigeon generates type-safe Kotlin/Dart bindings. There are two categories:

- **Host APIs** — Kotlin code that Dart calls into.
- **Flutter APIs** — Dart code that Kotlin calls into.

---

## Host APIs (Kotlin implements, Dart calls)

### `PHostApi`

Implemented by: `ForegroundService`

The primary call-control API. All call lifecycle operations from Dart arrive here.

| Method                                       | Description                                        |
|----------------------------------------------|----------------------------------------------------|
| `setUp(handle, ...)`                         | Register phone account, init notification channels |
| `tearDown()`                                 | Hang up all calls, clean up                        |
| `reportNewIncomingCall(callId, meta)`        | Register incoming call with Telecom                |
| `reportConnectingOutgoingCall(callId, meta)` | Mark outgoing call as connecting                   |
| `reportConnectedOutgoingCall(callId, meta)`  | Mark outgoing call as connected                    |
| `reportEndCall(callId)`                      | Force-end call from Dart side                      |
| `reportUpdateCall(callId, meta)`             | Update call metadata                               |
| `startCall(callId, meta)`                    | Initiate an outgoing call                          |
| `answerCall(callId)`                         | Answer incoming call                               |
| `endCall(callId)`                            | End a call                                         |
| `setMuted(callId, muted)`                    | Toggle mute                                        |
| `setHeld(callId, held)`                      | Toggle hold                                        |
| `setSpeaker(callId, on)`                     | Toggle speaker                                     |
| `setAudioDevice(callId, device)`             | Select audio device                                |
| `sendDTMF(callId, digit)`                    | Send DTMF tone                                     |
| `onDelegateSet()`                            | Dart signals it is ready to receive events         |

---

### `PHostActivityControlApi`

Implemented by: `ActivityControlApi`

| Method                     | Description                                            |
|----------------------------|--------------------------------------------------------|
| `showOverLockscreen(show)` | Toggle lock-screen overlay flag on the activity window |
| `wakeScreenOnShow(wake)`   | Toggle screen-wake flag                                |
| `sendToBackground()`       | Move activity to background                            |
| `isDeviceLocked()`         | Returns whether device is currently locked             |

---

### `PHostPermissionsApi`

Implemented by: `PermissionsApi`

| Method                                  | Description                                     |
|-----------------------------------------|-------------------------------------------------|
| `requestPermissions(permissions)`       | Request runtime permissions via the activity    |
| `checkPermissionsStatus(permissions)`   | Check which permissions are granted             |
| `getFullScreenIntentPermissionStatus()` | Check `USE_FULL_SCREEN_INTENT` status (API 34+) |
| `openFullScreenIntentSettings()`        | Navigate to FSI settings screen                 |
| `getBatteryMode()`                      | Return current battery optimization mode        |

---

### `PHostConnectionsApi`

Implemented by: `ConnectionsApi`

| Method                  | Description                                                  |
|-------------------------|--------------------------------------------------------------|
| `getConnection(callId)` | Return `CallMetadata` for a specific call                    |
| `getConnections()`      | Return all active `CallMetadata` records                     |
| `cleanConnections()`    | Clear all connections without hanging up (for tearDown race) |

---

### `PHostDiagnosticsApi`

Implemented by: `DiagnosticsApi`

| Method                  | Description                                           |
|-------------------------|-------------------------------------------------------|
| `getDiagnosticReport()` | Return a structured diagnostic snapshot for debugging |

---

### `PHostSoundApi`

Implemented by: `SoundApi`

| Method                      | Description                           |
|-----------------------------|---------------------------------------|
| `playRingbackSound(callId)` | Start ringback tone for outgoing call |
| `stopRingbackSound(callId)` | Stop ringback tone                    |

---

### `PHostBackgroundPushNotificationIsolateBootstrapApi`

Implemented by: `BackgroundPushNotificationIsolateBootstrapApi`

| Method                                       | Description                                         |
|----------------------------------------------|-----------------------------------------------------|
| `initializePushNotificationCallback(handle)` | Store Dart isolate entry-point                      |
| `configureSignalingService(config)`          | Persist service config                              |
| `reportNewIncomingCall(callId, meta)`        | Start `IncomingCallService` for push-triggered call |

---

### `SmsReceptionConfigBootstrapApi`

Configures the optional SMS-based incoming call trigger (`IncomingCallSmsTriggerReceiver`).

---

## Flutter APIs (Dart implements, Kotlin calls)

### `PDelegateFlutterApi`

Kotlin calls these methods on the Dart delegate to notify of call events.

| Method                                       | Description                           |
|----------------------------------------------|---------------------------------------|
| `performIncomingCall(callId, meta)`          | Incoming call arrived                 |
| `performAnswerCall(callId)`                  | Call was answered (from Telecom side) |
| `performEndCall(callId, reason)`             | Call ended                            |
| `performHoldCall(callId, onHold)`            | Call hold state changed               |
| `performMuteCall(callId, muted)`             | Mute state changed                    |
| `performSendDTMF(callId, digit)`             | DTMF tone                             |
| `performSetAudioDevice(callId, device)`      | Audio device selected                 |
| `performUpdateAudioDevices(callId, devices)` | Available audio devices changed       |
| `performConnecting(callId)`                  | Outgoing call connecting              |
| `performConnected(callId)`                   | Outgoing call connected               |
| `performOutgoingFailure(callId, info)`       | Outgoing call failed                  |
| `performIncomingFailure(callId, info)`       | Incoming call failed                  |

---

## Data Types (Pigeon-generated)

| Type                       | Description                                                         |
|----------------------------|---------------------------------------------------------------------|
| `PCallMetadata`            | Call details: display name, handle, video, audio device flags       |
| `PCallHandle`              | Phone number or SIP URI                                             |
| `PAudioDevice`             | Audio device descriptor (earpiece, speaker, Bluetooth, etc.)        |
| `PCallkeepConnectionState` | Telecom state enum: RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTED |
| `PFailureMetaData`         | Structured failure info (reason, code)                              |

---

## Related Components

- [foreground-service.md](foreground-service.md) — implements `PHostApi`
- [background-services.md](background-services.md) — bootstrap APIs wired to services
- [plugin.md](plugin.md) — registers all host APIs on `onAttachedToEngine`
