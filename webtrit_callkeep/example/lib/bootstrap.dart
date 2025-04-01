import 'dart:async';

import 'package:flutter/material.dart';

import 'package:logging/logging.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'isolates.dart' as isolate;

final logger = Logger('bootstrap');

Future<void> bootstrap(FutureOr<Widget> Function() builder) async {
  await runZonedGuarded(
    () async {
      WidgetsFlutterBinding.ensureInitialized();

      initializeLogs();
      logger.info('bootstrap');

      await Permission.notification.request();

      AndroidCallkeepServices.backgroundSignalingBootstrapService.initializeCallback(isolate.onStartForegroundService);

      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .initializeCallback(isolate.onPushNotificationCallback);

      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .configurePushNotificationSignalingService(launchBackgroundIsolateEvenIfAppIsOpen: true);

      FlutterError.onError = (details) {
        logger.severe('FlutterError', details.exception, details.stack);
      };

      runApp(await builder());
    },
    (error, stackTrace) {
      logger.severe('runZonedGuarded', error, stackTrace);
    },
  );
}

class CallkeepLogs implements CallkeepLogsDelegate {
  final _logger = Logger('CallkeepLogs');

  @override
  void onLog(CallkeepLogType type, String tag, String message) {
    _logger.info('$tag $message');
  }
}

void initializeLogs() {
  hierarchicalLoggingEnabled = true;

  Logger.root.clearListeners();
  Logger.root.level = Level.ALL;

  Logger.root.onRecord.listen((record) {
    debugPrint('${record.time} [${record.level.name}] ${record.loggerName}: ${record.message}');
    if (record.error != null) {
      debugPrint('Error: ${record.error}');
    }
    if (record.stackTrace != null) {
      debugPrint('${record.stackTrace}');
    }
  });

  WebtritCallkeepLogs().setLogsDelegate(CallkeepLogs());
}
