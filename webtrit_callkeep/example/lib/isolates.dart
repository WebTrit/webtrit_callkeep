import 'dart:isolate' show ReceivePort;
import 'dart:ui' show IsolateNameServer;

import 'package:logging/logging.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'bootstrap.dart';

final _log = Logger('Isolates');

/// Port name used by integration tests to inject signaling commands into the
/// background isolate.  The background isolate registers a ReceivePort under
/// this name so the test (main) isolate can send incomingCall / endCall /
/// endCalls messages that are executed on the correct Flutter engine messenger.
const signalingServiceCommandPortName = 'webtrit_callkeep.signaling_test';

@pragma('vm:entry-point')
Future<void> onPushNotificationCallback(CallkeepIncomingCallMetadata? metadata) async {
  initializeLogs();
  _log.info('onPushNotificationCallback: metadata: $metadata');

  final callId = metadata?.callId;
  if (callId == null || callId.isEmpty) {
    _log.warning('onPushNotificationCallback: callId is null or empty, skipping releaseCall');
    return;
  }

  Future.delayed(Duration(seconds: 3), () {
    _log.info('Ending call after 3 seconds');
    BackgroundPushNotificationService().releaseCall(callId);
  });
}
