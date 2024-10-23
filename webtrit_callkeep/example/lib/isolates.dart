import 'dart:isolate';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

@pragma('vm:entry-point')
Future<void> sideIsolateCallbackHandle() async {
  CallkeepBackgroundService().startService();
  logIsolateI('Side isolate callback handle');
}

@pragma('vm:entry-point')
Future<void> onStartForegroundService(CallkeepServiceStatus status, Map<String, dynamic> data) async {
  logIsolateI('Callkeep sync status: $status, data: $data');
  await CallkeepBackgroundService().endAllBackgroundCalls();
  Future.delayed(Duration(seconds: 5), () {
    CallkeepBackgroundService().stopService();
  });
}

@pragma('vm:entry-point')
Future<void> onChangedLifecycle(CallkeepServiceStatus status) async {
  if (status.lifecycle == CallkeepLifecycleType.onStop) {
    CallkeepBackgroundService().stopService();
  } else {
    CallkeepBackgroundService().startService();
  }
}

void logIsolateI(String message) {
  print('[$message] Isolate ID: ${Isolate.current.hashCode}');
}
