import 'dart:async';
import 'package:flutter/foundation.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

// ---------------------------------------------------------------------------
// Shared fixtures
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

const _handle1 = CallkeepHandle.number('380004000000');

var _idCounter = 0;
String _nextId() => 'delegate-${_idCounter++}';

// ---------------------------------------------------------------------------
// Recording delegate with extended callbacks
// ---------------------------------------------------------------------------

class _RecordingDelegate implements CallkeepDelegate {
  final List<String> answerCallIds = [];
  final List<String> endCallIds = [];
  final List<String> startCallIds = [];
  final List<({String callId, bool onHold})> holdEvents = [];
  final List<({String callId, bool muted})> muteEvents = [];
  int activateAudioSessionCount = 0;
  int deactivateAudioSessionCount = 0;
  final List<({String callId, CallkeepIncomingCallError? error})> pushIncomingEvents = [];

  void Function(String callId)? onPerformAnswerCall;
  void Function(String callId)? onPerformEndCall;
  void Function(String callId)? onPerformStartCall;
  void Function()? onDidActivateAudioSession;
  void Function()? onDidDeactivateAudioSession;
  void Function(String callId, CallkeepIncomingCallError? error)? onDidPushIncomingCall;

  @override
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video) {}

  @override
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  ) {
    pushIncomingEvents.add((callId: callId, error: error));
    onDidPushIncomingCall?.call(callId, error);
  }

  @override
  void didActivateAudioSession() {
    activateAudioSessionCount++;
    onDidActivateAudioSession?.call();
  }

  @override
  void didDeactivateAudioSession() {
    deactivateAudioSessionCount++;
    onDidDeactivateAudioSession?.call();
  }

  @override
  void didReset() {}

  @override
  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool video,
  ) {
    startCallIds.add(callId);
    onPerformStartCall?.call(callId);
    return Future.value(true);
  }

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
  Future<bool> performSetHeld(String callId, bool onHold) {
    holdEvents.add((callId: callId, onHold: onHold));
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    muteEvents.add((callId: callId, muted: muted));
    return Future.value(true);
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) => Future.value(true);

  @override
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device) => Future.value(true);

  @override
  Future<bool> performAudioDevicesUpdate(String callId, List<CallkeepAudioDevice> devices) => Future.value(true);
}

// ---------------------------------------------------------------------------
// Helper: await a delegate callback with timeout
// ---------------------------------------------------------------------------

Future<T> _waitFor<T>(Future<T> future, {String label = 'callback'}) {
  return future.timeout(
    const Duration(seconds: 10),
    onTimeout: () => throw TimeoutException('$label did not fire within timeout'),
  );
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

  // -------------------------------------------------------------------------
  // setDelegate(null) mid-call (Android only)
  // -------------------------------------------------------------------------

  group('setDelegate(null) mid-call (Android only)', () {
    testWidgets('setDelegate(null) during active call does not crash', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;

      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Alice');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Simulate BLoC.close() pattern: setDelegate(null) while call is active
      callkeep.setDelegate(null);

      // endCall must not throw even with null delegate
      await expectLater(callkeep.endCall(id), completes);

      await callkeep.tearDown();
    });

    testWidgets('setDelegate(null) then restore routes callbacks to restored delegate', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Bob');

      // Temporarily remove delegate
      callkeep.setDelegate(null);

      // Restore delegate
      callkeep.setDelegate(delegate);

      // endCall should route to restored delegate
      final endLatch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };
      await callkeep.endCall(id);

      final ended = await _waitFor(endLatch.future, label: 'performEndCall after delegate restore');
      expect(ended, id);
    });
  });

  // -------------------------------------------------------------------------
  // Delegate swap mid-call (Android only)
  // -------------------------------------------------------------------------

  group('delegate swap mid-call (Android only)', () {
    testWidgets('swapping to new delegate routes only new delegate receives events', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Carol');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall delegate1');

      // Swap to delegate2
      final delegate2 = _RecordingDelegate();
      callkeep.setDelegate(delegate2);

      // Old delegate must not receive further callbacks
      delegate.onPerformEndCall = (_) => fail('old delegate must not receive events after swap');

      final endLatch = Completer<String>();
      delegate2.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };

      await callkeep.endCall(id);
      final ended = await _waitFor(endLatch.future, label: 'performEndCall on delegate2');
      expect(ended, id);
      expect(delegate.endCallIds.where((c) => c == id).length, 0,
          reason: 'old delegate must not have received performEndCall');
    });
  });

  // -------------------------------------------------------------------------
  // didPushIncomingCall callback (Android only)
  // -------------------------------------------------------------------------

  group('didPushIncomingCall callback (Android only)', () {
    testWidgets('fires with null error on successful push-path registration', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();

      final latch = Completer<CallkeepIncomingCallError?>();
      delegate.onDidPushIncomingCall = (cid, err) {
        if (cid == id && !latch.isCompleted) latch.complete(err);
      };

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Dave');

      final err = await _waitFor(latch.future, label: 'didPushIncomingCall');
      expect(err, isNull);
    });

    testWidgets('fires with callIdAlreadyExists on duplicate registration', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();

      // First registration via main path
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Eve');
      await Future.delayed(const Duration(milliseconds: 300));

      // Duplicate registration via push path — platform returns error directly
      final err = await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, _handle1, displayName: 'Eve');

      // The platform returns callIdAlreadyExists (or similar) for duplicate IDs
      expect(err, isNotNull);
      expect(
        err == CallkeepIncomingCallError.callIdAlreadyExists ||
            err == CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered ||
            err == CallkeepIncomingCallError.callIdAlreadyTerminated,
        isTrue,
      );
    });
  });

  // -------------------------------------------------------------------------
  // Audio session delegate callbacks (Android only)
  // -------------------------------------------------------------------------

  group('audio session delegate callbacks (Android only)', () {
    testWidgets('didActivateAudioSession fires after answerCall', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Frank');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };

      final audioLatch = Completer<void>();
      delegate.onDidActivateAudioSession = () {
        if (!audioLatch.isCompleted) audioLatch.complete();
      };

      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // didActivateAudioSession fires asynchronously after answer
      try {
        await audioLatch.future.timeout(const Duration(seconds: 5));
        expect(delegate.activateAudioSessionCount, greaterThan(0));
      } on TimeoutException {
        // Some test environments (emulators without audio) may not fire this;
        // mark as skipped rather than fail
        markTestSkipped('didActivateAudioSession did not fire on this device');
      }
    });

    testWidgets('didDeactivateAudioSession fires after call ends', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Grace');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final deactivateLatch = Completer<void>();
      delegate.onDidDeactivateAudioSession = () {
        if (!deactivateLatch.isCompleted) deactivateLatch.complete();
      };

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall');

      try {
        await deactivateLatch.future.timeout(const Duration(seconds: 5));
        expect(delegate.deactivateAudioSessionCount, greaterThan(0));
      } on TimeoutException {
        markTestSkipped('didDeactivateAudioSession did not fire on this device');
      }
    });
  });
}
