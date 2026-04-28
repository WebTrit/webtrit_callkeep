import 'package:logging/logging.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'bootstrap.dart';

final _log = Logger('Isolates');

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
