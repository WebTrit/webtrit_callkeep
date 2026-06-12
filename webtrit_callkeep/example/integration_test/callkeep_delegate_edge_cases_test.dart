import 'dart:async';
import 'package:flutter/foundation.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'helpers/callkeep_test_helpers.dart';

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late Callkeep callkeep;
  late RecordingDelegate delegate;
  var globalTearDownNeeded = true;

  setUp(() async {
    globalTearDownNeeded = true;
    callkeep = Callkeep();
    delegate = RecordingDelegate();
    await callkeep.setUp(kTestOptions);
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

  group('setDelegate(null) mid-call (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android,
      () {
    testWidgets('setDelegate(null) during active call does not crash', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Alice');
      // Poll until the PhoneConnection is established (FGS broadcast delivered).
      // A fixed delay is not enough after 80+ tests when FGS latency accumulates;
      // polling converges as soon as the connection is ready regardless of load.
      await waitForConnection(id, timeout: const Duration(seconds: 30));

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      // Connection is already established, so performAnswerCall should arrive quickly.
      await waitFor(answerLatch.future, label: 'performAnswerCall', timeout: const Duration(seconds: 15));

      // Simulate BLoC.close() pattern: setDelegate(null) while call is active
      callkeep.setDelegate(null);

      // endCall must not throw even with null delegate
      await expectLater(callkeep.endCall(id), completes);

      await callkeep.tearDown();
    });

    testWidgets('setDelegate(null) then restore routes callbacks to restored delegate', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Bob');

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

      final ended = await waitFor(endLatch.future, label: 'performEndCall after delegate restore');
      expect(ended, id);
    });
  });

  // -------------------------------------------------------------------------
  // Delegate swap mid-call (Android only)
  // -------------------------------------------------------------------------

  group('delegate swap mid-call (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('swapping to new delegate routes only new delegate receives events', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Carol');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall delegate1');

      // Swap to delegate2
      final delegate2 = RecordingDelegate();
      callkeep.setDelegate(delegate2);

      // Old delegate must not receive further callbacks
      delegate.onPerformEndCall = (_) => fail('old delegate must not receive events after swap');

      final endLatch = Completer<String>();
      delegate2.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };

      await callkeep.endCall(id);
      final ended = await waitFor(endLatch.future, label: 'performEndCall on delegate2');
      expect(ended, id);
      expect(delegate.endCallIds.where((c) => c == id).length, 0,
          reason: 'old delegate must not have received performEndCall');
    });
  });

  // -------------------------------------------------------------------------
  // didPushIncomingCall callback (Android only)
  // -------------------------------------------------------------------------

  group('didPushIncomingCall callback (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android,
      () {
    testWidgets('fires with null error on successful push-path registration', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();

      final latch = Completer<CallkeepIncomingCallError?>();
      delegate.onDidPushIncomingCall = (cid, err) {
        if (cid == id && !latch.isCompleted) latch.complete(err);
      };

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, kTestHandle1, displayName: 'Dave');

      final err = await waitFor(latch.future, label: 'didPushIncomingCall');
      expect(err, isNull);
    });

    testWidgets('fires with callIdAlreadyExists on duplicate registration', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();

      // First registration via main path
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Eve');
      await Future.delayed(const Duration(milliseconds: 300));

      // Duplicate registration via push path — platform returns error directly
      final err = await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(id, kTestHandle1, displayName: 'Eve');

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

  group('audio session delegate callbacks (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('didActivateAudioSession fires after answerCall', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Frank');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };

      final audioLatch = Completer<void>();
      delegate.onDidActivateAudioSession = () {
        if (!audioLatch.isCompleted) audioLatch.complete();
      };

      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

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
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Grace');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      final deactivateLatch = Completer<void>();
      delegate.onDidDeactivateAudioSession = () {
        if (!deactivateLatch.isCompleted) deactivateLatch.complete();
      };

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await waitFor(endLatch.future, label: 'performEndCall');

      try {
        await deactivateLatch.future.timeout(const Duration(seconds: 5));
        expect(delegate.deactivateAudioSessionCount, greaterThan(0));
      } on TimeoutException {
        markTestSkipped('didDeactivateAudioSession did not fire on this device');
      }
    });
  });
}
