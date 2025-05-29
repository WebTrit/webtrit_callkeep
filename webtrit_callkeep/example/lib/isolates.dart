import 'package:logging/logging.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'app/constants.dart';
import 'bootstrap.dart';
import 'package:fluttertoast/fluttertoast.dart';

final _log = Logger('Isolates');

@pragma('vm:entry-point')
Future<void> onStartForegroundService(CallkeepServiceStatus status) async {
  initializeLogs();
  _log.info('onStartForegroundService: $status');
  CallkeepConnections().cleanConnections();

  _log.info('Starting call after 3 seconds');
  Future.delayed(Duration(seconds: 3), () {
    Fluttertoast.showToast(msg: "Starting incoming call", toastLength: Toast.LENGTH_SHORT);
    BackgroundSignalingService().incomingCall(call1Identifier, call1Number);
  });

  _log.info('Starting call after 5 seconds');
  Future.delayed(Duration(seconds: 8), () {
    Fluttertoast.showToast(msg: "End incoming call", toastLength: Toast.LENGTH_SHORT);
    BackgroundSignalingService().endCall(call1Identifier);
  });

  return Future.value();
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
Future<void> onPushNotificationCallback(CallkeepPushNotificationSyncStatus status) async {
  initializeLogs();
  _log.info('onPushNotificationCallback: $status');

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
