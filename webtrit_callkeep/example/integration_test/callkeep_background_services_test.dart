import 'dart:async';

import 'package:flutter/foundation.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

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
// The tests exercise the *callkeep layer* side of those services:
//   - Call registration / deduplication between isolate and main process.
//   - performEndCall / performAnswerCall delegate routing.
//   - releaseCall clean-up triggered by signaling events
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
    // Matches: IsolateManager.run → push path registration,
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
    // Multi-call cleanup
    //
    // Scenario: signaling error / unregistered event while multiple calls are
    // active. IsolateManager calls releaseCall(callId) for each terminal path
    // which must trigger performEndCall for every active call.
    // Matches: IsolateManager._onSignalingError → releaseCall(callId)
    //          IsolateManager._onUnregistered   → releaseCall(callId)
    // -----------------------------------------------------------------------

    // Note: releaseCall(callId) targets a specific call and requires
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
}
