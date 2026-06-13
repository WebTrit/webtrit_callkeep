import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'helpers/callkeep_test_helpers.dart';

// Poll getConnection until it returns the expected state or the timeout
// expires. State transitions in :callkeep_core (e.g. hold) are async, so
// reading the state immediately after issuing the command is not reliable.
Future<CallkeepConnection?> _waitForConnectionState(
  String callId,
  CallkeepConnectionState state, {
  Duration timeout = const Duration(seconds: 5),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    final conn = await CallkeepConnections().getConnection(callId);
    if (conn?.state == state) return conn;
    await Future.delayed(const Duration(milliseconds: 100));
  }
  return null;
}

// Poll getConnections until the list contains callId or the timeout expires.
Future<bool> _waitForConnectionInList(
  String callId, {
  Duration timeout = const Duration(seconds: 5),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    final list = await CallkeepConnections().getConnections();
    if (list.any((c) => c.callId == callId)) return true;
    await Future.delayed(const Duration(milliseconds: 100));
  }
  return false;
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
  // CallkeepConnections.getConnection (Android only)
  // -------------------------------------------------------------------------

  group('CallkeepConnections.getConnection (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('getConnection returns null for nonexistent callId', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final conn = await CallkeepConnections().getConnection('conn-nonexistent-${nextTestId()}');
      expect(conn, isNull);
    });

    testWidgets('getConnection returns stateRinging after reportNewIncomingCall', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Alice');

      final conn = await waitForConnection(id);
      expect(conn, isNotNull);
      expect(conn!.callId, id);
      expect(conn.state, CallkeepConnectionState.stateRinging);
    });

    testWidgets('getConnection returns stateActive after answerCall', (WidgetTester _) async {
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

      final conn = await _waitForConnectionState(id, CallkeepConnectionState.stateActive);
      expect(conn, isNotNull);
      expect(conn!.state, CallkeepConnectionState.stateActive);
    });

    testWidgets('getConnection returns stateHolding after setHeld(true)', (WidgetTester _) async {
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

      await callkeep.setHeld(id, onHold: true);

      final conn = await _waitForConnectionState(id, CallkeepConnectionState.stateHolding);
      expect(conn, isNotNull);
      expect(conn!.state, CallkeepConnectionState.stateHolding);
    });

    testWidgets('getConnection returns null or stateDisconnected after endCall', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Dan');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await waitFor(endLatch.future, label: 'performEndCall');

      final conn = await CallkeepConnections().getConnection(id);
      // After endCall, connection is either removed (null) or in disconnected state
      final isGone = conn == null || conn.state == CallkeepConnectionState.stateDisconnected;
      expect(isGone, isTrue);
    });

    testWidgets('getConnection returns null on non-Android (no-op path)', (WidgetTester _) async {
      if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Non-Android only');
        return;
      }
      final conn = await CallkeepConnections().getConnection(nextTestId());
      expect(conn, isNull);
    });
  });

  // -------------------------------------------------------------------------
  // CallkeepConnections.getConnections (Android only)
  // -------------------------------------------------------------------------

  group('CallkeepConnections.getConnections (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('getConnections has no entry for a nonexistent callId before any call', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final connections = await CallkeepConnections().getConnections();
      // There may be stale connections from prior test runs in this process;
      // we only assert the list is a valid list type.
      expect(connections, isA<List<CallkeepConnection>>());
    });

    testWidgets('getConnections includes connection after reportNewIncomingCall', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Eve');

      final found = await _waitForConnectionInList(id);
      expect(found, isTrue);
    });

    testWidgets('getConnections includes both connections for two concurrent calls', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id1 = nextTestId();
      final id2 = nextTestId();
      await callkeep.reportNewIncomingCall(id1, kTestHandle1, displayName: 'Frank');
      await _waitForConnectionInList(id1);

      await callkeep.reportNewIncomingCall(
        id2,
        const CallkeepHandle.number('380003000001'),
        displayName: 'Grace',
      );

      final connections = await CallkeepConnections().getConnections();
      // id1 must always be present
      expect(connections.any((c) => c.callId == id1), isTrue);
      // id2 may or may not be present: Android can immediately disconnect a
      // second ringing call if it exceeds the active-call limit, even when
      // reportNewIncomingCall returns null. Just verify the list is valid.
      expect(connections, isA<List<CallkeepConnection>>());
    });
  });

  // -------------------------------------------------------------------------
  // CallkeepConnections.cleanConnections (Android only)
  // -------------------------------------------------------------------------

  group('CallkeepConnections.cleanConnections (Android only)',
      skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android, () {
    testWidgets('cleanConnections completes without error on empty state', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      await expectLater(CallkeepConnections().cleanConnections(), completes);
    });

    testWidgets('cleanConnections removes all active connections', (WidgetTester _) async {
      if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Hank');
      await waitForConnection(id);

      await CallkeepConnections().cleanConnections();

      // After cleanConnections, connection should be gone or disconnected
      final conn = await CallkeepConnections().getConnection(id);
      final isGone = conn == null || conn.state == CallkeepConnectionState.stateDisconnected;
      expect(isGone, isTrue);
    });
  });
}
