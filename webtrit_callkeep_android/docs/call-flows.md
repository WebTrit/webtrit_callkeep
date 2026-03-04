# Call Flows

Step-by-step traces for every call scenario. For the entry points that initiate each flow,
see [call-triggers.md](call-triggers.md).

---

## Incoming Call — Signaling Service Path

The app has a persistent WebSocket connection via `SignalingIsolateService`. The Flutter background
isolate detects the incoming call and notifies Telecom.

```
Signaling server
    │
    │  WebSocket message
    ▼
Flutter background isolate (inside SignalingIsolateService)
    │
    │  Dart: PHostBackgroundSignalingIsolateApi.incomingCall(callId, handle, displayName, hasVideo)
    │  (Pigeon @HostApi → SignalingIsolateService.incomingCall)
    ▼
SignalingIsolateService.incomingCall()
    │
    │  PhoneConnectionService.startIncomingCall(context, metadata, onSuccess, onError)
    ▼
ConnectionManager.validateConnectionAddition()
    ├── DUPLICATE callId? → onError(callIdAlreadyExists)
    ├── Another incoming exists? → onError(filteredByDoNotDisturb / busy)
    └── OK → TelephonyUtils.addNewIncomingCall(metadata)
                    │
                    │  TelecomManager.addNewIncomingCall(phoneAccountHandle, extras)
                    ▼
        Android Telecom Framework
                    │
                    │  calls PhoneConnectionService.onCreateIncomingConnection()
                    ▼
        PhoneConnectionService (:callkeep_core)
            ├── PhoneConnection.createIncomingPhoneConnection()
            ├── connectionManager.addConnection(callId, connection)
            └── broadcast ConnectionPerform.ConnectionAdded
                    │
                    ▼
        PhoneConnection (STATE_RINGING)
            └── onShowIncomingCallUi() — system shows call UI

                    ──────── User sees incoming call ────────

        User taps Answer:
            PhoneConnection.onAnswer()
                ├── setActive()  (STATE_RINGING → STATE_ACTIVE)
                ├── audioManager.activate()
                └── broadcast ConnectionPerform.AnswerCall
                        │
                        ▼ (cross-process broadcast)
        ForegroundService (main process)
            ├── flutterDelegateApi.performAnswerCall(callId)
            └── flutterDelegateApi.didActivateAudioSession()
                        │
                        ▼ (Pigeon @FlutterApi)
        Flutter main isolate — call is now active

        User taps Decline:
            PhoneConnection.onDisconnect()
                ├── setDisconnected()
                └── broadcast ConnectionPerform.DeclineCall
                        │
                        ▼
        ForegroundService
            ├── connectionTracker.remove(callId)
            ├── flutterDelegateApi.performEndCall(callId)
            └── flutterDelegateApi.didDeactivateAudioSession()
```

---

## Incoming Call — Push Notification Path

The app is not in the foreground; a push notification (e.g., FCM) triggers the incoming call
handling.

```
Push notification (FCM)
    │
    │  App calls WebtritCallkeepAndroid.incomingCallPushNotificationService()
    │  or platform directly starts IncomingCallService via PendingIntent
    ▼
BackgroundPushNotificationIsolateBootstrapApi.reportNewIncomingCall()
    │
    │  PhoneConnectionService.startIncomingCall(context, metadata, ...)
    ▼
(same TelecomManager path as signaling — see above)
    │
    ▼
PhoneConnection (STATE_RINGING) — Telecom shows incoming call UI

IncomingCallService.onStartCommand(IC_INITIALIZE)
    ├── show high-priority IncomingCallNotificationBuilder notification
    └── DefaultIsolateLaunchPolicy.shouldLaunch()?
            ├── YES: FlutterIsolateHandler launches Flutter isolate
            │         isolate receives onNotificationSync callback
            └── NO:  skip (app is in foreground, main isolate handles it)

User taps "Answer" in notification:
    IncomingCallService.onStartCommand(NotificationAction.Answer)
        └── callLifecycleHandler.reportAnswerToConnectionService(metadata)
                └── PhoneConnectionService.startAnswerCall(metadata)
                        └── PhoneConnection.onAnswer() → (same path as above)

Answer confirmed (ConnectionPerform.AnswerCall received):
    IncomingCallService.performAnswerCall()
        └── callLifecycleHandler.flutterApi.performAnswer(callId)
                └── isolate notified

After call answered:
    IncomingCallService.release(IC_RELEASE_WITH_ANSWER)
        ├── incomingCallHandler.releaseIncomingCallNotification(answered=true)
        └── 2-second timeout → stopSelf()
```

---

## Outgoing Call

The Flutter app initiates a call via the Dart API.

```
Flutter main isolate
    │
    │  Dart: WebtritCallkeepAndroid.startCall(callId, handle, displayName, video, proximityEnabled)
    │  → PHostApi.startCall() → ForegroundService.startCall()
    ▼
ForegroundService.startCall()
    ├── outgoingCallbacksManager.put(callId, callback, timeout=5000ms)
    └── retryManager.run(callId, config=OUTGOING_RETRY_CONFIG)

        Attempt 1 (and subsequent):
        ├── if attempt > 1: TelephonyUtils.registerPhoneAccount()
        ├── PhoneConnectionService.startOutgoingCall(context, metadata)
        │       ├── isEmergencyNumber? → throw EmergencyNumberException
        │       ├── existing active call? → it.hungUp() first
        │       └── TelephonyUtils.placeOutgoingCall(uri, metadata)
        │               └── TelecomManager.placeCall(uri, extras)
        │
        └── On SecurityException(CALL_PHONE):
                └── CallPhoneSecurityRetryDecider.shouldRetry()?
                        ├── YES (attempt < 5): wait → retry
                        └── NO:  onFinalFailure → callback(SELF_MANAGED_PHONE_ACCOUNT_NOT_REGISTERED)

        Android Telecom Framework
            │
            │  PhoneConnectionService.onCreateOutgoingConnection()
            ▼
        PhoneConnection (STATE_DIALING)
            └── broadcast ConnectionPerform.ConnectionAdded

        Remote side answers:
            PhoneConnection.establish()
                ├── setActive()  (STATE_DIALING → STATE_ACTIVE)
                └── broadcast ConnectionPerform.OngoingCall
                        │
                        ▼
        ForegroundService
            ├── retryManager.cancel(callId)
            ├── outgoingCallbacksManager.invokeAndRemove(callId, Result.success(null))
            │       └── Flutter callback resolved: no error
            └── flutterDelegateApi.performStartCall(callId, handle, name, video)
                        │
                        ▼
        Flutter main isolate — call is active

        On failure (onCreateOutgoingConnectionFailed):
            broadcast ConnectionPerform.OutgoingFailure(FailureMetadata)
                │
                ▼
            ForegroundService.handleCSReportOutgoingFailure()
                ├── retryManager.cancel(callId)
                └── outgoingCallbacksManager.invokeAndRemove(callId, Result.success(error))

        On timeout (5000ms exceeded):
            outgoingCallbacksManager timeout fires
                ├── retryManager.cancel(callId)
                └── callback(TIMEOUT)
```

---

## Call End (from Flutter)

Flutter requests the call to end (user hangs up or call is programmatically terminated).

```
Flutter: WebtritCallkeepAndroid.reportEndCall(callId, displayName, reason)
    │
    ▼
ForegroundService.reportEndCall()
    └── PhoneConnectionService.startDeclineCall(metadata)
            └── startService(intent, action=DeclineCall)
                    └── PhoneConnectionServiceDispatcher.dispatch(DeclineCall, metadata)
                            └── connection.hungUp()
                                    ├── setDisconnected(DisconnectCause.LOCAL)
                                    └── broadcast ConnectionPerform.DeclineCall
                                            │
                                            ▼
                    ForegroundService.handleCSReportDeclineCall()
                        ├── connectionTracker.remove(callId)
                        ├── flutterDelegateApi.performEndCall(callId)
                        ├── flutterDelegateApi.didDeactivateAudioSession()
                        └── if lockscreen: ActivityHolder.finish()
```

---

## Audio Device Change

Flutter requests switching the audio output device.

```
Flutter: WebtritCallkeepAndroid.setAudioDevice(callId, device)
    │
    ▼
ForegroundService.setAudioDevice()
    └── PhoneConnectionService.setAudioDevice(context, metadata)
            └── startService(intent, action=AudioDeviceSet)
                    └── PhoneConnectionServiceDispatcher.dispatch(AudioDeviceSet, metadata)
                            └── connection.setAudioDevice(audioDevice)
                                    │
                                    │  API 34+: requestCallEndpointChange(endpoint)
                                    │  < API 34: audioManager.setSpeakerphoneOn(...)
                                    │
                                    └── onCallEndpointChanged() callback
                                            └── broadcast ConnectionPerform.AudioDeviceSet
                                                    │
                                                    ▼
                        ForegroundService.handleCSReportAudioDeviceSet()
                            └── flutterDelegateApi.performAudioDeviceSet(callId, device)
```

---

## Lock Screen Flow

When an incoming call arrives while the device is locked, the Activity must be configured to show
over the lock screen.

```
PhoneConnection.onShowIncomingCallUi()
    └── Telecom system shows full-screen intent notification

WebtritCallkeepPlugin.onStateChanged(ON_START)
    └── val hasActive = !ForegroundService.connectionTracker.isEmpty()
            ├── true:  activity.setShowWhenLocked(true) + setTurnScreenOn(true)
            └── false: activity.setShowWhenLocked(false) + setTurnScreenOn(false)
```

The flags are set dynamically (not in the manifest) and cleared on the next `ON_START` after the
call ends, avoiding them being permanently enabled.
