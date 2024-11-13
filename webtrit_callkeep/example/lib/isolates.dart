import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

@pragma('vm:entry-point')
Future<void> sideIsolateCallbackHandle() async {
  CallkeepBackgroundService().startService();
  logIsolateI('Side isolate callback handle');
}

@pragma('vm:entry-point')
Future<void> onStartForegroundService(CallkeepServiceStatus status) async {
  logIsolateI('Callkeep sync status: $status');
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
  if (kIsWeb) {
    print("logIsolateI web not supported");
  } else {
    print('[$message] Isolate ID: ${Isolate.current.hashCode}');
  }
}
