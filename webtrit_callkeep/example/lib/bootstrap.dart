import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'package:hydrated_bloc/hydrated_bloc.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'isolates.dart' as isolate;

Future<void> bootstrap(FutureOr<Widget> Function() builder) async {
  final logger = Logger('bootstrap');
  WidgetsFlutterBinding.ensureInitialized();
  isolate.logIsolateI('Main isolate');

  await Permission.notification.request();

  CallkeepBackgroundService.initializeCallback(
    onStart: isolate.onStartForegroundService,
    onChangedLifecycle: isolate.onChangedLifecycle,
  );

  WebtritCallkeepLogs().setLogsDelegate(CallkeepLogs());

  await runZonedGuarded(
    () async {
      HydratedBloc.storage = await HydratedStorage.build(
        storageDirectory: kIsWeb ? HydratedStorage.webStorageDirectory : await getTemporaryDirectory(),
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
