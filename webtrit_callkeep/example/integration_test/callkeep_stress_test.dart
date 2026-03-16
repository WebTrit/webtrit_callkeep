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
    callkeep.setDelegate(delegate);
    await callkeep.setUp(_options);
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
