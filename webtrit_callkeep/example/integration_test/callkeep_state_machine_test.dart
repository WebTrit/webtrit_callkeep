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
  // Full state machine: answer -> hold -> mute -> unmute -> unhold -> end
  // -------------------------------------------------------------------------

  group('full state machine: answer -> hold -> mute -> unmute -> unhold -> end', () {
    testWidgets('all 6 steps verified with individual latches', (WidgetTester _) async {
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Alice');

      // Step 1: answer
      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Step 2: hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // Step 3: mute (filter for muted: true to skip system-initiated false)
      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };
      await callkeep.setMuted(id, muted: true);
      await waitFor(muteLatch.future, label: 'performSetMuted(true)');

      // Step 4: unmute
      final unmuteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && !muted && !unmuteLatch.isCompleted) unmuteLatch.complete();
      };
      await callkeep.setMuted(id, muted: false);
      await waitFor(unmuteLatch.future, label: 'performSetMuted(false)');

      // Step 5: unhold
      final unholdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && !onHold && !unholdLatch.isCompleted) unholdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: false);
      await waitFor(unholdLatch.future, label: 'performSetHeld(false)');

      // Step 6: end
      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await waitFor(endLatch.future, label: 'performEndCall');

      // Verify event ordering
      expect(delegate.answerCallIds.contains(id), isTrue);
      expect(delegate.holdEvents.any((e) => e.callId == id && e.onHold), isTrue);
      expect(delegate.holdEvents.any((e) => e.callId == id && !e.onHold), isTrue);
      expect(delegate.muteEvents.any((e) => e.callId == id && e.muted), isTrue);
      expect(delegate.muteEvents.any((e) => e.callId == id && !e.muted), isTrue);
      expect(delegate.endCallIds.contains(id), isTrue);

      // hold(true) must come before hold(false)
      final holdTrueIdx = delegate.holdEvents.indexWhere((e) => e.callId == id && e.onHold);
      final holdFalseIdx = delegate.holdEvents.indexWhere((e) => e.callId == id && !e.onHold);
      expect(holdTrueIdx, lessThan(holdFalseIdx));
    });
  });

  // -------------------------------------------------------------------------
  // Mute while on hold (Android only)
  // -------------------------------------------------------------------------

  group('mute while on hold (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('setMuted(true) while held fires performSetMuted(true)', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Bob');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Hold first
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // Mute while held
      final muteLatch = Completer<({String callId, bool muted})>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) {
          muteLatch.complete((callId: cid, muted: muted));
        }
      };
      await callkeep.setMuted(id, muted: true);

      final event = await waitFor(muteLatch.future, label: 'performSetMuted(true) while held');
      expect(event.callId, id);
      expect(event.muted, isTrue);
    });

    testWidgets('setMuted(false) while held fires performSetMuted(false)', (WidgetTester _) async {
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
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // Mute first
      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };
      await callkeep.setMuted(id, muted: true);
      await waitFor(muteLatch.future, label: 'performSetMuted(true)');

      // Unmute while held
      final unmuteLatch = Completer<({String callId, bool muted})>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && !muted && !unmuteLatch.isCompleted) {
          unmuteLatch.complete((callId: cid, muted: muted));
        }
      };
      await callkeep.setMuted(id, muted: false);

      final event = await waitFor(unmuteLatch.future, label: 'performSetMuted(false) while held');
      expect(event.callId, id);
      expect(event.muted, isFalse);
    });
  });

  // -------------------------------------------------------------------------
  // DTMF while on hold (Android only)
  // -------------------------------------------------------------------------

  group('DTMF while on hold (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets("sendDTMF('5') while held fires performSendDTMF", (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Dan');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // DTMF while held
      final dtmfLatch = Completer<({String callId, String key})>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id && !dtmfLatch.isCompleted) dtmfLatch.complete((callId: cid, key: key));
      };
      await callkeep.sendDTMF(id, '5');

      final event = await waitFor(dtmfLatch.future, label: 'performSendDTMF while held');
      expect(event.callId, id);
      expect(event.key, '5');
    });
  });

  // -------------------------------------------------------------------------
  // Two-call hold swap (Android only)
  // -------------------------------------------------------------------------

  group('two-call hold swap (Android only)', skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('hold call1, answer call2, unhold call1 produces correct holdEvents', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id1 = nextTestId();
      final id2 = nextTestId();

      await callkeep.reportNewIncomingCall(id1, kTestHandle1, displayName: 'Eve');

      // Answer id1
      final answer1Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id1 && !answer1Latch.isCompleted) answer1Latch.complete();
      };
      await callkeep.answerCall(id1);
      await waitFor(answer1Latch.future, label: 'performAnswerCall id1');

      // Report id2
      await callkeep.reportNewIncomingCall(id2, kTestHandle2, displayName: 'Frank');

      // Wait for id2's Telecom connection to be created before proceeding.
      // The connection is built asynchronously in :callkeep_core and may not
      // exist yet by the time answerCall is called.
      // On some OEM devices (e.g. Huawei), Telecom rejects the second incoming
      // call even when the first is active. If _waitForConnection returns null
      // the device does not support concurrent self-managed calls and the
      // remainder of this scenario cannot be exercised.
      final conn2 = await waitForConnection(id2);
      if (conn2 == null) {
        markTestSkipped('device does not support concurrent incoming calls');
        return;
      }

      // Hold id1
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id1 && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id1, onHold: true);
      await waitFor(holdLatch.future, label: 'performSetHeld id1 hold');

      // Answer id2
      final answer2Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id2 && !answer2Latch.isCompleted) answer2Latch.complete();
      };
      await callkeep.answerCall(id2);
      await waitFor(answer2Latch.future, label: 'performAnswerCall id2');

      // Unhold id1
      final unholdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id1 && !onHold && !unholdLatch.isCompleted) unholdLatch.complete();
      };
      await callkeep.setHeld(id1, onHold: false);
      await waitFor(unholdLatch.future, label: 'performSetHeld id1 unhold');

      // Verify hold events for id1: hold(true) then hold(false)
      final id1HoldEvents = delegate.holdEvents.where((e) => e.callId == id1).toList();
      expect(id1HoldEvents.length, greaterThanOrEqualTo(2));
      expect(id1HoldEvents.first.onHold, isTrue);
      expect(id1HoldEvents.last.onHold, isFalse);

      // id2 was answered
      expect(delegate.answerCallIds.contains(id2), isTrue);
    });

    testWidgets('DTMF on call2 while call1 is held routes only to call2', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id1 = nextTestId();
      final id2 = nextTestId();

      await callkeep.reportNewIncomingCall(id1, kTestHandle1, displayName: 'Grace');

      final answer1Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id1 && !answer1Latch.isCompleted) answer1Latch.complete();
      };
      await callkeep.answerCall(id1);
      await waitFor(answer1Latch.future, label: 'performAnswerCall id1');

      await callkeep.reportNewIncomingCall(id2, kTestHandle2, displayName: 'Hank');
      // Same OEM guard as in the hold-swap test above: skip if Telecom rejects id2.
      final conn2 = await waitForConnection(id2);
      if (conn2 == null) {
        markTestSkipped('device does not support concurrent incoming calls');
        return;
      }

      // Hold id1
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id1 && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id1, onHold: true);
      await waitFor(holdLatch.future, label: 'performSetHeld id1');

      // Answer id2
      final answer2Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id2 && !answer2Latch.isCompleted) answer2Latch.complete();
      };
      await callkeep.answerCall(id2);
      await waitFor(answer2Latch.future, label: 'performAnswerCall id2');

      // DTMF on id2 only
      final dtmfLatch = Completer<({String callId, String key})>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id2 && !dtmfLatch.isCompleted) dtmfLatch.complete((callId: cid, key: key));
      };
      await callkeep.sendDTMF(id2, '7');

      final event = await waitFor(dtmfLatch.future, label: 'performSendDTMF id2');
      expect(event.callId, id2);

      // id1 must not have received DTMF
      expect(delegate.dtmfEvents.any((e) => e.callId == id1), isFalse);
    });
  });

  // -------------------------------------------------------------------------
  // Nested controls: mute then hold (Android only)
  // -------------------------------------------------------------------------

  group('nested controls: mute then hold (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('mute then hold then end fires each callback exactly once', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Irene');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await waitFor(answerLatch.future, label: 'performAnswerCall');

      // Mute
      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };
      await callkeep.setMuted(id, muted: true);
      await waitFor(muteLatch.future, label: 'performSetMuted(true)');

      // Hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // End
      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await waitFor(endLatch.future, label: 'performEndCall');

      // Each app-initiated callback fires at least once for this callId.
      // Android may fire additional system-initiated mute callbacks (e.g. on
      // hold state change), so use greaterThanOrEqualTo for mute events.
      expect(delegate.answerCallIds.where((c) => c == id).length, 1);
      expect(delegate.muteEvents.where((e) => e.callId == id && e.muted).length, greaterThanOrEqualTo(1));
      expect(delegate.holdEvents.where((e) => e.callId == id && e.onHold).length, 1);
      expect(delegate.endCallIds.where((c) => c == id).length, 1);
    });
  });
}
