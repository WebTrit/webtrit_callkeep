import 'dart:isolate' show ReceivePort;
import 'dart:ui' show IsolateNameServer;

import 'package:logging/logging.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'app/constants.dart';
import 'bootstrap.dart';

final _log = Logger('Isolates');

/// Port name used by integration tests to inject signaling commands into the
/// background isolate.  The background isolate registers a ReceivePort under
/// this name so the test (main) isolate can send incomingCall / endCall /
/// endCalls messages that are executed on the correct Flutter engine messenger.
const signalingServiceCommandPortName = 'webtrit_callkeep.signaling_test';

@pragma('vm:entry-point')
Future<void> onStartForegroundService(CallkeepServiceStatus status, CallkeepIncomingCallMetadata? metadata) async {
  initializeLogs();
  _log.info('onStartForegroundService: $status, metadata: $metadata');

  // Register a command port so integration tests (and other callers) can
  // inject signaling events from outside the background isolate.  Only
  // registers once per isolate lifetime; subsequent invocations are no-ops.
  if (IsolateNameServer.lookupPortByName(signalingServiceCommandPortName) == null) {
    final port = ReceivePort();
    IsolateNameServer.registerPortWithName(port.sendPort, signalingServiceCommandPortName);
    port.listen((dynamic message) async {
      if (message is! Map) return;
      try {
        switch (message['action'] as String?) {
          case 'incomingCall':
            await BackgroundSignalingService().incomingCall(
              message['callId'] as String,
              CallkeepHandle(
                type: CallkeepHandleType.values.byName(message['handleType'] as String),
                value: message['handleValue'] as String,
              ),
              displayName: message['displayName'] as String?,
              hasVideo: (message['hasVideo'] as bool?) ?? false,
            );
          case 'endCall':
            await BackgroundSignalingService().endCall(message['callId'] as String);
          case 'endCalls':
            await BackgroundSignalingService().endCalls();
        }
      } catch (_) {}
    });
  }
}

@pragma('vm:entry-point')
Future<void> onChangedLifecycle(CallkeepServiceStatus status) async {
  initializeLogs();
  _log.info('onChangedLifecycle: $status');

  if (status.lifecycleEvent == CallkeepLifecycleEvent.onStop) {
    BackgroundSignalingService().endCall(call1Identifier);
  }

  return Future.value();
}

@pragma('vm:entry-point')
Future<void> onPushNotificationCallback(
    CallkeepPushNotificationSyncStatus status, CallkeepIncomingCallMetadata? metadata) async {
  initializeLogs();
  _log.info('onPushNotificationCallback: $status, metadata: $metadata');

  if (status == CallkeepPushNotificationSyncStatus.synchronizeCallStatus) {
    Future.delayed(Duration(seconds: 3), () {
      _log.info('Ending call after 3 seconds');
      BackgroundPushNotificationService().endCall(call1Identifier);
    });
  } else {
    _log.info('onPushNotificationCallback: unknown');
  }

  return Future.value();
}
