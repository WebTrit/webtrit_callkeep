import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

// ---------------------------------------------------------------------------
// Shared test fixtures
// ---------------------------------------------------------------------------

const _options = CallkeepOptions(
  ios: CallkeepIOSOptions(
    localizedName: 'Integration Tests',
    maximumCallGroups: 2,
    maximumCallsPerCallGroup: 1,
    supportedHandleTypes: {CallkeepHandleType.number},
  ),
  android: CallkeepAndroidOptions(),
);

const _handle1 = CallkeepHandle.number('380000000000');
const _handle2 = CallkeepHandle.number('380000000001');

// Each test gets fresh IDs to avoid CALL_ID_ALREADY_TERMINATED from prior runs.
// Android ConnectionManager keeps terminated connections in its registry until
// the process restarts, so reusing IDs across setUp/tearDown cycles fails.
var _idCounter = 0;
String _nextId() => 'stress-${_idCounter++}';

// ---------------------------------------------------------------------------
// Recording delegate
// ---------------------------------------------------------------------------

class _RecordingDelegate implements CallkeepDelegate {
  final answerCallIds = <String>[];
  final endCallIds = <String>[];
  final didPushEvents = <({String callId, CallkeepIncomingCallError? error})>[];

  void Function(String callId)? onPerformAnswerCall;
  void Function(String callId)? onPerformEndCall;

  @override
  void continueStartCallIntent(
    CallkeepHandle handle,
    String? displayName,
    bool video,
  ) {}

  @override
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  ) {
    didPushEvents.add((callId: callId, error: error));
  }

  @override
  void didActivateAudioSession() {}

  @override
  void didDeactivateAudioSession() {}

  @override
  void didReset() {}

  @override
  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool video,
  ) =>
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
  Future<bool> performAudioDeviceSet(
    String callId,
    CallkeepAudioDevice device,
  ) =>
      Future.value(true);

  @override
  Future<bool> performAudioDevicesUpdate(
    String callId,
    List<CallkeepAudioDevice> devices,
  ) =>
      Future.value(true);
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late Callkeep callkeep;
  late _RecordingDelegate delegate;

  setUp(() async {
    callkeep = Callkeep();
    delegate = _RecordingDelegate();
    // ForegroundService binding is async: the PHostApi Pigeon channel is only
    // registered after onServiceConnected fires. On the very first test the
    // setUp() call may arrive before that happens, producing a channel-error.
    // Retry with backoff until the service is ready.
    for (var attempt = 0; attempt < 10; attempt++) {
      try {
        await callkeep.setUp(_options);
        break;
      } catch (_) {
        if (attempt == 9) rethrow;
        await Future.delayed(const Duration(milliseconds: 300));
      }
    }
    // Set the delegate only after setUp succeeds so that the unawaited
    // onDelegateSet() Pigeon call does not produce an unhandled channel-error.
    callkeep.setDelegate(delegate);
  });

  tearDown(() async {
    callkeep.setDelegate(null);
    try {
      await callkeep.tearDown().timeout(const Duration(seconds: 15));
    } catch (_) {
      // tearDown timed out, threw, or was already called in the test body
    }
  });

  // -------------------------------------------------------------------------
  // setUp / tearDown lifecycle
  // -------------------------------------------------------------------------

  group('setUp / tearDown lifecycle', () {
    test('isSetUp returns true after setUp', () async {
      expect(await callkeep.isSetUp(), isTrue);
    });

    test('tearDown then re-setUp works', () async {
      await callkeep.tearDown();
      await callkeep.setUp(_options);
      expect(await callkeep.isSetUp(), isTrue);
    });
  });

  // -------------------------------------------------------------------------
  // reportNewIncomingCall - deduplication
  // -------------------------------------------------------------------------

  group('reportNewIncomingCall - deduplication', () {
    test('fresh call ID succeeds', () async {
      final id = _nextId();

      final err = await callkeep.reportNewIncomingCall(
        id,
        _handle1,
        displayName: 'Call 1',
      );
      expect(err, isNull);
    });

    test('second report with same ID returns callIdAlreadyExists', () async {
      final id = _nextId();

      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');
      final err = await callkeep.reportNewIncomingCall(
        id,
        _handle1,
        displayName: 'Call',
      );

      expect(err, CallkeepIncomingCallError.callIdAlreadyExists);
    });

    test('spam 4x same ID - only first succeeds', () async {
      final id = _nextId();
      final results = <CallkeepIncomingCallError?>[];

      for (var i = 0; i < 4; i++) {
        results.add(
          await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call'),
        );
      }

      expect(results[0], isNull, reason: 'first call must succeed');
      for (final err in results.sublist(1)) {
        expect(err, isNotNull, reason: 'subsequent calls must return an error');
      }
    });

    test('two different call IDs both succeed', () async {
      final id1 = _nextId();
      final id2 = _nextId();

      final err1 = await callkeep.reportNewIncomingCall(
        id1,
        _handle1,
        displayName: 'Call 1',
      );
      final err2 = await callkeep.reportNewIncomingCall(
        id2,
        _handle2,
        displayName: 'Call 2',
      );

      expect(err1, isNull);
      expect(err2, isNull);
    });
  });

  // -------------------------------------------------------------------------
  // Call lifecycle - answer and end via Dart API
  // -------------------------------------------------------------------------

  group('call lifecycle', () {
    test('answerCall triggers performAnswerCall callback', () async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');

      final completer = Completer<String>();
      delegate.onPerformAnswerCall = completer.complete;

      await callkeep.answerCall(id);

      final answeredId = await completer.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => throw TimeoutException('performAnswerCall not fired'),
      );
      expect(answeredId, id);
    });

    test('endCall on incoming call triggers performEndCall callback', () async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');

      final completer = Completer<String>();
      delegate.onPerformEndCall = completer.complete;

      await callkeep.endCall(id);

      final endedId = await completer.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => throw TimeoutException('performEndCall not fired'),
      );
      expect(endedId, id);
    });

    test('endCall after answerCall triggers performEndCall', () async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');

      final answerCompleter = Completer<String>();
      delegate.onPerformAnswerCall = answerCompleter.complete;
      await callkeep.answerCall(id);
      await answerCompleter.future.timeout(const Duration(seconds: 5));

      final endCompleter = Completer<String>();
      delegate.onPerformEndCall = endCompleter.complete;
      await callkeep.endCall(id);

      final endedId = await endCompleter.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => throw TimeoutException('performEndCall not fired'),
      );
      expect(endedId, id);
    });

    test('endCall twice - second call returns error, delegate fires once', () async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');

      final completer = Completer<String>();
      delegate.onPerformEndCall = completer.complete;
      await callkeep.endCall(id);
      await completer.future.timeout(const Duration(seconds: 5));

      // Second endCall on an already-ended call must not throw
      final secondErr = await callkeep.endCall(id);
      expect(delegate.endCallIds.length, 1);
      expect(secondErr, isNotNull);
    });
  });

  // -------------------------------------------------------------------------
  // Stress - rapid succession
  // -------------------------------------------------------------------------

  group('stress - rapid succession', () {
    test('report two calls then end both - each performEndCall fires once', () async {
      final id1 = _nextId();
      final id2 = _nextId();

      await callkeep.reportNewIncomingCall(id1, _handle1, displayName: 'Call 1');
      await callkeep.reportNewIncomingCall(id2, _handle2, displayName: 'Call 2');

      final endedIds = <String>[];
      final latch = Completer<void>();
      var count = 0;
      delegate.onPerformEndCall = (id) {
        endedIds.add(id);
        count++;
        if (count == 2) latch.complete();
      };

      await callkeep.endCall(id1);
      await callkeep.endCall(id2);

      await latch.future.timeout(
        const Duration(seconds: 10),
        onTimeout: () => throw TimeoutException('not all performEndCall fired: $endedIds'),
      );

      expect(endedIds, containsAll([id1, id2]));
      expect(endedIds.length, 2);
    });

    test('spam same ID concurrently - exactly one succeeds', () async {
      final id = _nextId();
      final futures = List.generate(
        4,
        (_) => callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call'),
      );
      final results = await Future.wait(futures);

      final successes = results.where((e) => e == null).length;
      expect(successes, 1, reason: 'exactly one concurrent report must succeed');
    });

    test('tearDown while calls are active triggers performEndCall for each', () async {
      final id1 = _nextId();
      final id2 = _nextId();

      await callkeep.reportNewIncomingCall(id1, _handle1, displayName: 'Call 1');
      await callkeep.reportNewIncomingCall(id2, _handle2, displayName: 'Call 2');

      await callkeep.tearDown();

      // Give callbacks time to arrive after tearDown
      await Future.delayed(const Duration(milliseconds: 500));

      expect(delegate.endCallIds, containsAll([id1, id2]));
    });
  });

  // -------------------------------------------------------------------------
  // Regression - decline unanswered call (Android only)
  //
  // Covers the fix in IncomingCallService.handleRelease(answered=false):
  // performEndCall (SIP BYE) must fire before releaseResources closes the
  // WebSocket. Previously, release() was called directly from handleRelease,
  // closing the WebSocket before the BYE could be sent.
  // -------------------------------------------------------------------------

  group('regression - decline unanswered call (Android only)', () {
    /// Verifies that declining an unanswered call triggers performEndCall
    /// and does NOT trigger performAnswerCall.
    ///
    /// The fix ensures the handleRelease(answered=false) path calls
    /// performEndCall first (BYE → server) and only then calls release()
    /// (WebSocket teardown). The observable effect from Flutter is that
    /// performEndCall fires; the absence of performAnswerCall confirms the
    /// correct (decline, not answer) callback sequence ran.
    test('decline unanswered call fires performEndCall, not performAnswerCall', () async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');

      final endCompleter = Completer<String>();
      delegate.onPerformEndCall = endCompleter.complete;
      // Wire a hard failure so any late performAnswerCall is caught immediately
      // rather than being silently missed by the isEmpty check below.
      delegate.onPerformAnswerCall = (_) => fail(
            'performAnswerCall must not fire when declining before answer',
          );

      await callkeep.endCall(id);

      final endedId = await endCompleter.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => throw TimeoutException('performEndCall not fired after decline'),
      );

      expect(endedId, id);
      expect(
        delegate.answerCallIds,
        isEmpty,
        reason: 'performAnswerCall must not fire when declining before answer',
      );
      expect(
        delegate.endCallIds.where((e) => e == id).length,
        1,
        reason: 'performEndCall must fire exactly once',
      );
    });

    /// Exercises the race-condition window: endCall is called immediately
    /// after reportNewIncomingCall with no artificial delay. This is the
    /// closest integration-test approximation of the lock-screen decline
    /// button scenario — the call is still in RINGING state when the user
    /// taps decline.
    ///
    /// With the old code, the immediate decline could close the WebSocket
    /// before the BYE was sent. The fix serialises the teardown so BYE
    /// always precedes WebSocket close.
    test('immediate decline (no delay) still fires performEndCall', () async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      // Do NOT await — start the incoming call and immediately decline.
      // Attach an error handler so a transient channel error does not
      // become an unhandled async exception that destabilises the test.
      callkeep
          .reportNewIncomingCall(id, _handle1, displayName: 'Call')
          // ignore: unawaited_futures
          .catchError((_) {});

      final endCompleter = Completer<String>();
      delegate.onPerformEndCall = endCompleter.complete;

      await callkeep.endCall(id);

      final endedId = await endCompleter.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => throw TimeoutException('performEndCall not fired on immediate decline'),
      );
      expect(endedId, id);
    });

    /// After a decline the call is terminated. A subsequent reportNewIncomingCall
    /// with the same ID must return callIdAlreadyTerminated, confirming that
    /// the full cleanup path (performEndCall → release → releaseResources)
    /// completed and the ConnectionManager's terminated set was updated.
    test('after decline, re-reporting same ID returns callIdAlreadyTerminated', () async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');

      final endCompleter = Completer<String>();
      delegate.onPerformEndCall = endCompleter.complete;
      await callkeep.endCall(id);
      await endCompleter.future.timeout(const Duration(seconds: 5));

      // Poll until the Android side registers the terminated state, rather than
      // relying on a fixed delay that can be too short on slow devices.
      CallkeepIncomingCallError? err;
      final deadline = DateTime.now().add(const Duration(seconds: 5));
      while (DateTime.now().isBefore(deadline)) {
        err = await callkeep.reportNewIncomingCall(
          id,
          _handle1,
          displayName: 'Call',
        );
        if (err == CallkeepIncomingCallError.callIdAlreadyTerminated) break;
        await Future.delayed(const Duration(milliseconds: 100));
      }

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyTerminated,
        reason: 'terminated call must be recognised as such, not as a fresh slot',
      );
    });
  });

  // -------------------------------------------------------------------------
  // Regression - push auto-answer then main process reportNewIncomingCall
  // -------------------------------------------------------------------------

  group('regression - push auto-answer then main-process report (Android only)', () {
    /// Regression for the bug where `onCreateIncomingConnection` never called
    /// `removePending(callId)`.
    ///
    /// Flow:
    ///   1. Push isolate calls `reportNewIncomingCall` — the callId is reserved
    ///      as *pending* inside `checkAndReservePending`.
    ///   2. Telecom calls `onCreateIncomingConnection` on the binder thread.
    ///      The fix: `removePending(callId)` is called after `addConnection`.
    ///   3. Push isolate answers the call → `hasAnswered = true`.
    ///   4. Main process CallBloc calls `reportNewIncomingCall` again (~6 s later).
    ///
    /// Expected: the second report returns `callIdAlreadyExistsAndAnswered`, not
    /// `callIdAlreadyExists`.  The answered variant tells Flutter that the call
    /// is already active so it can show the in-call UI instead of treating it
    /// as a generic duplicate error.
    test('answered call - second reportNewIncomingCall returns callIdAlreadyExistsAndAnswered', () async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      // Step 1+2: push isolate reports the call; Telecom creates the connection
      // and (with the fix) removes it from pendingCallIds.
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Call');

      // Step 3: answer the call — sets hasAnswered = true on the PhoneConnection.
      final answerCompleter = Completer<String>();
      delegate.onPerformAnswerCall = answerCompleter.complete;
      await callkeep.answerCall(id);
      await answerCompleter.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => throw TimeoutException('performAnswerCall not fired'),
      );

      // Step 4: main process CallBloc calls reportNewIncomingCall again.
      final err = await callkeep.reportNewIncomingCall(
        id,
        _handle1,
        displayName: 'Call',
      );

      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered,
        reason: 'second report after answer must return '
            'callIdAlreadyExistsAndAnswered so Flutter shows the in-call UI',
      );
    });
  });

  // -------------------------------------------------------------------------
  // Stress - push + direct (Android only)
  // -------------------------------------------------------------------------

  group('stress - push + direct (Android only)', () {
    test('push then direct same ID - direct returns callIdAlreadyExists', () async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Call');

      // Give the push path time to register the connection
      await Future.delayed(const Duration(milliseconds: 300));

      final err = await callkeep.reportNewIncomingCall(
        id,
        _handle1,
        displayName: 'Call',
      );

      expect(err, CallkeepIncomingCallError.callIdAlreadyExists);
    });

    test('mixed push + direct spam 3x same ID - system stays stable', () async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      for (var i = 0; i < 3; i++) {
        AndroidCallkeepServices.backgroundPushNotificationBootstrapService
            .reportNewIncomingCall(id, _handle1, displayName: 'Call');

        final err = await callkeep.reportNewIncomingCall(
          id,
          _handle1,
          displayName: 'Call',
        );

        expect(err, isNotNull);
      }

      // tearDown must not throw even after spam
      await callkeep.tearDown();
      // On Android the ForegroundService stays running after tearDown, so
      // isSetUp() remains true. The important invariant is that tearDown()
      // completes without throwing.
    });
  });
}
