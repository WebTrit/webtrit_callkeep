import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'helpers/callkeep_test_helpers.dart';

// ---------------------------------------------------------------------------
// Minimal no-op delegate
// ---------------------------------------------------------------------------

class _NoOpDelegate implements CallkeepDelegate {
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
  Future<bool> performStartCall(String callId, CallkeepHandle handle, String? displayName, bool video) =>
      Future.value(true);

  @override
  Future<bool> performAnswerCall(String callId) => Future.value(true);

  @override
  Future<bool> performEndCall(String callId) => Future.value(true);

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
// Tests
// ---------------------------------------------------------------------------

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late Callkeep callkeep;
  var globalTearDownNeeded = true;

  setUp(() async {
    globalTearDownNeeded = true;
    callkeep = Callkeep();
    await callkeep.setUp(kTestOptions);
    callkeep.setDelegate(_NoOpDelegate());
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
  // reportEndCall - all reasons
  // -------------------------------------------------------------------------

  group('reportEndCall - all reasons', () {
    testWidgets('reportEndCall with failed on ringing call completes', (WidgetTester _) async {
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Alice');
      await expectLater(
        callkeep.reportEndCall(id, 'Alice', CallkeepEndCallReason.failed),
        completes,
      );
    });

    testWidgets('reportEndCall with answeredElsewhere on ringing call completes', (WidgetTester _) async {
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Bob');
      await expectLater(
        callkeep.reportEndCall(id, 'Bob', CallkeepEndCallReason.answeredElsewhere),
        completes,
      );
    });

    testWidgets('reportEndCall with declinedElsewhere on ringing call completes', (WidgetTester _) async {
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Carol');
      await expectLater(
        callkeep.reportEndCall(id, 'Carol', CallkeepEndCallReason.declinedElsewhere),
        completes,
      );
    });

    testWidgets('reportEndCall with missed on ringing call completes', (WidgetTester _) async {
      final id = nextTestId();
      await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Dan');
      await expectLater(
        callkeep.reportEndCall(id, 'Dan', CallkeepEndCallReason.missed),
        completes,
      );
    });

    testWidgets('all six CallkeepEndCallReason values in a loop complete without exception', (WidgetTester _) async {
      for (final reason in CallkeepEndCallReason.values) {
        final id = nextTestId();
        await callkeep.reportNewIncomingCall(id, kTestHandle1, displayName: 'Test');
        // Wait for the call to be promoted to the tracker (DidPushIncomingCall
        // broadcast received) before ending it. This ensures the connection
        // exists in Telecom before reportEndCall is called.
        await waitForConnection(id);
        await expectLater(
          callkeep.reportEndCall(id, 'Test', reason),
          completes,
        );
        // Wait for Telecom to fully process the disconnect (observable via the
        // tracker's connection map being cleared) before the next
        // addNewIncomingCall. This replaces a fixed 500ms hack with a proper
        // observable signal.
        await waitForConnectionGone(id);
      }
    });
  });
}
