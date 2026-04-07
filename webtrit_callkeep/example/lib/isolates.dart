import 'package:logging/logging.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'app/constants.dart';
import 'bootstrap.dart';

final _log = Logger('Isolates');

@pragma('vm:entry-point')
Future<void> onPushNotificationCallback(CallkeepIncomingCallMetadata? metadata) async {
  initializeLogs();
  _log.info('onPushNotificationCallback: metadata: $metadata');

  Future.delayed(Duration(seconds: 3), () {
    _log.info('Ending call after 3 seconds');
    BackgroundPushNotificationService().releaseCall(metadata?.callId ?? '');
  });
}
