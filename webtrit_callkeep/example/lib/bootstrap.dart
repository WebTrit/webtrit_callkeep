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

      // Configures how incoming SMS messages should be parsed on the Android side.
      //
      // Parameters:
      // - prefix: A required SMS message prefix used to filter out irrelevant messages.
      //           Only messages starting with this prefix will be parsed.
      // - regexPattern: ICU-compatible regular expression that extracts the required
      //           call metadata from the SMS body. Must contain exactly four capturing
      //           groups in the following order: callId, handle, displayName (URL-encoded), and hasVideo (true|false).
      //
      // Example accepted message format:
      // "<#> CALLHOME: https://app.webtrit.com/call?callId=abc123&handle=380971112233&displayName=John%20Doe&hasVideo=true"
      await AndroidCallkeepServices.smsReceptionConfig.configureReceivedSms(
        prefix: "<#> CALLHOME:",
        regexPattern:
            r'https:\/\/app\.webtrit\.com\/call\?callId=([^&]+)&handle=([^&]+)&displayName=([^&]+)&hasVideo=(true|false)',
      );

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
