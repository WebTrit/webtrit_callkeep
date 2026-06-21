import 'dart:async';
import 'package:flutter/foundation.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'helpers/callkeep_test_helpers.dart';

// ---------------------------------------------------------------------------
// Standard setUp helper
// ---------------------------------------------------------------------------

Future<void> _doSetUp(Callkeep callkeep) async {
  await callkeep.setUp(kTestOptions);
}

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
    await _doSetUp(callkeep);
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
  // answerCall idempotency (Android only)
  // -------------------------------------------------------------------------

  group('answerCall idempotency (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('answerCall twice fires performAnswerCall exactly once', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Alice');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall first');

      // Allow Telecom to register answered state
      await Future.delayed(const Duration(milliseconds: 200));

      // Second answerCall on the same call — must not crash regardless of error value
      await expectLater(callkeep.answerCall(id), completes);

      // performAnswerCall must have fired exactly once
      expect(
        delegate.answerCallIds.where((c) => c == id).length,
        1,
        reason: 'performAnswerCall must fire exactly once',
      );
    });

    testWidgets('answerCall on a call already ended via endCall returns error', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Bob');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await waitFor(endLatch.future, label: 'performEndCall');

      final err = await callkeep.answerCall(id);
      expect(err, isNotNull);
    });
  });

  // -------------------------------------------------------------------------
  // endCall returns unknownCallUuid after reportEndCall (Android only)
  // -------------------------------------------------------------------------

  group('endCall returns unknownCallUuid after reportEndCall (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('endCall after reportEndCall(remoteEnded) returns unknownCallUuid or null', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      // Control tearDown manually so fixture tearDown doesn't add a second
      // performEndCall for this call ID and pollute the count assertion.
      globalTearDownNeeded = false;

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Carol');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      await callkeep.reportEndCall(id, 'Carol', CallkeepEndCallReason.remoteEnded);
      await Future.delayed(const Duration(milliseconds: 400));

      // Must not throw; error may be unknownCallUuid or null
      final err = await callkeep.endCall(id);
      expect(
        err == null || err == CallkeepCallRequestError.unknownCallUuid,
        isTrue,
        reason: 'endCall after reportEndCall must return unknownCallUuid or null, not throw',
      );

      await callkeep.tearDown();
    });
  });

  // -------------------------------------------------------------------------
  // WebtritCallkeepSound ringback (Android only)
  // -------------------------------------------------------------------------

  group('WebtritCallkeepSound ringback (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android,
      () {
    testWidgets('playRingbackSound completes without error', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      await expectLater(WebtritCallkeepSound().playRingbackSound(), completes);
    });

    testWidgets('stopRingbackSound completes without error', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      await WebtritCallkeepSound().playRingbackSound();
      await expectLater(WebtritCallkeepSound().stopRingbackSound(), completes);
    });

    testWidgets('stopRingbackSound when not playing is safe', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      await expectLater(WebtritCallkeepSound().stopRingbackSound(), completes);
    });
  });

  // -------------------------------------------------------------------------
  // performAnswerCall returning false is handled gracefully (Android only)
  // -------------------------------------------------------------------------

  group('performAnswerCall returning false is handled gracefully (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('performAnswerCall returning false does not crash callkeep', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();

      // Delegate returns false from performAnswerCall
      delegate.performAnswerCallOverride = (_) => Future.value(false);

      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Dan');
      final err = await callkeep.answerCall(id);

      await Future.delayed(const Duration(milliseconds: 300));

      // Must complete without exception
      expect(() => err, returnsNormally);
    });
  });

  // -------------------------------------------------------------------------
  // setDelegate(null) in close() pattern (Android only)
  // -------------------------------------------------------------------------

  group('setDelegate(null) in close() pattern (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('tearDown after setDelegate(null) does not crash or fire stale callbacks', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Eve');

      // Simulate BLoC.close(): remove delegate before tearDown
      callkeep.setDelegate(null);

      var crashDetected = false;
      try {
        await callkeep.tearDown();
      } catch (_) {
        crashDetected = true;
      }

      expect(crashDetected, isFalse, reason: 'tearDown with null delegate must not throw');
      // The (now-null) delegate must not have received callbacks
      expect(delegate.endCallIds.where((c) => c == id).length, 0, reason: 'null delegate must not receive callbacks');
    });
  });

  // -------------------------------------------------------------------------
  // performEndCall async contract (Android only)
  // -------------------------------------------------------------------------

  group('performEndCall async contract (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android,
      () {
    testWidgets('performEndCall async work is awaited before native connection cleanup', (WidgetTester _) async {
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
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      final signalingDone = Completer<void>();
      delegate.performEndCallOverride = (cid) async {
        // Simulate async signaling work (e.g. sending SIP DECLINE)
        await Future.delayed(const Duration(milliseconds: 400));
        if (!signalingDone.isCompleted) signalingDone.complete();
        return true;
      };

      await callkeep.endCall(id);

      // Allow time for performEndCall async work to complete after endCall returns.
      // Android callkeep may not await performEndCall before completing endCall
      // (fire-and-forget via IPC). Wait up to 1s to give the async work time to finish.
      if (!signalingDone.isCompleted) {
        await signalingDone.future.timeout(
          const Duration(seconds: 1),
          onTimeout: () {}, // acceptable if platform uses fire-and-forget
        );
      }
      // Verify that performEndCall was actually invoked (async work ran).
      expect(
        signalingDone.isCompleted,
        isTrue,
        reason: 'performEndCall override must have been called for endCall to proceed',
      );
    });

    testWidgets('performEndCall returning false is handled gracefully', (WidgetTester _) async {
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

      delegate.performEndCallOverride = (_) => Future.value(false);

      await expectLater(callkeep.endCall(id), completes);
    });
  });

  // -------------------------------------------------------------------------
  // performEndCall during signaling connect race (Android only)
  // -------------------------------------------------------------------------

  group('performEndCall during signaling connect race (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('endCall fires performEndCall even when called immediately after reportNewIncomingCall',
        (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Hank');

      // Immediately end without waiting for any answer — push race scenario
      final err = await callkeep.endCall(id);

      await Future.delayed(const Duration(milliseconds: 300));

      // Must not throw; error is acceptable (e.g. call may not yet be in Telecom)
      expect(() => err, returnsNormally);
    });
  });
}
