import 'dart:async';
import 'package:flutter/foundation.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'helpers/callkeep_test_helpers.dart';

// ---------------------------------------------------------------------------
// ForegroundService / main-process signaling path — integration tests
//
// These tests cover the scenario where an incoming call arrives via the
// main-process signaling path (ForegroundService.reportNewIncomingCall),
// not via a push notification.
//
// Real-world flow (CallBloc._onCallSignalingEventIncoming):
//   WebSocket incoming_call event received
//   → CallBloc calls callkeep.reportNewIncomingCall()
//   → ForegroundService.reportNewIncomingCall()
//       calls tracker.addPending(callId)                   -- synchronous
//       calls PhoneConnectionService.startIncomingCall()   -- async Telecom call
//   → Telecom calls onCreateIncomingConnection()
//       PhoneConnection is created                         -- synchronous on CS side
//       DidPushIncomingCall broadcast sent via sendBroadcast() -- ASYNC delivery
//   → CallBloc receives the SDP offer, calls callkeep.answerCall()
//
// The race condition:
//   answerCall() may be called BEFORE the DidPushIncomingCall broadcast is
//   delivered to ForegroundService. At that moment:
//     tracker.exists(callId)   = false   (broadcast not yet processed)
//     tracker.isPending(callId)= true    (addPending was called)
//     connectionManager.getConnection() != null  (CS has the PhoneConnection)
//
//   The fix: check the CS connectionManager before falling back to the deferred
//   answer path, so that answerCall works regardless of broadcast delivery timing.
// ---------------------------------------------------------------------------

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

  // =========================================================================
  // answerCall — broadcast-lag race condition
  //
  // answerCall() is called immediately after reportNewIncomingCall() returns,
  // before the DidPushIncomingCall broadcast has been delivered to
  // ForegroundService. The ForegroundService must fall back to the CS
  // connectionManager to detect the PhoneConnection and answer immediately.
  // =========================================================================

  group('main-process signaling path — answerCall timing (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('answerCall immediately after reportNewIncomingCall fires performAnswerCall', (WidgetTester _) async {
      // This is the primary regression test for the broadcast-lag bug.
      // No delay between reportNewIncomingCall and answerCall — exercises the
      // window where tracker.isPending(callId) is true but the CS already has
      // the PhoneConnection.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Alice');

      final latch = Completer<String>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      // No delay: answerCall is called before DidPushIncomingCall broadcast
      // arrives — this is the race condition the fix addresses.
      await callkeep.answerCall(id);

      final answered = await waitFor(latch.future, label: 'performAnswerCall (immediate)');
      expect(answered, id);
    });

    testWidgets('answerCall after broadcast settles also fires performAnswerCall', (WidgetTester _) async {
      // Verifies the normal (non-race) path still works after the fix.
      // A short delay gives the DidPushIncomingCall broadcast time to arrive
      // so tracker.exists() is true when answerCall() is called.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Bob');
      await Future.delayed(const Duration(milliseconds: 300));

      final latch = Completer<String>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await callkeep.answerCall(id);

      final answered = await waitFor(latch.future, label: 'performAnswerCall (after broadcast)');
      expect(answered, id);
    });

    testWidgets('answerCall fires performAnswerCall exactly once', (WidgetTester _) async {
      // Guards against double-answer: the tracker or deferred-answer path must
      // not cause performAnswerCall to fire twice for a single answerCall().
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Charlie');

      final latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await callkeep.answerCall(id);
      await waitFor(latch.future, label: 'performAnswerCall');

      // Wait briefly to allow any spurious second callback to arrive.
      await Future.delayed(const Duration(milliseconds: 300));

      expect(
        delegate.answerCallIds.where((c) => c == id).length,
        1,
        reason: 'performAnswerCall must fire exactly once per answerCall()',
      );
    });

    testWidgets('video call: answerCall immediately fires performAnswerCall', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Dave', hasVideo: true);

      final latch = Completer<String>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await callkeep.answerCall(id);

      final answered = await waitFor(latch.future, label: 'performAnswerCall (video)');
      expect(answered, id);
    });
  });

  // =========================================================================
  // endCall — main-process path
  //
  // Verifies that endCall fires performEndCall for calls registered via the
  // main-process ForegroundService path, including immediately after
  // reportNewIncomingCall (before broadcast delivery).
  // =========================================================================

  group('main-process signaling path — endCall (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('endCall immediately after reportNewIncomingCall fires performEndCall', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Eve');

      final latch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await callkeep.endCall(id);

      final ended = await waitFor(latch.future, label: 'performEndCall (immediate decline)');
      expect(ended, id);
      expect(delegate.answerCallIds, isEmpty);
    });

    testWidgets('endCall fires performEndCall exactly once', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Frank');

      final latch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await callkeep.endCall(id);
      await waitFor(latch.future, label: 'performEndCall');

      await Future.delayed(const Duration(milliseconds: 300));

      expect(
        delegate.endCallIds.where((c) => c == id).length,
        1,
        reason: 'performEndCall must fire exactly once per endCall()',
      );
    });

    testWidgets('answer then endCall fires performEndCall once (not for answered call twice)', (WidgetTester _) async {
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

      final endLatch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };
      await callkeep.endCall(id);
      await waitFor(endLatch.future, label: 'performEndCall');

      await Future.delayed(const Duration(milliseconds: 300));

      expect(delegate.answerCallIds.where((c) => c == id).length, 1);
      expect(delegate.endCallIds.where((c) => c == id).length, 1);
    });
  });

  // =========================================================================
  // tearDown — main-process path
  //
  // tearDown() must fire performEndCall for every active main-process call,
  // including calls that were answered before the DidPushIncomingCall
  // broadcast was delivered to ForegroundService.
  // =========================================================================

  group('main-process signaling path — tearDown (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('tearDown fires performEndCall for an unanswered main-process call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Hank');

      final latch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await callkeep.tearDown();
      await waitFor(latch.future, label: 'performEndCall on tearDown');

      expect(
        delegate.endCallIds.where((c) => c == id).length,
        1,
        reason: 'tearDown must fire performEndCall exactly once for the unanswered call',
      );
    });

    testWidgets('tearDown fires performEndCall exactly once for an answered call', (WidgetTester _) async {
      // Regression: with the stale-pending bug, tearDown fired performEndCall
      // twice — once from tracker.getAll() and once from
      // drainUnconnectedPendingCallIds() when the call was still in pendingCallIds.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Irene');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };

      await callkeep.tearDown();
      await waitFor(endLatch.future, label: 'performEndCall on tearDown');

      await Future.delayed(const Duration(milliseconds: 300));

      expect(
        delegate.endCallIds.where((c) => c == id).length,
        1,
        reason: 'tearDown must fire performEndCall exactly once, not twice',
      );
    });

    testWidgets('tearDown fires performEndCall for every active main-process call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id1 = nextTestId();
      final id2 = nextTestId();

      await callkeep.reportNewIncomingCall(id1, kTestHandle1, displayName: 'Jack');
      await Future.delayed(const Duration(milliseconds: 200));
      final err2 = await callkeep.reportNewIncomingCall(id2, kTestHandle2, displayName: 'Kate');

      // On OEM devices that do not support concurrent self-managed calls (e.g.
      // Huawei), the second reportNewIncomingCall returns callRejectedBySystem.
      // In that case the call was never confirmed to Flutter, so tearDown will
      // only fire performEndCall for the calls that were actually accepted.
      final expectedIds = <String>{id1};
      if (err2 == null) expectedIds.add(id2);

      final endedIds = <String>[];
      final allDone = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (expectedIds.contains(cid) && !endedIds.contains(cid)) {
          endedIds.add(cid);
          if (endedIds.length == expectedIds.length && !allDone.isCompleted) allDone.complete();
        }
      };

      await callkeep.tearDown();
      await waitFor(allDone.future, label: 'performEndCall on tearDown for accepted calls');
      expect(endedIds, containsAll(expectedIds.toList()));
    });
  });

  // =========================================================================
  // deduplication — main-process path
  //
  // When a call is already active (registered via ForegroundService), a
  // second reportNewIncomingCall for the same callId must return an error.
  // =========================================================================

  group('main-process signaling path — deduplication (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('duplicate reportNewIncomingCall returns callIdAlreadyExists', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Leo');
      await Future.delayed(const Duration(milliseconds: 400));

      final err = await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Leo');
      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExists,
        reason: 'second reportNewIncomingCall for the same callId must return callIdAlreadyExists',
      );
    });

    testWidgets('answered call re-report returns callIdAlreadyExistsAndAnswered', (WidgetTester _) async {
      // Regression: before the removePending fix, the stale pendingCallIds entry
      // from the rejected re-report caused tearDown to fire performEndCall twice.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Mia');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      final err = await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Mia');
      expect(
        err,
        CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered,
        reason: 'reporting an already-answered callId must return callIdAlreadyExistsAndAnswered',
      );
    });

    testWidgets('re-report after reject does not cause double performEndCall on tearDown', (WidgetTester _) async {
      // Verifies that removePending() is called when CS rejects the re-report,
      // so tearDown does not see the callId in both getAll() and
      // drainUnconnectedPendingCallIds() and fire performEndCall twice.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Nick');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Simulate CallBloc arriving late with its own reportNewIncomingCall.
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Nick');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };

      await callkeep.tearDown();
      await waitFor(endLatch.future, label: 'performEndCall on tearDown');

      await Future.delayed(const Duration(milliseconds: 300));

      expect(
        delegate.endCallIds.where((c) => c == id).length,
        1,
        reason: 'tearDown must fire performEndCall exactly once even after a rejected re-report',
      );
    });
  });

  // =========================================================================
  // cold-start adoption — CALL_ID_ALREADY_EXISTS_AND_ANSWERED branch
  //
  // Regression for the cold-start race where ReplayConnectionStates fires
  // handleCSReportAnswerCall during ForegroundService.onCreate, marking the
  // call as answered in the main-process tracker BEFORE reportNewIncomingCall
  // arrives from the signaling layer (CallBloc.__onCallSignalingEventIncoming).
  //
  // In that state the tracker has isAnswered()==true but exists()==false and
  // isPending()==false. The early check in reportNewIncomingCall returned
  // CALL_ID_ALREADY_EXISTS_AND_ANSWERED immediately — without calling promote()
  // or performAnswerCall — so WebRTC never started and endCall() could not
  // find the call.
  //
  // We exercise the same ALREADY_ANSWERED branch via the observable re-report
  // path: report -> answer (tracker: isAnswered=true, exists=true) -> re-report.
  // The tracker state entering the branch differs only in exists(); the fix
  // code runs identically in both cases.
  // =========================================================================

  group('cold-start adoption — already-answered re-report (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('re-report of already-answered call fires performAnswerCall', (WidgetTester _) async {
      // Regression: the ALREADY_ANSWERED early-exit returned without firing
      // performAnswerCall, so WebRTC never started after cold-start adoption.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Oscar');

      final firstAnswerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !firstAnswerLatch.isCompleted) firstAnswerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(firstAnswerLatch.future, label: 'performAnswerCall (first)');

      // Simulate the signaling layer re-reporting the already-answered call
      // (cold-start: reportNewIncomingCall arrives after ReplayConnectionStates
      // already marked the call answered). The ALREADY_ANSWERED branch must
      // fire performAnswerCall directly to trigger WebRTC setup.
      final adoptLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !adoptLatch.isCompleted) adoptLatch.complete();
      };

      final err = await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Oscar');
      expect(err, CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered);

      await waitFor(adoptLatch.future, label: 'performAnswerCall (cold-start adoption)');
      expect(delegate.answerCallIds.where((c) => c == id).length, greaterThanOrEqualTo(2));
    });

    testWidgets('endCall succeeds after cold-start adoption', (WidgetTester _) async {
      // Regression: promote() was never called in the ALREADY_ANSWERED branch,
      // so core.exists(callId) was false and endCall() logged
      // "no connection or pending entry" without firing performEndCall.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Paula');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Simulate late-arriving reportNewIncomingCall (cold-start adoption).
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Paula');

      // After adoption, endCall must locate the call (via core.exists()) and
      // fire performEndCall — the primary observable failure before the fix.
      final endLatch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };
      await callkeep.endCall(id);

      final ended = await waitFor(endLatch.future, label: 'performEndCall after adoption');
      expect(ended, id);
    });

    testWidgets('tearDown fires performEndCall exactly once after cold-start adoption', (WidgetTester _) async {
      // After cold-start adoption, tearDown must find the call via getAll()
      // (promote() was called) and fire performEndCall exactly once.
      // drainUnconnectedPendingCallIds() must not also include it (promote()
      // removes it from pendingCallIds), preventing a double-fire.
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Quinn');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Simulate late-arriving reportNewIncomingCall (cold-start adoption).
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Quinn');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };

      await callkeep.tearDown();
      await waitFor(endLatch.future, label: 'performEndCall on tearDown');

      await Future.delayed(const Duration(milliseconds: 300));

      expect(
        delegate.endCallIds.where((c) => c == id).length,
        1,
        reason: 'tearDown must fire performEndCall exactly once after cold-start adoption',
      );
    });
  });
}
