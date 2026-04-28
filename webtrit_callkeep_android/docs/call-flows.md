# Call Flows

End-to-end walkthroughs of the three main call scenarios.

---

## Incoming Call (Push Notification Path)

Triggered by an FCM message or a direct Dart call to `reportNewIncomingCall`.

```text
1.  FCM / Dart
        |
        v
2.  BackgroundPushNotificationIsolateBootstrapApi.reportNewIncomingCall(callId, meta)
        |   (Pigeon host call)
        v
3.  IncomingCallService  (starts as foreground service)
        |
        |  startService intent: NotifyPending
        v
4.  PhoneConnectionService.onStartCommand()
        |   ConnectionManager.addPendingForIncomingCall(callId, meta)
        v
5.  TelephonyUtils.addNewIncomingCall()  -->  Android Telecom
        |
        v
6.  Telecom --> PhoneConnectionService.onCreateIncomingConnection()
        |   PhoneConnection created (STATE_RINGING)
        |   broadcast: DidPushIncomingCall
        v
7.  ForegroundService.connectionServicePerformReceiver
        |   CallkeepCore.promote(callId, meta, state)
        |
        |   PDelegateFlutterApi.performIncomingCall(callId, meta)
        v
8.  Dart delegate receives performIncomingCall()
```

**Answer path (user taps answer in notification or UI):**

```text
9.  Dart calls PHostApi.answerCall(callId)
        |
        v
10. ForegroundService.answerCall()
        |   If PhoneConnection exists: CallkeepCore.startAnswerCall(callId)
        |   If not yet: ConnectionManager.reserveAnswer(callId)  (deferred)
        v
11. PhoneConnectionService.onStartCommand(AnswerCall)
        |   PhoneConnection.onAnswer()
        |   STATE_ACTIVE
        |   broadcast: AnswerCall
        v
12. ForegroundService receives AnswerCall broadcast
        |   MainProcessConnectionTracker.markAnswered(callId)
        |   PDelegateFlutterApi.performAnswerCall(callId)
        v
13. Dart delegate receives performAnswerCall()
```

**Deferred answer (user answered before PhoneConnection was created):**

At step 10, `reserveAnswer` stores the intent. At step 6 (`onCreateIncomingConnection`),
`ConnectionManager.consumeAnswer(callId)` returns true and `PhoneConnection.onAnswer()` is called
immediately — continuing from step 11 above.

---

## Incoming Call Rejected by Telecom (`callRejectedBySystem`)

Android does not allow two self-managed calls to be simultaneously in RINGING state. When a
second incoming call arrives while the first is still ringing, Telecom calls
`onCreateIncomingConnectionFailed` — **without** calling `onCreateIncomingConnection` first.

```text
1.  Dart / Push isolate calls reportNewIncomingCall(id2, meta)
        |  (call id1 is already in RINGING state)
        v
2.  TelephonyUtils.addNewIncomingCall()  -->  Android Telecom
        |
        v
3.  Telecom --> PhoneConnectionService.onCreateIncomingConnectionFailed(callId=id2)
        |   ConnectionManager.isPending(id2) == true  (was registered in step 2)
        |   broadcast: HungUp(id2)
        v
4.  ForegroundService.handleCSReportDeclineCall()
        |   pendingIncomingCallbacks[id2] exists (Pigeon response deferred)
        |   reply with PIncomingCallError(callRejectedBySystem)
        |   (performEndCall is NOT fired — call was never confirmed to Flutter)
        v
5.  Dart receives reportNewIncomingCall() result = callRejectedBySystem
```

**Key consequences**:

- `performEndCall` does **not** fire for `id2` — the call never existed in Telecom.
- The app must send the appropriate signaling (e.g. SIP BYE) to the server itself,
  without waiting for `performEndCall`.

**Scope**: this is standard AOSP behavior on Android 11+ (confirmed on stock Pixel 5).
Some vendors (Huawei, certain MediaTek OEMs) apply the same restriction even when the
first call is ACTIVE rather than RINGING.

---

## Outgoing Call

Triggered when the user initiates a call from the app UI.

```text
1.  Dart calls PHostApi.startCall(callId, meta)
        |
        v
2.  ForegroundService.startCall()
        |   CallkeepCore.startOutgoingCall(callId, meta)
        v
3.  startService intent --> PhoneConnectionService.onStartCommand()
        |   TelephonyUtils.addOutgoingCall()  -->  Android Telecom
        v
4.  Telecom --> PhoneConnectionService.onCreateOutgoingConnection()
        |   PhoneConnection created (STATE_DIALING)
        |   broadcast: OngoingCall
        v
5.  ForegroundService receives OngoingCall broadcast
        |   CallkeepCore.promote(callId, meta, STATE_DIALING)
        |   PDelegateFlutterApi.performConnecting(callId)
        v
6.  Dart delegate receives performConnecting()

--- Remote side answers ---

7.  App / signaling calls PHostApi.reportConnectedOutgoingCall(callId)
        |
        v
8.  ForegroundService.reportConnectedOutgoingCall()
        |   CallkeepCore.startEstablishCall(callId)
        v
9.  PhoneConnectionService: PhoneConnection.setActive() -> STATE_ACTIVE
        |   broadcast: AnswerCall
        v
10. ForegroundService receives AnswerCall broadcast
        |   MainProcessConnectionTracker.markAnswered(callId)
        |   PDelegateFlutterApi.performConnected(callId)
        v
11. Dart delegate receives performConnected()
```

---

## TearDown (All Calls Ended)

Triggered by Dart calling `tearDown()` — used on logout or app reset.

```text
1.  Dart calls PHostApi.tearDown()
        |
        v
2.  ForegroundService.tearDown()
        |   For each non-terminated call in MainProcessConnectionTracker:
        |     directNotifiedCallIds += callId
        |     PDelegateFlutterApi.performEndCall(callId, reason=LOCAL_HANGUP)
        |
        |   CallkeepCore.sendTearDownConnections()
        v
3.  startService intent (TearDownConnections) --> PhoneConnectionService
        |   For each PhoneConnection: hungUp()
        |     -> PhoneConnection.onDisconnect()
        |        broadcast: HungUp (but suppressed by directNotifiedCallIds)
        |
        |   For each pending callId with no PhoneConnection:
        |     broadcast: HungUp (synthesized)
        |
        |   broadcast: TearDownComplete
        v
4.  ForegroundService receives TearDownComplete
        |   Clean up tracker, stop services
        |   Complete Dart tearDown() response
        v
5.  Dart receives tearDown() success result
```

**Duplicate notification prevention**: `directNotifiedCallIds` ensures that when
`ForegroundService` receives the `HungUp` broadcast in step 3, it does not call
`performEndCall()` again for calls already notified in step 2.

---

## Hot-Restart Recovery

When Flutter hot-restarts (development only) the main process Flutter engine is re-attached, but
`:callkeep_core` retains live `PhoneConnection` objects.

```text
1.  Flutter hot-restart
        |
        v
2.  WebtritCallkeepPlugin.onAttachedToEngine()
        |
        v
3.  ForegroundService.syncConnectionState()
        |   CallkeepCore.sendSyncAudioState()    -> PhoneConnectionService re-emits audio state
        |   CallkeepCore.sendSyncConnectionState() -> PhoneConnectionService re-fires AnswerCall
        v
4.  ForegroundService broadcast handlers receive re-emitted events
        |   MainProcessConnectionTracker updated
        |   PDelegateFlutterApi notified with current state
        v
5.  Flutter UI reflects existing call state
```

---

## Related Components

- [foreground-service.md](foreground-service.md) — processes broadcast events in all flows
- [phone-connection-service.md](phone-connection-service.md) — Telecom integration in
  `:callkeep_core`
- [phone-connection.md](phone-connection.md) — per-call state transitions
- [callkeep-core.md](callkeep-core.md) — command dispatch from main process
- [ipc-broadcasting.md](ipc-broadcasting.md) — event transport between processes
