import 'dart:async';
import 'dart:io' show Platform;

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

const _handle1 = CallkeepHandle.number('380006000000');
const _handle2 = CallkeepHandle.number('380006000001');

var _idCounter = 0;
String _nextId() => 'sm-${_idCounter++}';

// ---------------------------------------------------------------------------
// Recording delegate
// ---------------------------------------------------------------------------

class _RecordingDelegate implements CallkeepDelegate {
  final List<String> startCallIds = [];
  final List<String> answerCallIds = [];
  final List<String> endCallIds = [];
  final List<({String callId, bool onHold})> holdEvents = [];
  final List<({String callId, bool muted})> muteEvents = [];
  final List<({String callId, String key})> dtmfEvents = [];
  final List<({String callId, CallkeepAudioDevice device})> audioDeviceEvents = [];

  void Function(String callId)? onPerformStartCall;
  void Function(String callId)? onPerformAnswerCall;
  void Function(String callId)? onPerformEndCall;
  void Function(String callId, bool onHold)? onPerformSetHeld;
  void Function(String callId, bool muted)? onPerformSetMuted;
  void Function(String callId, String key)? onPerformSendDTMF;

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
    onPerformSetHeld?.call(callId, onHold);
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    muteEvents.add((callId: callId, muted: muted));
    onPerformSetMuted?.call(callId, muted);
    return Future.value(true);
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) {
    dtmfEvents.add((callId: callId, key: key));
    onPerformSendDTMF?.call(callId, key);
    return Future.value(true);
  }

  @override
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device) {
    audioDeviceEvents.add((callId: callId, device: device));
    return Future.value(true);
  }

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

// Poll until a Telecom connection for callId exists (or timeout). The
// connection is created asynchronously in :callkeep_core after
// reportNewIncomingCall, so it is not guaranteed to exist immediately.
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

// Poll until a Telecom connection for callId reaches the desired state (or
// timeout). Used to guard against the race where Telecom's CallsManager has
// not yet processed setActive() when the next reportNewIncomingCall arrives.
Future<void> _waitForConnectionState(
  String callId,
  CallkeepConnectionState targetState, {
  Duration timeout = const Duration(seconds: 10),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    final conn = await CallkeepConnections().getConnection(callId);
    if (conn != null && conn.state == targetState) return;
    await Future.delayed(const Duration(milliseconds: 100));
  }
  throw TimeoutException('$callId did not reach $targetState within timeout');
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
  // Full state machine: answer -> hold -> mute -> unmute -> unhold -> end
  // -------------------------------------------------------------------------

  group('full state machine: answer -> hold -> mute -> unmute -> unhold -> end', () {
    testWidgets('all 6 steps verified with individual latches', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Alice');

      // Step 1: answer
      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Step 2: hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // Step 3: mute (filter for muted: true to skip system-initiated false)
      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };
      await callkeep.setMuted(id, muted: true);
      await _waitFor(muteLatch.future, label: 'performSetMuted(true)');

      // Step 4: unmute
      final unmuteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && !muted && !unmuteLatch.isCompleted) unmuteLatch.complete();
      };
      await callkeep.setMuted(id, muted: false);
      await _waitFor(unmuteLatch.future, label: 'performSetMuted(false)');

      // Step 5: unhold
      final unholdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && !onHold && !unholdLatch.isCompleted) unholdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: false);
      await _waitFor(unholdLatch.future, label: 'performSetHeld(false)');

      // Step 6: end
      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall');

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

  group('mute while on hold (Android only)', () {
    testWidgets('setMuted(true) while held fires performSetMuted(true)', (WidgetTester _) async {
      if (!Platform.isAndroid) {
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

      // Hold first
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // Mute while held
      final muteLatch = Completer<({String callId, bool muted})>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) {
          muteLatch.complete((callId: cid, muted: muted));
        }
      };
      await callkeep.setMuted(id, muted: true);

      final event = await _waitFor(muteLatch.future, label: 'performSetMuted(true) while held');
      expect(event.callId, id);
      expect(event.muted, isTrue);
    });

    testWidgets('setMuted(false) while held fires performSetMuted(false)', (WidgetTester _) async {
      if (!Platform.isAndroid) {
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

      // Hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // Mute first
      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };
      await callkeep.setMuted(id, muted: true);
      await _waitFor(muteLatch.future, label: 'performSetMuted(true)');

      // Unmute while held
      final unmuteLatch = Completer<({String callId, bool muted})>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && !muted && !unmuteLatch.isCompleted) {
          unmuteLatch.complete((callId: cid, muted: muted));
        }
      };
      await callkeep.setMuted(id, muted: false);

      final event = await _waitFor(unmuteLatch.future, label: 'performSetMuted(false) while held');
      expect(event.callId, id);
      expect(event.muted, isFalse);
    });
  });

  // -------------------------------------------------------------------------
  // DTMF while on hold (Android only)
  // -------------------------------------------------------------------------

  group('DTMF while on hold (Android only)', () {
    testWidgets("sendDTMF('5') while held fires performSendDTMF", (WidgetTester _) async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Dan');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // DTMF while held
      final dtmfLatch = Completer<({String callId, String key})>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id && !dtmfLatch.isCompleted) dtmfLatch.complete((callId: cid, key: key));
      };
      await callkeep.sendDTMF(id, '5');

      final event = await _waitFor(dtmfLatch.future, label: 'performSendDTMF while held');
      expect(event.callId, id);
      expect(event.key, '5');
    });
  });

  // -------------------------------------------------------------------------
  // Two-call hold swap (Android only)
  // -------------------------------------------------------------------------

  group('two-call hold swap (Android only)', () {
    testWidgets('hold call1, answer call2, unhold call1 produces correct holdEvents', (WidgetTester _) async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id1 = _nextId();
      final id2 = _nextId();

      await callkeep.reportNewIncomingCall(id1, _handle1, displayName: 'Eve');

      // Answer id1
      final answer1Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id1 && !answer1Latch.isCompleted) answer1Latch.complete();
      };
      await callkeep.answerCall(id1);
      await _waitFor(answer1Latch.future, label: 'performAnswerCall id1');

      // Wait for id1's Telecom connection to reach ACTIVE before reporting id2.
      // Telecom refuses a second incoming self-managed call while the first is
      // still RINGING in its CallsManager. performAnswerCall fires before
      // Telecom processes setActive(), so we poll here to close that race.
      await _waitForConnectionState(id1, CallkeepConnectionState.stateActive);

      // Report id2
      await callkeep.reportNewIncomingCall(id2, _handle2, displayName: 'Frank');

      // Wait for id2's Telecom connection to be created before proceeding.
      // The connection is built asynchronously in :callkeep_core and may not
      // exist yet by the time answerCall is called.
      await _waitForConnection(id2);

      // Hold id1
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id1 && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id1, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld id1 hold');

      // Answer id2
      final answer2Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id2 && !answer2Latch.isCompleted) answer2Latch.complete();
      };
      await callkeep.answerCall(id2);
      await _waitFor(answer2Latch.future, label: 'performAnswerCall id2');

      // Unhold id1
      final unholdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id1 && !onHold && !unholdLatch.isCompleted) unholdLatch.complete();
      };
      await callkeep.setHeld(id1, onHold: false);
      await _waitFor(unholdLatch.future, label: 'performSetHeld id1 unhold');

      // Verify hold events for id1: hold(true) then hold(false)
      final id1HoldEvents = delegate.holdEvents.where((e) => e.callId == id1).toList();
      expect(id1HoldEvents.length, greaterThanOrEqualTo(2));
      expect(id1HoldEvents.first.onHold, isTrue);
      expect(id1HoldEvents.last.onHold, isFalse);

      // id2 was answered
      expect(delegate.answerCallIds.contains(id2), isTrue);
    });

    testWidgets('DTMF on call2 while call1 is held routes only to call2', (WidgetTester _) async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id1 = _nextId();
      final id2 = _nextId();

      await callkeep.reportNewIncomingCall(id1, _handle1, displayName: 'Grace');

      final answer1Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id1 && !answer1Latch.isCompleted) answer1Latch.complete();
      };
      await callkeep.answerCall(id1);
      await _waitFor(answer1Latch.future, label: 'performAnswerCall id1');

      // Wait for id1's Telecom connection to reach ACTIVE before reporting id2.
      // Telecom refuses a second incoming self-managed call while the first is
      // still RINGING in its CallsManager. performAnswerCall fires before
      // Telecom processes setActive(), so we poll here to close that race.
      await _waitForConnectionState(id1, CallkeepConnectionState.stateActive);

      await callkeep.reportNewIncomingCall(id2, _handle2, displayName: 'Hank');
      await _waitForConnection(id2);

      // Hold id1
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id1 && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id1, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld id1');

      // Answer id2
      final answer2Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id2 && !answer2Latch.isCompleted) answer2Latch.complete();
      };
      await callkeep.answerCall(id2);
      await _waitFor(answer2Latch.future, label: 'performAnswerCall id2');

      // DTMF on id2 only
      final dtmfLatch = Completer<({String callId, String key})>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id2 && !dtmfLatch.isCompleted) dtmfLatch.complete((callId: cid, key: key));
      };
      await callkeep.sendDTMF(id2, '7');

      final event = await _waitFor(dtmfLatch.future, label: 'performSendDTMF id2');
      expect(event.callId, id2);

      // id1 must not have received DTMF
      expect(delegate.dtmfEvents.any((e) => e.callId == id1), isFalse);
    });
  });

  // -------------------------------------------------------------------------
  // Nested controls: mute then hold (Android only)
  // -------------------------------------------------------------------------

  group('nested controls: mute then hold (Android only)', () {
    testWidgets('mute then hold then end fires each callback exactly once', (WidgetTester _) async {
      if (!Platform.isAndroid) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Irene');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Mute
      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };
      await callkeep.setMuted(id, muted: true);
      await _waitFor(muteLatch.future, label: 'performSetMuted(true)');

      // Hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld(true)');

      // End
      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall');

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
