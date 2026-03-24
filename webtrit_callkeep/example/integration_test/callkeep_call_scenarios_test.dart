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

const _handle1 = CallkeepHandle.number('380000000000');
const _handle2 = CallkeepHandle.number('380000000001');

var _idCounter = 0;
String _nextId() => 'scenario-${_idCounter++}';

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
  void Function(String callId, CallkeepAudioDevice device)? onPerformAudioDeviceSet;

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
    onPerformAudioDeviceSet?.call(callId, device);
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

Future<void> _waitForConnectionGone(
  String callId, {
  Duration timeout = const Duration(seconds: 5),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    final conn = await CallkeepConnections().getConnection(callId);
    if (conn == null) return;
    await Future.delayed(const Duration(milliseconds: 100));
  }
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
  // Use case: incoming call answered
  //
  // Matches CallBloc flow:
  //   incomingFromOffer → incomingSubmittedAnswer → incomingPerformingStarted
  //   → incomingInitializingMedia → incomingAnswering → connected
  //
  // The callkeep layer is responsible for routing performAnswerCall to the
  // delegate so the app can initialize WebRTC and send the SDP answer.
  // -------------------------------------------------------------------------

  group('incoming call answered', () {
    testWidgets('answerCall fires performAnswerCall with the correct callId', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Alice');

      final latch = Completer<String>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await callkeep.answerCall(id);

      final answered = await _waitFor(latch.future, label: 'performAnswerCall');
      expect(answered, id);
    });

    testWidgets('video incoming call: answerCall fires performAnswerCall', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Bob', hasVideo: true);

      final latch = Completer<String>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await callkeep.answerCall(id);

      final answered = await _waitFor(latch.future, label: 'performAnswerCall (video)');
      expect(answered, id);
    });
  });

  // -------------------------------------------------------------------------
  // Use case: incoming call declined before answer
  //
  // Matches CallBloc flow:
  //   incomingFromOffer → user declines → performEndCall → disconnecting
  //
  // The bloc sends a SIP BYE/decline via performEndCall. The callkeep layer
  // must route this to the delegate before tearing down any resources.
  // -------------------------------------------------------------------------

  group('incoming call declined before answer', () {
    testWidgets('endCall on ringing call fires performEndCall', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Charlie');

      final latch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await callkeep.endCall(id);

      final ended = await _waitFor(latch.future, label: 'performEndCall on decline');
      expect(ended, id);
      expect(delegate.answerCallIds, isEmpty);
    });

    testWidgets('endCall fires performEndCall and not performAnswerCall', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Dave');

      delegate.onPerformAnswerCall = (_) => fail('performAnswerCall must not fire on decline');

      final latch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      await callkeep.endCall(id);
      await _waitFor(latch.future, label: 'performEndCall on decline');

      expect(delegate.answerCallIds, isEmpty);
    });
  });

  // -------------------------------------------------------------------------
  // Use case: incoming call answered then hung up
  //
  // Matches CallBloc flow:
  //   connected → user taps end → performEndCall → disconnecting → removed
  // -------------------------------------------------------------------------

  group('incoming call answered then hung up', () {
    testWidgets('answer then endCall fires performEndCall after performAnswerCall', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Eve');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final endLatch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };
      await callkeep.endCall(id);

      final ended = await _waitFor(endLatch.future, label: 'performEndCall after answer');
      expect(ended, id);
      expect(delegate.answerCallIds.where((c) => c == id).length, 1);
      expect(delegate.endCallIds.where((c) => c == id).length, 1);
    });
  });

  // -------------------------------------------------------------------------
  // Use case: remote end (reportEndCall)
  //
  // Matches CallBloc flow:
  //   connected → remote sends BYE → _CallSignalingEventHangup →
  //   performEndCall (to confirm on platform) → disconnecting → removed
  //
  // When the server hangs up, the app calls reportEndCall to inform the
  // native layer that the call is over.
  // -------------------------------------------------------------------------

  group('remote end (reportEndCall)', () {
    testWidgets('reportEndCall after answer completes without error', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Frank');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Simulate server BYE received: app informs native layer
      await callkeep.reportEndCall(id, 'Frank', CallkeepEndCallReason.remoteEnded);
    });

    testWidgets('reportEndCall with unanswered reason completes without error', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Grace');

      // Simulate no-answer / missed call
      await callkeep.reportEndCall(id, 'Grace', CallkeepEndCallReason.unanswered);
    });
  });

  // -------------------------------------------------------------------------
  // Use case: call on hold / unhold
  //
  // Matches CallBloc flow:
  //   connected → CallControlEvent.setHeld(true) → performSetHeld(true)
  //   → held flag set on ActiveCall
  //   connected → CallControlEvent.setHeld(false) → performSetHeld(false)
  // -------------------------------------------------------------------------

  group('call hold / unhold', () {
    testWidgets('setHeld true fires performSetHeld(onHold: true)', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Hank');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final holdLatch = Completer<({String callId, bool onHold})>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && !holdLatch.isCompleted) holdLatch.complete((callId: cid, onHold: onHold));
      };

      await callkeep.setHeld(id, onHold: true);

      final event = await _waitFor(holdLatch.future, label: 'performSetHeld(true)');
      expect(event.callId, id);
      expect(event.onHold, isTrue);
    });

    testWidgets('setHeld false fires performSetHeld(onHold: false)', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Irene');

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

      // Then unhold
      final unholdLatch = Completer<({String callId, bool onHold})>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && !onHold && !unholdLatch.isCompleted) {
          unholdLatch.complete((callId: cid, onHold: onHold));
        }
      };
      await callkeep.setHeld(id, onHold: false);

      final event = await _waitFor(unholdLatch.future, label: 'performSetHeld(false)');
      expect(event.callId, id);
      expect(event.onHold, isFalse);
    });

    testWidgets('hold/unhold sequence preserves correct call id', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Jack');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final events = <({String callId, bool onHold})>[];
      final allDone = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id) {
          events.add((callId: cid, onHold: onHold));
          if (events.length == 2 && !allDone.isCompleted) allDone.complete();
        }
      };

      await callkeep.setHeld(id, onHold: true);
      await callkeep.setHeld(id, onHold: false);
      await _waitFor(allDone.future, label: 'both hold events');

      expect(events[0].onHold, isTrue);
      expect(events[1].onHold, isFalse);
    });
  });

  // -------------------------------------------------------------------------
  // Use case: mute / unmute
  //
  // Matches CallBloc flow:
  //   connected → CallControlEvent.setMuted(true) → performSetMuted(true)
  //   → muted flag set on ActiveCall → audio track disabled
  // -------------------------------------------------------------------------

  group('mute / unmute', () {
    testWidgets('setMuted true fires performSetMuted(muted: true)', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Kate');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Android fires performSetMuted(false) on answer as a system-initiated
      // "mic is live" notification. Filter to only resolve on muted: true so
      // that initial system callback does not prematurely complete the latch.
      final muteLatch = Completer<({String callId, bool muted})>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) {
          muteLatch.complete((callId: cid, muted: muted));
        }
      };

      await callkeep.setMuted(id, muted: true);

      final event = await _waitFor(muteLatch.future, label: 'performSetMuted(true)');
      expect(event.callId, id);
      expect(event.muted, isTrue);
    });

    testWidgets('setMuted false fires performSetMuted(muted: false)', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Leo');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      // Mute first
      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };
      await callkeep.setMuted(id, muted: true);
      await _waitFor(muteLatch.future, label: 'performSetMuted(true)');

      // Then unmute
      final unmuteLatch = Completer<({String callId, bool muted})>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && !muted && !unmuteLatch.isCompleted) {
          unmuteLatch.complete((callId: cid, muted: muted));
        }
      };
      await callkeep.setMuted(id, muted: false);

      final event = await _waitFor(unmuteLatch.future, label: 'performSetMuted(false)');
      expect(event.muted, isFalse);
    });

    testWidgets('mute does not fire performSetMuted for a different call', (WidgetTester _) async {
      // Tests call isolation: muting call A must not trigger performSetMuted
      // for call B. Uses a single answered call to avoid Telecom concurrency
      // limits that prevent reliably answering two calls simultaneously.
      final id = _nextId();
      final otherId = 'other-${_nextId()}'; // never registered
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Mia');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final muteLatch = Completer<void>();
      delegate.onPerformSetMuted = (cid, muted) {
        if (cid == id && muted && !muteLatch.isCompleted) muteLatch.complete();
      };

      await callkeep.setMuted(id, muted: true);
      await _waitFor(muteLatch.future, label: 'performSetMuted id');

      expect(
        delegate.muteEvents.any((e) => e.callId == otherId),
        isFalse,
        reason: 'muting one call must not trigger performSetMuted for a different callId',
      );
    });
  });

  // -------------------------------------------------------------------------
  // Use case: DTMF tones
  //
  // Matches CallBloc flow:
  //   connected → CallControlEvent.sentDTMF(key) → performSendDTMF(key)
  //   → sent to remote via SIP INFO or RTP DTMF
  // -------------------------------------------------------------------------

  group('DTMF tones', () {
    testWidgets('sendDTMF fires performSendDTMF with the correct key', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Olivia');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final dtmfLatch = Completer<({String callId, String key})>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id && !dtmfLatch.isCompleted) dtmfLatch.complete((callId: cid, key: key));
      };

      await callkeep.sendDTMF(id, '5');

      final event = await _waitFor(dtmfLatch.future, label: 'performSendDTMF');
      expect(event.callId, id);
      expect(event.key, '5');
    });

    testWidgets('multiple DTMF digits are each delivered in order', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Paul');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final receivedKeys = <String>[];
      final allDone = Completer<void>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id) {
          receivedKeys.add(key);
          if (receivedKeys.length == 3 && !allDone.isCompleted) allDone.complete();
        }
      };

      await callkeep.sendDTMF(id, '1');
      await callkeep.sendDTMF(id, '2');
      await callkeep.sendDTMF(id, '3');

      await _waitFor(allDone.future, label: 'all DTMF events');
      expect(receivedKeys, equals(['1', '2', '3']));
    });
  });

  // -------------------------------------------------------------------------
  // Use case: audio device selection
  //
  // Matches CallBloc flow:
  //   CallControlEvent.audioDeviceSet → _CallPerformEventAudioDeviceSet
  //   → performAudioDeviceSet → platform routes audio
  // -------------------------------------------------------------------------

  group('audio device selection (Android only)', () {
    testWidgets('setAudioDevice completes without error on an answered call', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      // performAudioDeviceSet is a system-driven command callback (Android
      // fires it when IT routes audio — e.g. at answer time — not in response
      // to our setAudioDevice call).  We therefore verify that setAudioDevice
      // completes without error rather than waiting for the delegate callback.
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Quinn');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      await Future.delayed(const Duration(milliseconds: 300));

      final speakerDevice = CallkeepAudioDevice(
        type: CallkeepAudioDeviceType.speaker,
        id: null,
        name: 'Speaker',
      );
      await expectLater(callkeep.setAudioDevice(id, speakerDevice), completes);
    });
  });

  // -------------------------------------------------------------------------
  // Use case: outgoing call lifecycle (Android only)
  //
  // Matches CallBloc flow:
  //   CallControlEvent.started → outgoingCreated → outgoingInitializingMedia
  //   → outgoingOfferPreparing → outgoingOfferSent → outgoingRinging
  //   → connected → endCall → disconnecting
  //
  // On Android, startCall triggers the system dialer via TelecomManager which
  // calls back performStartCall. After that, the app progresses the call via
  // reportConnectingOutgoingCall / reportConnectedOutgoingCall.
  // -------------------------------------------------------------------------

  group('outgoing call lifecycle (Android only)', () {
    testWidgets('startCall fires performStartCall', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      final id = _nextId();

      final latch = Completer<String>();
      delegate.onPerformStartCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      final err = await callkeep.startCall(id, _handle1, displayNameOrContactIdentifier: 'Rachel');
      expect(err, isNull, reason: 'startCall must succeed');

      final started = await _waitFor(latch.future, label: 'performStartCall');
      expect(started, id);
    });

    testWidgets('startCall → reportConnectingOutgoingCall → reportConnectedOutgoingCall completes',
        (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = _nextId();

      final startLatch = Completer<String>();
      delegate.onPerformStartCall = (cid) {
        if (cid == id && !startLatch.isCompleted) startLatch.complete(cid);
      };

      await callkeep.startCall(id, _handle1, displayNameOrContactIdentifier: 'Sam');
      await _waitFor(startLatch.future, label: 'performStartCall');

      // Progress outgoing call state — matches outgoingRinging → connected in CallBloc
      await callkeep.reportConnectingOutgoingCall(id);
      await callkeep.reportConnectedOutgoingCall(id);

      // Clean up
      final endLatch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall outgoing');
      await callkeep.tearDown();
    });

    testWidgets('outgoing call endCall before answer fires performEndCall', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }

      globalTearDownNeeded = false;
      final id = _nextId();

      final startLatch = Completer<void>();
      delegate.onPerformStartCall = (cid) {
        if (cid == id && !startLatch.isCompleted) startLatch.complete();
      };

      await callkeep.startCall(id, _handle1, displayNameOrContactIdentifier: 'Tina');
      await _waitFor(startLatch.future, label: 'performStartCall');

      // User cancels before remote answers — matches CallControlEvent.ended
      // while call is in outgoingRinging state
      final endLatch = Completer<String>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete(cid);
      };
      await callkeep.endCall(id);

      final ended = await _waitFor(endLatch.future, label: 'performEndCall before answer');
      expect(ended, id);
      await callkeep.tearDown();
    });
  });

  // -------------------------------------------------------------------------
  // Use case: two simultaneous calls — hold one, answer another
  //
  // Matches CallBloc flow:
  //   call1 active → call2 arrives → system puts call1 on hold automatically
  //   → performSetHeld(call1, true) fires → call2 becomes current
  //
  // This scenario verifies that the platform correctly routes hold callbacks
  // to the right call ID when multiple calls exist.
  // -------------------------------------------------------------------------

  group('two simultaneous calls - hold one, answer another', () {
    testWidgets('explicit hold of first call fires performSetHeld before answering second', (WidgetTester _) async {
      // Android Telecom does not guarantee automatic hold when a second call
      // is answered — behavior is device-specific. This test uses an explicit
      // setHeld to validate the hold/answer sequence that CallBloc applies:
      //   1. App calls setHeld(id1, true) to put call1 on hold.
      //   2. App answers call2.
      final id1 = _nextId();
      final id2 = _nextId();

      await callkeep.reportNewIncomingCall(id1, _handle1, displayName: 'Uma');

      final answer1Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id1 && !answer1Latch.isCompleted) answer1Latch.complete();
      };
      await callkeep.answerCall(id1);
      await _waitFor(answer1Latch.future, label: 'performAnswerCall id1');

      await callkeep.reportNewIncomingCall(id2, _handle2, displayName: 'Victor');

      // Explicitly hold id1 — this is what CallBloc does before answering a
      // second call so that the audio routes correctly.
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id1 && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id1, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld id1');

      // Now answer id2. Wait for the Telecom connection to exist in
      // :callkeep_core before calling answerCall — the connection is created
      // asynchronously and answerCall fails silently if called too early.
      // On some OEM devices (e.g. Huawei), Telecom rejects the second incoming
      // call even when the first is active. Skip if the device does not support
      // concurrent self-managed calls.
      final conn2 = await _waitForConnection(id2);
      if (conn2 == null) {
        markTestSkipped('device does not support concurrent incoming calls');
        return;
      }
      final answer2Latch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id2 && !answer2Latch.isCompleted) answer2Latch.complete();
      };
      await callkeep.answerCall(id2);
      await _waitFor(answer2Latch.future, label: 'performAnswerCall id2');

      expect(delegate.holdEvents.any((e) => e.callId == id1 && e.onHold), isTrue);
      expect(delegate.answerCallIds.contains(id2), isTrue);
    });

    testWidgets('both calls are ended independently', (WidgetTester _) async {
      final id1 = _nextId();
      final id2 = _nextId();

      final err1 = await callkeep.reportNewIncomingCall(id1, _handle1, displayName: 'Wendy');
      final err2 = await callkeep.reportNewIncomingCall(id2, _handle2, displayName: 'Xavier');

      // On devices that do not support concurrent self-managed calls (standard
      // Android 11+, Huawei, other OEMs), the second call is rejected by Telecom
      // and never confirmed to Flutter, so performEndCall will only fire for the
      // accepted calls.
      final expectedIds = <String>{};
      if (err1 == null) expectedIds.add(id1);
      if (err2 == null) expectedIds.add(id2);

      if (expectedIds.isEmpty) {
        markTestSkipped('device rejected all incoming calls');
        return;
      }

      final endedIds = <String>[];
      final allEnded = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (expectedIds.contains(cid) && !endedIds.contains(cid)) {
          endedIds.add(cid);
          if (endedIds.length == expectedIds.length && !allEnded.isCompleted) allEnded.complete();
        }
      };

      await callkeep.endCall(id1);
      await callkeep.endCall(id2);

      await _waitFor(allEnded.future, label: 'both performEndCall');
      expect(endedIds, containsAll(expectedIds.toList()));
    });
  });

  // -------------------------------------------------------------------------
  // Use case: reportUpdateCall (display name resolution)
  //
  // Matches CallBloc flow:
  //   incoming push → contact resolver fetches display name →
  //   reportUpdateCall(callId, displayName: resolved) to update the native UI
  // -------------------------------------------------------------------------

  group('reportUpdateCall (display name update)', () {
    testWidgets('reportUpdateCall succeeds on an active incoming call', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Unknown');

      // Simulates ContactNameResolver completing and updating the call UI
      await callkeep.reportUpdateCall(id, displayName: 'Yara Smith');
    });

    testWidgets('reportUpdateCall with same name does not throw', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Zack');
      await callkeep.reportUpdateCall(id, displayName: 'Zack');
    });
  });

  // -------------------------------------------------------------------------
  // Use case: tearDown while calls active (cleanup on signaling disconnect)
  //
  // Matches CallBloc flow:
  //   network lost / signaling disconnect → _ResetStateEvent.completeCalls()
  //   → tearDown → performEndCall for each active call
  // -------------------------------------------------------------------------

  group('tearDown with active calls (signaling disconnect scenario)', () {
    testWidgets('tearDown fires performEndCall for an unanswered incoming call', (WidgetTester _) async {
      globalTearDownNeeded = false;
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Anna');

      final latch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await callkeep.tearDown();
      await _waitFor(latch.future, label: 'performEndCall on tearDown');

      expect(delegate.endCallIds.where((c) => c == id).length, 1);
    });

    testWidgets('tearDown fires performEndCall for an answered call', (WidgetTester _) async {
      globalTearDownNeeded = false;
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Bruno');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };

      await callkeep.tearDown();
      await _waitFor(endLatch.future, label: 'performEndCall on tearDown');

      expect(delegate.endCallIds.where((c) => c == id).length, 1);
    });
  });

  // -------------------------------------------------------------------------
  // Use case: call operations on unknown callId do not crash
  //
  // Matches CallBloc defensive check:
  //   if (state.retrieveActiveCall(callId) == null) return;
  //
  // The callkeep layer must not crash or fire spurious callbacks when asked
  // to operate on a call that was already ended or never existed.
  //
  // Platform notes:
  // - answerCall on a never-registered ID returns a non-null error.
  // - endCall / setHeld / setMuted on a never-registered ID return null on
  //   Android (the platform silently ignores unknown IDs for these operations).
  //   The important invariant is that no exception is thrown and no spurious
  //   delegate callbacks fire.
  // -------------------------------------------------------------------------

  group('operations on unknown or already-ended callId', () {
    testWidgets('endCall on nonexistent id does not throw', (WidgetTester _) async {
      // Android returns null for endCall on a never-registered callId.
      // The key invariant: no exception must be thrown.
      await expectLater(
        callkeep.endCall('nonexistent-${_nextId()}'),
        completes,
      );
    });

    testWidgets('answerCall on nonexistent id returns an error', (WidgetTester _) async {
      final err = await callkeep.answerCall('nonexistent-${_nextId()}');
      expect(err, isNotNull);
    });

    testWidgets('setHeld on nonexistent id does not throw', (WidgetTester _) async {
      // Android returns null for setHeld on a never-registered callId.
      await expectLater(
        callkeep.setHeld('nonexistent-${_nextId()}', onHold: true),
        completes,
      );
    });

    testWidgets('setMuted on nonexistent id does not throw', (WidgetTester _) async {
      // Android returns null for setMuted on a never-registered callId.
      await expectLater(
        callkeep.setMuted('nonexistent-${_nextId()}', muted: true),
        completes,
      );
    });

    testWidgets('endCall after call already ended returns an error', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Clara');

      final latch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(latch.future, label: 'first performEndCall');

      final secondErr = await callkeep.endCall(id);
      expect(secondErr, isNotNull);
    });

    testWidgets('answerCall on a call already ended via endCall returns error', (WidgetTester _) async {
      // Mirrors CallBloc defensive check: state.retrieveActiveCall returns null
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Dora');

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall');

      final err = await callkeep.answerCall(id);
      expect(err, isNotNull);
    });
  });

  // -------------------------------------------------------------------------
  // Operations after reportEndCall
  // -------------------------------------------------------------------------

  group('operations after reportEndCall', () {
    testWidgets('endCall after reportEndCall(remoteEnded) completes safely', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Ellis');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      await callkeep.reportEndCall(id, 'Ellis', CallkeepEndCallReason.remoteEnded);
      await _waitForConnectionGone(id);

      // Must not throw
      await expectLater(callkeep.endCall(id), completes);
    });

    testWidgets('setHeld after reportEndCall(remoteEnded) completes safely', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Flora');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      await callkeep.reportEndCall(id, 'Flora', CallkeepEndCallReason.remoteEnded);
      await _waitForConnectionGone(id);

      await expectLater(callkeep.setHeld(id, onHold: true), completes);
    });

    testWidgets('setMuted after reportEndCall(remoteEnded) completes safely', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Glen');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      await callkeep.reportEndCall(id, 'Glen', CallkeepEndCallReason.remoteEnded);
      await _waitForConnectionGone(id);

      await expectLater(callkeep.setMuted(id, muted: true), completes);
    });
  });

  // -------------------------------------------------------------------------
  // Additional DTMF tones (extend existing group)
  // -------------------------------------------------------------------------

  group('DTMF extended keys', () {
    testWidgets("DTMF key '0' fires performSendDTMF with '0'", (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Helen');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final dtmfLatch = Completer<String>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id && !dtmfLatch.isCompleted) dtmfLatch.complete(key);
      };
      await callkeep.sendDTMF(id, '0');

      final key = await _waitFor(dtmfLatch.future, label: 'performSendDTMF 0');
      expect(key, '0');
    });

    testWidgets("DTMF key '*' fires performSendDTMF with '*'", (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Ivan');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final dtmfLatch = Completer<String>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id && !dtmfLatch.isCompleted) dtmfLatch.complete(key);
      };
      await callkeep.sendDTMF(id, '*');

      final key = await _waitFor(dtmfLatch.future, label: 'performSendDTMF *');
      expect(key, '*');
    });

    testWidgets("DTMF key '#' fires performSendDTMF with '#'", (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Jane');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final dtmfLatch = Completer<String>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id && !dtmfLatch.isCompleted) dtmfLatch.complete(key);
      };
      await callkeep.sendDTMF(id, '#');

      final key = await _waitFor(dtmfLatch.future, label: 'performSendDTMF #');
      expect(key, '#');
    });

    testWidgets('DTMF keys A, B, C, D are each delivered in order', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Karl');

      final answerLatch = Completer<void>();
      delegate.onPerformAnswerCall = (cid) {
        if (cid == id && !answerLatch.isCompleted) answerLatch.complete();
      };
      await callkeep.answerCall(id);
      await _waitFor(answerLatch.future, label: 'performAnswerCall');

      final receivedKeys = <String>[];
      final allDone = Completer<void>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id) {
          receivedKeys.add(key);
          if (receivedKeys.length == 4 && !allDone.isCompleted) allDone.complete();
        }
      };

      await callkeep.sendDTMF(id, 'A');
      await callkeep.sendDTMF(id, 'B');
      await callkeep.sendDTMF(id, 'C');
      await callkeep.sendDTMF(id, 'D');

      await _waitFor(allDone.future, label: 'all DTMF A-D events');
      expect(receivedKeys, equals(['A', 'B', 'C', 'D']));
    });
  });

  // -------------------------------------------------------------------------
  // reportNewIncomingCall with no displayName
  // -------------------------------------------------------------------------

  group('reportNewIncomingCall with no displayName', () {
    testWidgets('reportNewIncomingCall with displayName omitted does not return error', (WidgetTester _) async {
      final id = _nextId();
      final err = await callkeep.reportNewIncomingCall(id, _handle1);
      expect(err, isNull);
    });

    testWidgets('reportNewIncomingCall with displayName null and hasVideo true does not return error',
        (WidgetTester _) async {
      final id = _nextId();
      final err = await callkeep.reportNewIncomingCall(id, _handle1, hasVideo: true);
      expect(err, isNull);
    });
  });

  // -------------------------------------------------------------------------
  // reportUpdateCall with handle and flags
  // -------------------------------------------------------------------------

  group('reportUpdateCall with handle and flags', () {
    testWidgets('reportUpdateCall with hasVideo=true completes', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Lena');
      await expectLater(callkeep.reportUpdateCall(id, hasVideo: true), completes);
    });

    testWidgets('reportUpdateCall with handle change completes', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Mike');
      await expectLater(
        callkeep.reportUpdateCall(id, handle: const CallkeepHandle.number('380000000099')),
        completes,
      );
    });

    testWidgets('reportUpdateCall with proximityEnabled=true completes', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Nora');
      await expectLater(callkeep.reportUpdateCall(id, proximityEnabled: true), completes);
    });

    testWidgets('reportUpdateCall with all fields set at once completes', (WidgetTester _) async {
      final id = _nextId();
      await callkeep.reportNewIncomingCall(id, _handle1, displayName: 'Oscar');
      await expectLater(
        callkeep.reportUpdateCall(
          id,
          handle: const CallkeepHandle.number('380000000088'),
          displayName: 'Oscar Updated',
          hasVideo: true,
          proximityEnabled: true,
        ),
        completes,
      );
    });
  });

  // -------------------------------------------------------------------------
  // Outgoing call extended (Android only)
  // -------------------------------------------------------------------------

  group('outgoing call extended (Android only)', () {
    testWidgets('startCall with hasVideo=true fires performStartCall', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;
      final id = _nextId();

      final latch = Completer<String>();
      delegate.onPerformStartCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      final err = await callkeep.startCall(id, _handle1, displayNameOrContactIdentifier: 'Pat', hasVideo: true);
      expect(err, isNull, reason: 'startCall with hasVideo must succeed');

      final started = await _waitFor(latch.future, label: 'performStartCall hasVideo');
      expect(started, id);

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall');
      await callkeep.tearDown();
    });

    testWidgets('startCall with proximityEnabled=true completes', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;
      final id = _nextId();

      final latch = Completer<void>();
      delegate.onPerformStartCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete();
      };

      await expectLater(
        callkeep.startCall(id, _handle1, displayNameOrContactIdentifier: 'Quinn', proximityEnabled: true),
        completes,
      );

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      try {
        await _waitFor(endLatch.future, label: 'performEndCall');
      } catch (_) {}
      await callkeep.tearDown();
    });

    testWidgets('full outgoing sequence: start + connect + hold + DTMF + unhold + end', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;
      final id = _nextId();

      final startLatch = Completer<void>();
      delegate.onPerformStartCall = (cid) {
        if (cid == id && !startLatch.isCompleted) startLatch.complete();
      };
      await callkeep.startCall(id, _handle1, displayNameOrContactIdentifier: 'Rose');
      await _waitFor(startLatch.future, label: 'performStartCall');

      await callkeep.reportConnectingOutgoingCall(id);
      await callkeep.reportConnectedOutgoingCall(id);

      // Hold
      final holdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && onHold && !holdLatch.isCompleted) holdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: true);
      await _waitFor(holdLatch.future, label: 'performSetHeld(true) outgoing');

      // DTMF
      final dtmfLatch = Completer<String>();
      delegate.onPerformSendDTMF = (cid, key) {
        if (cid == id && !dtmfLatch.isCompleted) dtmfLatch.complete(key);
      };
      await callkeep.sendDTMF(id, '9');
      await _waitFor(dtmfLatch.future, label: 'performSendDTMF outgoing');

      // Unhold
      final unholdLatch = Completer<void>();
      delegate.onPerformSetHeld = (cid, onHold) {
        if (cid == id && !onHold && !unholdLatch.isCompleted) unholdLatch.complete();
      };
      await callkeep.setHeld(id, onHold: false);
      await _waitFor(unholdLatch.future, label: 'performSetHeld(false) outgoing');

      // End
      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall outgoing full');
      await callkeep.tearDown();
    });
  });

  // -------------------------------------------------------------------------
  // Handle type diversity (Android only)
  // -------------------------------------------------------------------------

  group('handle type diversity (Android only)', () {
    testWidgets('reportNewIncomingCall with CallkeepHandle.generic reports without error', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      final err = await callkeep.reportNewIncomingCall(
        id,
        const CallkeepHandle.generic('generic-user-id'),
        displayName: 'Steve',
      );
      expect(err, isNull);
    });

    testWidgets('reportNewIncomingCall with CallkeepHandle.email reports without error', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      final id = _nextId();
      final err = await callkeep.reportNewIncomingCall(
        id,
        const CallkeepHandle.email('test@example.com'),
        displayName: 'Tina',
      );
      expect(err, isNull);
    });

    testWidgets('startCall with CallkeepHandle.generic fires performStartCall', (WidgetTester _) async {
      if (kIsWeb || !defaultTargetPlatform == TargetPlatform.android) {
        markTestSkipped('Android only');
        return;
      }
      globalTearDownNeeded = false;
      final id = _nextId();

      final latch = Completer<String>();
      delegate.onPerformStartCall = (cid) {
        if (cid == id && !latch.isCompleted) latch.complete(cid);
      };

      final err = await callkeep.startCall(
        id,
        const CallkeepHandle.generic('sip:user@domain.com'),
        displayNameOrContactIdentifier: 'Uma',
      );
      expect(err, isNull, reason: 'startCall with generic handle must succeed');

      final started = await _waitFor(latch.future, label: 'performStartCall generic handle');
      expect(started, id);

      final endLatch = Completer<void>();
      delegate.onPerformEndCall = (cid) {
        if (cid == id && !endLatch.isCompleted) endLatch.complete();
      };
      await callkeep.endCall(id);
      await _waitFor(endLatch.future, label: 'performEndCall');
      await callkeep.tearDown();
    });
  });
}
