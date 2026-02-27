# Pigeon API Reference

All interfaces are defined in `pigeons/callkeep.messages.dart` and code-generated into:
- `lib/src/common/callkeep.pigeon.dart` (Dart)
- `android/src/main/kotlin/com/webtrit/callkeep/Generated.kt` (Kotlin)

To regenerate after modifying the source file:
```bash
flutter pub run pigeon --input pigeons/callkeep.messages.dart
```

Generated files are excluded from analysis (`analysis_options.yaml`). **Never edit them by hand.**

---

## `@HostApi` — Flutter calls Kotlin

### `PHostApi`

**Implemented by:** `ForegroundService`
**Registered in:** `onServiceConnected` (after `ForegroundService` bind)

| Method | Description |
|---|---|
| `isSetUp()` | Returns `true` always (service is running). |
| `setUp(options)` | Registers `PhoneAccount`, notification channels, stores ringtone/ringback paths. |
| `tearDown()` | Calls `PhoneConnectionService.tearDown()` to disconnect all calls. |
| `reportNewIncomingCall(callId, handle, displayName, hasVideo)` | Reports incoming call to Telecom. Returns `PIncomingCallError?`. |
| `reportConnectingOutgoingCall(callId)` | No-op on Android (iOS only). |
| `reportConnectedOutgoingCall(callId)` | Launches activity + calls `PhoneConnectionService.startEstablishCall()`. |
| `reportUpdateCall(callId, handle, displayName, hasVideo, proximityEnabled)` | Updates call metadata in `PhoneConnection`. |
| `reportEndCall(callId, displayName, reason)` | Calls `PhoneConnectionService.startDeclineCall()`. |
| `startCall(callId, handle, displayName, video, proximityEnabled)` | Initiates outgoing call with retry logic. Returns `PCallRequestError?`. |
| `answerCall(callId)` | Calls `PhoneConnectionService.startAnswerCall()`. Returns `PCallRequestError?`. |
| `endCall(callId)` | Calls `PhoneConnectionService.startHungUpCall()`. |
| `setHeld(callId, onHold)` | Calls `PhoneConnectionService.startHoldingCall()`. |
| `setMuted(callId, muted)` | Calls `PhoneConnectionService.startMutingCall()`. |
| `setSpeaker(callId, enabled)` | Calls `PhoneConnectionService.startSpeaker()`. |
| `setAudioDevice(callId, device)` | Calls `PhoneConnectionService.setAudioDevice()`. |
| `sendDTMF(callId, key)` | Calls `PhoneConnectionService.startSendDtmfCall()`. |
| `onDelegateSet()` | Triggers state restore for active connections (post hot restart). |

---

### `PHostBackgroundSignalingIsolateBootstrapApi`

**Implemented by:** `BackgroundSignalingIsolateBootstrapApi`
**Registered in:** `onAttachedToEngine`
**Purpose:** Configures and controls `SignalingIsolateService`.

| Method | Description |
|---|---|
| `initializeSignalingServiceCallback(callbackDispatcher, onSync)` | Stores callback handles in `StorageDelegate.SignalingService`. |
| `configureSignalingService(notificationName, notificationDescription)` | Stores notification text. |
| `startService()` | Sets enabled flag + calls `SignalingIsolateService.start()`. |
| `stopService()` | Clears enabled flag + calls `SignalingIsolateService.stop()`. |

---

### `PHostBackgroundSignalingIsolateApi`

**Implemented by:** `SignalingIsolateService`
**Registered in:** `onAttachedToService` (when engine attaches to `SignalingIsolateService`)
**Purpose:** Called from Flutter signaling isolate to trigger call actions.

| Method | Description |
|---|---|
| `incomingCall(callId, handle, displayName, hasVideo)` | Forwards incoming call to `PhoneConnectionService.startIncomingCall()`. |
| `endCall(callId)` | Calls `PhoneConnectionService.startHungUpCall()`. |
| `endAllCalls()` | Calls `PhoneConnectionService.tearDown()`. |

---

### `PHostBackgroundPushNotificationIsolateBootstrapApi`

**Implemented by:** `BackgroundPushNotificationIsolateBootstrapApi`
**Registered in:** `onAttachedToEngine`
**Purpose:** Configures push-notification-based incoming calls.

| Method | Description |
|---|---|
| `initializePushNotificationCallback(callbackDispatcher, onNotificationSync)` | Stores callback handles in `StorageDelegate.IncomingCallService`. |
| `configureSignalingService(launchBackgroundIsolateEvenIfAppIsOpen)` | Stores isolate launch policy. |
| `reportNewIncomingCall(callId, handle, displayName, hasVideo)` | Directly calls `PhoneConnectionService.startIncomingCall()`. Returns `PIncomingCallError?`. |

---

### `PHostBackgroundPushNotificationIsolateApi`

**Implemented by:** `CallLifecycleHandler` (from `IncomingCallService`)
**Registered in:** `onAttachedToService` (when engine attaches to `IncomingCallService`)

| Method | Description |
|---|---|
| `endCall(callId)` | Calls `PhoneConnectionService.startHungUpCall()`. |
| `endAllCalls()` | Calls `PhoneConnectionService.tearDown()`. |

---

### `PHostPermissionsApi`

**Implemented by:** `PermissionsApi`
**Registered in:** `onAttachedToEngine`

| Method | Description |
|---|---|
| `getFullScreenIntentPermissionStatus()` | Returns `GRANTED`/`DENIED` for `USE_FULL_SCREEN_INTENT`. |
| `openFullScreenIntentSettings()` | Opens system settings for full-screen intent. |
| `openSettings()` | Opens app's system settings page. |
| `getBatteryMode()` | Returns `unrestricted`/`optimized`/`restricted`/`unknown`. |
| `requestPermissions(permissions)` | Requests runtime permissions; waits up to 20 s for user response. |
| `checkPermissionsStatus(permissions)` | Checks current permission status without prompting. |

---

### `PHostDiagnosticsApi`

**Implemented by:** `DiagnosticsApi`

| Method | Description |
|---|---|
| `getDiagnosticReport()` | Returns a `Map<String, Any?>` with Telecom state, failed calls, service status, etc. |

---

### `PHostSoundApi`

**Implemented by:** `SoundApi`

| Method | Description |
|---|---|
| `playRingbackSound()` | Plays outgoing ringback tone (stored path from `StorageDelegate`). |
| `stopRingbackSound()` | Stops ringback. |

---

### `PHostConnectionsApi`

**Implemented by:** `ConnectionsApi`

| Method | Description |
|---|---|
| `getConnection(callId)` | Returns `PCallkeepConnection?` from `ForegroundService.connectionTracker`. |
| `getConnections()` | Returns all tracked connections. |
| `cleanConnections()` | Calls `PhoneConnectionService.tearDown()` and clears tracker. |
| `updateActivitySignalingStatus(status)` | Broadcasts new `SignalingStatus` via `SignalingStatusBroadcaster`. |

---

### `PHostActivityControlApi`

**Implemented by:** `ActivityControlApi`
**Registered in:** `onAttachedToActivity` (requires `Activity` reference)

| Method | Description |
|---|---|
| `showOverLockscreen(enable)` | Sets `setShowWhenLocked` flag on the Activity. |
| `wakeScreenOnShow(enable)` | Sets `setTurnScreenOn` flag on the Activity. |
| `sendToBackground()` | Calls `activity.moveTaskToBack(true)`. Returns success bool. |
| `isDeviceLocked()` | Returns `true` if keyguard is active. |

---

### `PHostSmsReceptionConfigApi`

**Implemented by:** `SmsReceptionConfigBootstrapApi`
**Registered in:** `onAttachedToEngine`

| Method | Description |
|---|---|
| `initializeSmsReception(messagePrefix, regexPattern)` | Validates regex, then stores prefix and pattern in `StorageDelegate`. |

---

## `@FlutterApi` — Kotlin calls Flutter

### `PDelegateFlutterApi`

**Received by:** `_CallkeepDelegateRelay` (via `WebtritCallkeepAndroid.setDelegate`)
**Called from:** `ForegroundService`

| Method | Trigger |
|---|---|
| `continueStartCallIntent(handle, displayName, video)` | System `Intent.ACTION_CALL` received (for system-initiated calls). |
| `didPushIncomingCall(handle, displayName, video, callId, error?)` | `PhoneConnection.onShowIncomingCallUi()` in `:callkeep_core`. |
| `performStartCall(callId, handle, displayName, video)` | Outgoing call active (ConnectionPerform.OngoingCall). |
| `performAnswerCall(callId)` | User answered call (ConnectionPerform.AnswerCall). |
| `performEndCall(callId)` | Call ended (ConnectionPerform.DeclineCall / HungUp). |
| `performSetHeld(callId, onHold)` | Hold state changed. |
| `performSetMuted(callId, muted)` | Mute state changed. |
| `performSendDTMF(callId, key)` | DTMF sent. |
| `performAudioDeviceSet(callId, device)` | Active audio device changed. |
| `performAudioDevicesUpdate(callId, devices)` | Available device list changed. |
| `didActivateAudioSession()` | Call answered; audio is live. |
| `didDeactivateAudioSession()` | Call ended; audio released. |
| `didReset()` | (Not used on Android currently.) |

---

### `PDelegateBackgroundRegisterFlutterApi`

**Received by:** `_BackgroundServiceDelegate` in `_isolatePluginCallbackDispatcher`
**Called from:** `SignalingIsolateService` and `IncomingCallService`

| Method | Trigger |
|---|---|
| `onWakeUpBackgroundHandler(callbackHandle, status, callData?)` | Activity lifecycle or signaling status changed; wakes up signaling isolate. |
| `onApplicationStatusChanged(callbackHandle, status)` | Application lifecycle status delivered to signaling isolate. |
| `onNotificationSync(callbackHandle, status, callData?)` | Push notification isolate sync status delivered. |

---

### `PDelegateBackgroundServiceFlutterApi`

**Received by:** Push isolate / signaling isolate
**Called from:** `IncomingCallService` and `SignalingIsolateService`

| Method | Trigger |
|---|---|
| `performAnswerCall(callId)` | User answered from notification while isolate is running. |
| `performEndCall(callId)` | User declined from notification while isolate is running. |

---

### `PDelegateLogsFlutterApi`

**Received by:** `_LogsDelegateRelay` (via `WebtritCallkeepAndroid.setLogsDelegate`)
**Called from:** `Log` singleton (native side log forwarder)

| Method | Description |
|---|---|
| `onLog(type, tag, message)` | Native log event forwarded to Dart. |

---

### `PPushRegistryDelegateFlutterApi`

| Method | Description |
|---|---|
| `didUpdatePushTokenForPushTypeVoIP(token?)` | VoIP push token updated. On Android this is a no-op token path (FCM handles it). |

---

### `PDelegateSmsReceiverFlutterApi`

| Method | Description |
|---|---|
| `onSmsReceived(text)` | Matching SMS received by `IncomingCallSmsTriggerReceiver`. |

---

## Type Mapping

Pigeon types (`P*`) are mapped to platform-interface types (`Callkeep*`) by extension functions in `lib/src/common/converters.dart`.

| Pigeon type | Platform-interface type |
|---|---|
| `PHandle` | `CallkeepHandle` |
| `PAudioDevice` | `CallkeepAudioDevice` |
| `PIncomingCallErrorEnum` | `CallkeepIncomingCallError` |
| `PCallRequestErrorEnum` | `CallkeepCallRequestError` |
| `PEndCallReasonEnum` | `CallkeepEndCallReason` |
| `PCallkeepConnection` | `CallkeepConnection` |
| `PCallkeepServiceStatus` | `CallkeepServiceStatus` |
| `PCallkeepSignalingStatus` | `CallkeepSignalingStatus` |
| `PCallkeepAndroidBatteryMode` | `CallkeepAndroidBatteryMode` |
| `PSpecialPermissionStatusTypeEnum` | `CallkeepSpecialPermissionStatus` |
