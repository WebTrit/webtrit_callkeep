import 'dart:async';
import 'dart:isolate' show SendPort;
import 'dart:ui' show IsolateNameServer;

import 'package:flutter/foundation.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

// onStartForegroundService registers the IsolateNameServer command port and
// routes signaling commands (incomingCall / endCall / endCalls) to the
// BackgroundSignalingService Pigeon API.  Importing it here ensures the
// function is included in the test APK binary so that
// PluginUtilities.getCallbackFromHandle can resolve it in the background
// engine, and so that initializeCallback can store a valid handle in
// SharedPreferences before the service is started.
import 'package:webtrit_callkeep_example/isolates.dart' show onStartForegroundService, signalingServiceCommandPortName;

// Reuse the port name constant from isolates.dart so tests break at compile
// time rather than silently if the string is ever renamed.
const _signalingTestPortName = signalingServiceCommandPortName;

// ---------------------------------------------------------------------------
// Background services integration tests
//
// These tests mirror the background work performed by the Dart services in
// webtrit_phone/lib/features/call/services:
//
//   PushNotificationIsolateManager  — handles incoming calls arriving via FCM
//     push notifications (device is locked / app killed). Registered in the
//     callkeep stack as BackgroundPushNotificationService.
//
//   SignalingForegroundIsolateManager — keeps a SIP WebSocket alive while
//     the app is backgrounded but the process is still running. Registered
//     as BackgroundSignalingService.
//
// The tests exercise the *callkeep layer* side of those services:
//   - Call registration / deduplication between isolate and main process.
//   - performEndCall / performAnswerCall delegate routing.
//   - endCall / endCalls clean-up triggered by signaling events
//     (_onHangupCall, _onSignalingError, _onNoActiveLines, _onUnregistered).
//   - Lifecycle management: startService / stopService without crash.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const _options = CallkeepOptions(
  ios: CallkeepIOSOptions(
    localizedName: 'BG Service Tests',
    maximumCallGroups: 2,
    maximumCallsPerCallGroup: 1,
    supportedHandleTypes: {CallkeepHandleType.number},
  ),
  android: CallkeepAndroidOptions(),
);

const _handle1 = CallkeepHandle.number('380001000000');
const _handle2 = CallkeepHandle.number('380001000001');

var _idCounter = 0;
String _nextId() => 'bg-${_idCounter++}';

// ---------------------------------------------------------------------------
// Recording delegate
// ---------------------------------------------------------------------------

class _RecordingDelegate implements CallkeepDelegate {
  final List<String> answerCallIds = [];
  final List<String> endCallIds = [];

  void Function(String callId)? onPerformAnswerCall;
  void Function(String callId)? onPerformEndCall;

  @override
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video) {}

  @override
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  ) {}

  @override
  void didActivateAudioSession() {}

  @override
  void didDeactivateAudioSession() {}

  @override
  void didReset() {}

  @override
  Future<bool> performStartCall(String callId, CallkeepHandle handle, String? displayName, bool video) =>
      Future.value(true);

  @override
  Future<bool> performAnswerCall(String callId) {
    answerCallIds.add(callId);
    onPerformAnswerCall?.call(callId);
    return Future.value(true);
  }

  @override
  Future<bool> performEndCall(String callId) {
    endCallIds.add(callId);
    onPerformEndCall?.call(callId);
    return Future.value(true);
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) => Future.value(true);

  @override
  Future<bool> performSetMuted(String callId, bool muted) => Future.value(true);

  @override
  Future<bool> performSendDTMF(String callId, String key) => Future.value(true);

  @override
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device) => Future.value(true);

  @override
  Future<bool> performAudioDevicesUpdate(String callId, List<CallkeepAudioDevice> devices) => Future.value(true);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Future<T> _waitFor<T>(Future<T> future, {String label = 'callback'}) {
  return future.timeout(
    const Duration(seconds: 10),
    onTimeout: () => throw TimeoutException('$label did not fire within timeout'),
  );
}

Future<CallkeepConnection?> _waitForConnection(
  String callId, {
  Duration timeout = const Duration(seconds: 5),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    final conn = await CallkeepConnections().getConnection(callId);
    if (conn != null) return conn;
    await Future.delayed(const Duration(milliseconds: 100));
  }
  return null;
}

// ---------------------------------------------------------------------------
// Signaling-isolate command helpers
//
// Starts the SignalingIsolateService and waits until onStartForegroundService
// has registered its command port.  Triggering updateActivitySignalingStatus
// fires SignalingIsolateService.synchronizeSignalingIsolate() which calls
// onWakeUpBackgroundHandler in the background Dart isolate — that invokes
// onStartForegroundService, which registers the IsolateNameServer port.
// ---------------------------------------------------------------------------

Future<void> _startSignalingServiceAndAwaitPort() async {
  IsolateNameServer.removePortNameMapping(_signalingTestPortName);
  // Store valid CALLBACK_DISPATCHER and ON_SYNC_HANDLER handles in
  // SharedPreferences.  These are required by FlutterEngineHelper to start
  // the background Dart engine and by synchronizeSignalingIsolate to invoke
  // onStartForegroundService.  bootstrap.dart normally does this during app
  // startup, but integration tests run with the test file as the Dart entry
  // point so bootstrap() is never called.
  await AndroidCallkeepServices.backgroundSignalingBootstrapService.initializeCallback(onStartForegroundService);
  await AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();

  final deadline = DateTime.now().add(const Duration(seconds: 10));
  while (DateTime.now().isBefore(deadline)) {
    await Future.delayed(const Duration(milliseconds: 300));
    try {
      await CallkeepConnections().updateActivitySignalingStatus(CallkeepSignalingStatus.disconnect);
    } catch (_) {}
    await Future.delayed(const Duration(milliseconds: 200));
    if (IsolateNameServer.lookupPortByName(_signalingTestPortName) != null) return;
  }
  throw TimeoutException('Background signaling isolate did not register command port within 10s');
}

void _sendToSignalingIsolate(Map<String, dynamic> message) {
  final SendPort? port = IsolateNameServer.lookupPortByName(_signalingTestPortName);
  if (port == null) throw StateError('Signaling test port not registered');
  port.send(message);
}

Future<void> _signalingIncomingCall(
  String callId,
  CallkeepHandle handle, {
  String? displayName,
  bool hasVideo = false,
}) async {
  _sendToSignalingIsolate({
    'action': 'incomingCall',
    'callId': callId,
    'handleValue': handle.value,
    'handleType': handle.type.name,
    'displayName': displayName,
    'hasVideo': hasVideo,
  });
}

Future<void> _signalingEndCall(String callId) async {
  _sendToSignalingIsolate({'action': 'endCall', 'callId': callId});
}

Future<void> _signalingEndCalls() async {
  _sendToSignalingIsolate({'action': 'endCalls'});
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late Callkeep callkeep;
  late _RecordingDelegate delegate;
  var globalTearDownNeeded = true;

  setUp(() async {
    globalTearDownNeeded = true;
    callkeep = Callkeep();
    delegate = _RecordingDelegate();
    await callkeep.setUp(_options);
    callkeep.setDelegate(delegate);
  });

  tearDown(() async {
    callkeep.setDelegate(null);
    if (globalTearDownNeeded) {
      try {
        await callkeep.tearDown().timeout(const Duration(seconds: 15));
      } catch (_) {}
    }
    await Future.delayed(const Duration(milliseconds: 300));
  });

  // =========================================================================
  // PUSH NOTIFICATION BACKGROUND SERVICE
  //
  // Simulates PushNotificationIsolateManager running in a background isolate:
  //   FCM arrives → isolate starts → SignalingManager connects →
  //   incoming call SIP offer → reportNewIncomingCall (push path) →
  //   user acts → SIP BYE → endCall (push path) → isolate shuts down
  // =========================================================================

  group('push notification background service (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    // -----------------------------------------------------------------------
    // Registration and deduplication
    //
    // Scenario: push isolate calls reportNewIncomingCall first; then the main
    // process CallBloc (after receiving the signaling offer) also calls it.
    // The second report must be recognised as a duplicate, not a new call.
    // Matches: IsolateManager.launchSignaling → push path registration,
    //          CallBloc._onCallPushEventIncoming vs _onCallSignalingEventIncoming
    // -----------------------------------------------------------------------

    testWidgets('push-path report then main-process report returns callIdAlreadyExists', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Alice');

      final err = await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Alice');

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExists,
        reason: 'main-process report after push-path registration must return callIdAlreadyExists',
      );
    });

    testWidgets('push-path duplicate report returns callIdAlreadyExists', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Bob');

      final err = await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Bob');

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExists,
        reason: 'second push-path report for the same callId must be rejected',
      );
    });

    testWidgets('concurrent push-path spam — exactly one succeeds', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();
      final futures = List.generate(
        4,
        (_) => AndroidCallkeepServices.backgroundPushNotificationBootstrapService
            .reportNewIncomingCall(id, _handle1, displayName: 'Charlie'),
      );
      final results = await Future.wait(futures);
      final successes = results.where((e) => e == null).length;

      expect(successes, 1, reason: 'exactly one concurrent push report must succeed');
    });

    // -----------------------------------------------------------------------
    // performEndCall routing for a push-path call
    //
    // Scenario: signaling receives SIP BYE while the push path registered the
    // call.  The Telecom connection is ended which fires performEndCall on the
    // main delegate.
    //
    // Note: BackgroundPushNotificationService.endCall() requires the push
    // notification isolate (IncomingCallService) to be running — that service
    // is only started by a real FCM push and cannot be started in an
    // integration test.  We therefore end the call via the main-process
    // callkeep.endCall() API, which exercises the same Telecom tear-down path
    // and results in the same performEndCall callback.
    // Matches: IsolateManager._onHangupCall → endCallOnService(callId)
    // -----------------------------------------------------------------------

    testWidgets('push-path call endCall fires performEndCall on main delegate', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Dave');

      final latch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      // Use the main-process API: IncomingCallService (push isolate) is not
      // available without a real FCM push, so we end via callkeep directly.
      await callkeep.endCall(id);

      final ended = await _waitFor(latch.future, label: 'performEndCall for push-path call');
      expect(ended, id);
    });

    // -----------------------------------------------------------------------
    // endCalls — all-calls cleanup
    //
    // Scenario: signaling error / unregistered event while multiple calls are
    // active. IsolateManager._onSignalingError / _onUnregistered calls
    // endCallsOnService() which must trigger performEndCall for every active
    // call.
    // Matches: IsolateManager._onSignalingError → endCallsOnService()
    //          IsolateManager._onUnregistered   → endCallsOnService()
    // -----------------------------------------------------------------------

    // Note: BackgroundPushNotificationService.endCalls() requires
    // IncomingCallService (push isolate), which is only started by FCM.
    // We use callkeep.endCall() per call instead to end Telecom connections
    // and verify performEndCall fires for each one.
    testWidgets('push-path endCalls fires performEndCall for every active call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id1 = _nextId();
      final id2 = _nextId();

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id1, _handle1, displayName: 'Eve');
      // Wait for id1 to be promoted (DidPushIncomingCall received) before
      // adding id2. This ensures Telecom sees id1 in RINGING state first.
      await _waitForConnection(id1);

      // Register the callback before reporting id2. Telecom may call
      // onCreateIncomingConnectionFailed for id2 (BUSY — id1 is RINGING),
      // which fires performEndCall immediately via the HungUp broadcast path.
      // Registering here ensures that early firing is captured even if
      // _waitForConnection(id2) times out before endCall(id2) is called.
      final endedIds = <String>[];
      final allDone = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if ({id1, id2}.contains(cid) && !endedIds.contains(cid)) {
          endedIds.add(cid);
          if (endedIds.length == 2 && !allDone.isCompleted) allDone.complete();
        }
      };

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id2, _handle2, displayName: 'Frank');

      // On OEM devices that reject concurrent incoming calls, id2 is never
      // promoted (no DidPushIncomingCall). Skip rather than waiting the full
      // _waitForConnection timeout on every run on such devices.
      final conn2 = await _waitForConnection(id2);
      if (conn2 == null) {
        markTestSkipped('device does not support concurrent incoming calls');
        return;
      }

      await callkeep.endCall(id1);
      await callkeep.endCall(id2);

      await _waitFor(allDone.future, label: 'both performEndCall for push-path calls');
      expect(endedIds, containsAll([id1, id2]));
    });

    // -----------------------------------------------------------------------
    // Push call answered before main process arrives
    //
    // Scenario: user answers on the lock screen (from push notification UI).
    // The push isolate answers the call (hasAnswered = true). When CallBloc
    // starts and calls reportNewIncomingCall, it must receive
    // callIdAlreadyExistsAndAnswered so it shows the in-call UI directly
    // instead of treating it as a generic collision.
    // Matches: PushNotificationIsolateManager push-answer path,
    //          CallBloc._onCallPushEventIncoming error handling
    // -----------------------------------------------------------------------

    testWidgets('push-path call answered → main-process report returns callIdAlreadyExistsAndAnswered',
        (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Grace');
      // Wait for the call to be promoted before answering. answerCall() works
      // via the deferred-answer path when the call is still pending, but
      // _waitForConnection guarantees the PhoneConnection exists so the answer
      // goes through the direct path (no race with onCreateIncomingConnection).
      await _waitForConnection(id);

      // Answer from the main process (simulates push isolate answering the call)
      final answerLatch = Completer<String>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete(cid);
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Main-process CallBloc arrives late with its own reportNewIncomingCall
      final err = await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Grace');

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered,
        reason: 'answered call must return callIdAlreadyExistsAndAnswered so CallBloc shows in-call UI',
      );
    });

    // -----------------------------------------------------------------------
    // Transfer-back: same callId reused after the previous call ends
    //
    // Scenario: after a blind transfer the call returns to the originating
    // device with the same callId.  The stale STATE_DISCONNECTED connection
    // left in ConnectionManager must be treated as absent so the new
    // incoming call registers successfully with Telecom.
    // -----------------------------------------------------------------------

    testWidgets('after push service ends a call, re-reporting same id succeeds (transfer-back)',
        (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      // First call: register → end → wait for delegate notification.
      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Hank');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall for first call');

      // Transfer-back: new incoming call reusing the same callId must succeed.
      final err = await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Hank');

      expect(
        err,
        isNull,
        reason: 'transfer-back reusing a terminated callId must be accepted',
      );

      // Cleanup: end the re-registered call.
      final endLatch2 = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch2.isCompleted) endLatch2.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch2.future, label: 'performEndCall for transfer-back call');
    });

    // -----------------------------------------------------------------------
    // tearDown while push-path calls are active
    //
    // Scenario: app tearDown (e.g. logout) is called while push isolate has
    // active calls. The main callkeep tearDown must still fire performEndCall
    // for each call regardless of which service path registered them.
    // Matches: CallBloc.close() → callkeep.tearDown()
    // -----------------------------------------------------------------------

    testWidgets('callkeep tearDown fires performEndCall for push-path active call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = _nextId();

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Irene');

      // Wait for the push-path call to propagate from :callkeep_core to the
      // main-process connection tracker via IPC before calling tearDown().
      // Without this, tearDown() may call getAll() before the call is visible
      // in the main process and skip the performEndCall dispatch.
      await _waitForConnection(id);

      final latch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await callkeep.tearDown();
      await _waitFor(latch.future, label: 'performEndCall on tearDown');

      expect(delegate.endCallIds.where((c) => c == id).length, 1);
    });
  });

  // =========================================================================
  // BACKGROUND SIGNALING SERVICE
  //
  // Simulates SignalingForegroundIsolateManager running while the app process
  // is alive but the UI is backgrounded:
  //   app backgrounded → handleLifecycleStatus(background, notConnected) →
  //   SignalingManager connects → incoming SIP offer →
  //   BackgroundSignalingService.incomingCall → native call created →
  //   user acts or SIP BYE → BackgroundSignalingService.endCall →
  //   performEndCall on main delegate
  // =========================================================================

  group('background signaling service (Android only)', () {
    setUp(() async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
      await _startSignalingServiceAndAwaitPort();
    });

    tearDown(() async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
      IsolateNameServer.removePortNameMapping(_signalingTestPortName);
      try {
        await AndroidCallkeepServices.backgroundSignalingBootstrapService
            .stopService()
            .timeout(const Duration(seconds: 5));
      } catch (_) {}
    });
    // -----------------------------------------------------------------------
    // Incoming call via signaling service
    //
    // Scenario: app is backgrounded, SignalingForegroundIsolateManager receives
    // a SIP offer and calls BackgroundSignalingService.incomingCall.
    // The main process must later detect the call as already existing.
    // Matches: SignalingForegroundIsolateManager._onIncomingCall →
    //          BackgroundSignalingService.incomingCall
    // -----------------------------------------------------------------------

    testWidgets('signaling service incomingCall creates a call recognised by main process', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await _signalingIncomingCall(id, _handle1, displayName: 'Jack');
      await Future.delayed(const Duration(milliseconds: 400));

      final err = await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Jack');

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExists,
        reason: 'main-process report after signaling-service registration must return callIdAlreadyExists',
      );
    });

    testWidgets('signaling service incomingCall duplicate returns callIdAlreadyExists', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await _signalingIncomingCall(id, _handle1, displayName: 'Kate');
      await Future.delayed(const Duration(milliseconds: 400));

      final err2 = await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Kate');
      expect(err2, CallkeepIncomingCallError.callIdAlreadyExists);
    });

    // -----------------------------------------------------------------------
    // endCall via signaling service
    //
    // Scenario: while backgrounded, SignalingForegroundIsolateManager receives
    // a SIP BYE and calls BackgroundSignalingService.endCall. This must
    // trigger performEndCall on the main delegate so CallBloc can update state.
    // Matches: IsolateManager._onHangupCall → endCallOnService(callId) →
    //          BackgroundSignalingService.endCall
    // -----------------------------------------------------------------------

    testWidgets('signaling service endCall fires performEndCall on main delegate', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await _signalingIncomingCall(id, _handle1, displayName: 'Leo');
      await Future.delayed(const Duration(milliseconds: 400));

      final latch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await _signalingEndCall(id);

      final ended = await _waitFor(latch.future, label: 'performEndCall via signaling service');
      expect(ended, id);
    });

    testWidgets('signaling service endCall fires performEndCall exactly once', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await _signalingIncomingCall(id, _handle1, displayName: 'Mia');
      await Future.delayed(const Duration(milliseconds: 400));

      final latch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await _signalingEndCall(id);
      await _waitFor(latch.future, label: 'performEndCall');

      expect(
        delegate.endCallIds.where((c) => c == id).length,
        1,
        reason: 'signaling service endCall must fire performEndCall exactly once',
      );
    });

    // -----------------------------------------------------------------------
    // endCalls — signaling error / unregistered cleanup
    //
    // Scenario: signaling WebSocket drops while backgrounded.
    // SignalingForegroundIsolateManager._onSignalingError calls
    // BackgroundSignalingService.endCalls, which must fire performEndCall
    // for every active call so CallBloc can clean up all active lines.
    // Matches: IsolateManager._onSignalingError  → endCallsOnService()
    //          IsolateManager._onNoActiveLines   → endCallsOnService()
    //          IsolateManager._onUnregistered    → endCallsOnService()
    // -----------------------------------------------------------------------

    testWidgets('signaling service endCalls fires performEndCall for every active call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id1 = _nextId();
      final id2 = _nextId();

      // Register the listener before creating calls so that an early
      // performEndCall for id2 is captured on devices that reject concurrent
      // incoming calls (id2 gets onCreateIncomingConnectionFailed while id1 is
      // still RINGING).  Without this, the callback fires during the delay
      // below and the test misses it.
      final endedIds = <String>[];
      final allDone = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if ({id1, id2}.contains(cid) && !endedIds.contains(cid)) {
          endedIds.add(cid);
          if (endedIds.length == 2 && !allDone.isCompleted) allDone.complete();
        }
      };

      await _signalingIncomingCall(id1, _handle1, displayName: 'Nick');
      await Future.delayed(const Duration(milliseconds: 200));
      await _signalingIncomingCall(id2, _handle2, displayName: 'Olivia');
      await Future.delayed(const Duration(milliseconds: 400));

      await _signalingEndCalls();

      await _waitFor(allDone.future, label: 'both performEndCall via signaling service endCalls');
      expect(endedIds, containsAll([id1, id2]));
    });

    testWidgets('signaling service endCalls with no active calls does not fire performEndCall', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      // No calls registered — endCalls must complete silently.
      await _signalingEndCalls();
      await Future.delayed(const Duration(milliseconds: 300));

      expect(delegate.endCallIds, isEmpty);
    });

    // -----------------------------------------------------------------------
    // Signaling service call answered before main process arrives
    //
    // Scenario: user answers the call from the notification shade while the
    // app is backgrounded. The signaling service path sets hasAnswered = true.
    // When the app comes to foreground and CallBloc calls reportNewIncomingCall,
    // it must receive callIdAlreadyExistsAndAnswered.
    // Matches: SignalingForegroundIsolateManager + CallBloc resume flow
    // -----------------------------------------------------------------------

    testWidgets('signaling-service call answered → main-process report returns callIdAlreadyExistsAndAnswered',
        (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await _signalingIncomingCall(id, _handle1, displayName: 'Paul');
      await Future.delayed(const Duration(milliseconds: 300));

      final answerLatch = Completer<String>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete(cid);
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final err = await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Paul');

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered,
        reason: 'answered signaling-service call must return callIdAlreadyExistsAndAnswered',
      );
    });

    // -----------------------------------------------------------------------
    // callkeep tearDown while signaling-service calls are active
    //
    // Scenario: app is going to the foreground and performs a full tearDown/
    // setUp cycle. All calls registered by the background signaling service
    // must be cleaned up via performEndCall.
    // Matches: SignalingForegroundIsolateManager.handleLifecycleStatus(foreground)
    //          → IsolateManager.close() → endCallsOnService()
    // -----------------------------------------------------------------------

    testWidgets('callkeep tearDown fires performEndCall for signaling-service active call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = _nextId();

      await _signalingIncomingCall(id, _handle1, displayName: 'Quinn');
      await Future.delayed(const Duration(milliseconds: 400));

      final latch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await callkeep.tearDown();
      await _waitFor(latch.future, label: 'performEndCall on tearDown');

      expect(delegate.endCallIds.where((c) => c == id).length, 1);
    });
  });

  // =========================================================================
  // BACKGROUND SIGNALING SERVICE — LIFECYCLE
  //
  // Simulates SignalingForegroundIsolateManager.handleLifecycleStatus:
  //   app backgrounded → startService (WebSocket goes live)
  //   app foregrounded → stopService (WebSocket torn down, isolate released)
  // =========================================================================

  group('background signaling service lifecycle (Android only)', () {
    // Ensure service is stopped after each lifecycle test so they don't
    // interfere with each other (some tests start but may not stop).
    tearDown(() async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
      try {
        await AndroidCallkeepServices.backgroundSignalingBootstrapService
            .stopService()
            .timeout(const Duration(seconds: 5));
      } catch (_) {}
    });

    // -----------------------------------------------------------------------
    // setUp and startService
    //
    // Scenario: app configures the background signaling service on startup
    // (typically in main.dart). Service must start without error.
    // -----------------------------------------------------------------------

    testWidgets('setUp then startService completes without error', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      await expectLater(
        AndroidCallkeepServices.backgroundSignalingBootstrapService.setUp(),
        completes,
      );

      await expectLater(
        AndroidCallkeepServices.backgroundSignalingBootstrapService.startService(),
        completes,
      );
    });

    testWidgets('startService is idempotent — calling twice does not throw', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      await AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();
      await expectLater(
        AndroidCallkeepServices.backgroundSignalingBootstrapService.startService(),
        completes,
      );
    });

    testWidgets('stopService completes without error', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      await AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();

      await expectLater(
        AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService(),
        completes,
      );
    });

    testWidgets('stopService without prior startService does not throw', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      // Matches the case where the app goes to foreground immediately after
      // cold start before the background service was ever started.
      await expectLater(
        AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService(),
        completes,
      );
    });

    testWidgets('start → stop → start cycle completes without error', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      // Matches repeated foreground/background transitions.
      await AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();
      await AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService();

      await expectLater(
        AndroidCallkeepServices.backgroundSignalingBootstrapService.startService(),
        completes,
      );
    });
  });

  // =========================================================================
  // CROSS-SERVICE INTERACTIONS
  //
  // Tests the interplay between push and signaling paths when both are active
  // simultaneously — a common real-world scenario where a push notification
  // arrives while the background signaling service is also running.
  // =========================================================================

  group('cross-service interactions (Android only)', () {
    setUp(() async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
      await _startSignalingServiceAndAwaitPort();
    });

    tearDown(() async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
      IsolateNameServer.removePortNameMapping(_signalingTestPortName);
      try {
        await AndroidCallkeepServices.backgroundSignalingBootstrapService
            .stopService()
            .timeout(const Duration(seconds: 5));
      } catch (_) {}
    });

    // -----------------------------------------------------------------------
    // Push and signaling services register the same callId
    //
    // Scenario: FCM push arrives AND the signaling WebSocket delivers the
    // same incoming call to two different service paths. Only the first
    // registration must succeed; the second must be rejected as duplicate.
    // -----------------------------------------------------------------------

    testWidgets('push then signaling service for same callId — signaling report returns callIdAlreadyExists',
        (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Rachel');
      await Future.delayed(const Duration(milliseconds: 400));

      final err = await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Rachel');
      expect(err, CallkeepIncomingCallError.callIdAlreadyExists);
    });

    testWidgets('signaling then push service for same callId — push report returns callIdAlreadyExists',
        (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      await _signalingIncomingCall(id, _handle1, displayName: 'Sam');
      await Future.delayed(const Duration(milliseconds: 400));

      final err = await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Sam');

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExists,
        reason: 'push report after signaling registration must be rejected as duplicate',
      );
    });

    // -----------------------------------------------------------------------
    // Push service cleans up, signaling service has a different call
    //
    // Scenario: push isolate ends its call while the signaling service has a
    // separate concurrent call. The endCall via push must not affect the
    // signaling service call.
    // -----------------------------------------------------------------------

    testWidgets('push service endCall does not affect a separate signaling service call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final pushId = _nextId();
      final signalingId = _nextId();

      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(pushId, _handle1, displayName: 'Tina');
      await _waitForConnection(pushId);

      await _signalingIncomingCall(signalingId, _handle2, displayName: 'Uma');
      await Future.delayed(const Duration(milliseconds: 400));

      // On devices that reject concurrent incoming calls, signalingId gets
      // onCreateIncomingConnectionFailed immediately (pushId is still RINGING).
      // Skip rather than asserting on a call that was never established.
      final signalingConn = await _waitForConnection(signalingId);
      if (signalingConn == null) {
        markTestSkipped('device does not support concurrent incoming calls');
        return;
      }

      // End only the push-path call via main-process API (IncomingCallService
      // is not available without a real FCM push).
      final endLatch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == pushId && !endLatch.isCompleted) endLatch.complete(cid);
      };
      await callkeep.endCall(pushId);
      await _waitFor(endLatch.future, label: 'performEndCall push call');

      // Signaling-path call must still be alive (report returns exists, not terminated)
      final err = await callkeep.reportNewIncomingCall(signalingId, _handle2, displayName: 'Uma');
      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExists,
        reason: 'signaling-service call must remain active after push-service endCall for a different id',
      );
    });
  });
}
