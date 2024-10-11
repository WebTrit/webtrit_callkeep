import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'package:hydrated_bloc/hydrated_bloc.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

Future<void> bootstrap(FutureOr<Widget> Function() builder) async {
  final logger = Logger('bootstrap');

  await runZonedGuarded(
    () async {
      WidgetsFlutterBinding.ensureInitialized();

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
