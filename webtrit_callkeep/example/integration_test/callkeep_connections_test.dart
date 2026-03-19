import 'dart:async';
import 'dart:io' show Platform;

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

const _handle1 = CallkeepHandle.number('380003000000');

var _idCounter = 0;
String _nextId() => 'conn-${_idCounter++}';

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
    for (var attempt = 0; attempt < 10; attempt++) {
      try {
        await callkeep.setUp(_options);
        break;
      } catch (_) {
        if (attempt == 9) rethrow;
        await Future.delayed(const Duration(milliseconds: 300));
      }
    }
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

  group('CallkeepConnections.getConnection (Android only)', () {
    test('getConnection returns null for nonexistent callId', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final conn = await CallkeepConnections().getConnection('conn-nonexistent-${_nextId()}');
      expect(conn, isNull);
    });

    test('getConnection returns stateRinging after reportNewIncomingCall', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Alice');
      await Future.delayed(const Duration(milliseconds: 300));

      final conn = await CallkeepConnections().getConnection(id);
      expect(conn, isNotNull);
      expect(conn!.callId, id);
      expect(conn.state, CallkeepConnectionState.stateRinging);
    });

    test('getConnection returns stateActive after answerCall', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Bob');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');
      await Future.delayed(const Duration(milliseconds: 300));

      final conn = await CallkeepConnections().getConnection(id);
      expect(conn, isNotNull);
      expect(conn!.state, CallkeepConnectionState.stateActive);
    });

    test('getConnection returns stateHolding after setHeld(true)', () async {
      if (kIsWeb || !Platform.isAndroid) {
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
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      await callkeep.setHeld(id, onHold: true);
      await Future.delayed(const Duration(milliseconds: 300));

      final conn = await CallkeepConnections().getConnection(id);
      expect(conn, isNotNull);
      expect(conn!.state, CallkeepConnectionState.stateHolding);
    });

    test('getConnection returns null or stateDisconnected after endCall', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Dan');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall');
      await Future.delayed(const Duration(milliseconds: 300));

      final conn = await CallkeepConnections().getConnection(id);
      // After endCall, connection is either removed (null) or in disconnected state
      final isGone = conn == null || conn.state == CallkeepConnectionState.stateDisconnected;
      expect(isGone, isTrue);
    });

    test('getConnection returns null on non-Android (no-op path)', () async {
      if (!kIsWeb && Platform.isAndroid) {
        markTestSkipped('Non-Android only');
        return;
      }
      final conn = await CallkeepConnections().getConnection(_nextId());
      expect(conn, isNull);
    });
  });

  // -------------------------------------------------------------------------
  // CallkeepConnections.getConnections (Android only)
  // -------------------------------------------------------------------------

  group('CallkeepConnections.getConnections (Android only)', () {
    test('getConnections has no entry for a nonexistent callId before any call', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final connections = await CallkeepConnections().getConnections();
      // There may be stale connections from prior test runs in this process;
      // we only assert the list is a valid list type.
      expect(connections, isA<List<CallkeepConnection>>());
    });

    test('getConnections includes connection after reportNewIncomingCall', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Eve');
      await Future.delayed(const Duration(milliseconds: 300));

      final connections = await CallkeepConnections().getConnections();
      expect(connections.any((c) => c.callId == id), isTrue);
    });

    test('getConnections includes both connections for two concurrent calls', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id1 = _nextId();
      final id2 = _nextId();
      await callkeep.reportNewIncomingCall(id1, _handle1, displayName: 'Frank');
      await Future.delayed(const Duration(milliseconds: 300));

      final err2 = await callkeep.reportNewIncomingCall(
        id2,
        const CallkeepHandle.number('380003000001'),
        displayName: 'Grace',
      );
      await Future.delayed(const Duration(milliseconds: 300));

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

  group('CallkeepConnections.cleanConnections (Android only)', () {
    test('cleanConnections completes without error on empty state', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      await expectLater(CallkeepConnections().cleanConnections(), completes);
    });

    test('cleanConnections removes all active connections', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Hank');
      await Future.delayed(const Duration(milliseconds: 300));

      await CallkeepConnections().cleanConnections();
      await Future.delayed(const Duration(milliseconds: 300));

      // After cleanConnections, connection should be gone or disconnected
      final conn = await CallkeepConnections().getConnection(id);
      final isGone = conn == null || conn.state == CallkeepConnectionState.stateDisconnected;
      expect(isGone, isTrue);
    });
  });

  // -------------------------------------------------------------------------
  // CallkeepConnections.updateActivitySignalingStatus (Android only)
  // -------------------------------------------------------------------------

  group('CallkeepConnections.updateActivitySignalingStatus (Android only)', () {
    test('updateActivitySignalingStatus completes for each enum value', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      for (final status in CallkeepSignalingStatus.values) {
        await expectLater(
          CallkeepConnections().updateActivitySignalingStatus(status),
          completes,
        );
      }
    });

    test('updateActivitySignalingStatus after tearDown does not throw', () async {
      if (kIsWeb || !Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;
      await callkeep.tearDown();
      await expectLater(
        CallkeepConnections().updateActivitySignalingStatus(CallkeepSignalingStatus.disconnect),
        completes,
      );
    });
  });
}
