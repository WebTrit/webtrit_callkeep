import 'package:webtrit_callkeep_platform_interface/src/annotation/annotation.dart';

@MultiplatformConstFile()
class CallDataConst {
  @MultiplatformConstField()
  static const String displayName = 'displayName';

  @MultiplatformConstField()
  static const String callId = 'callId';

  @MultiplatformConstField()
  static const String callUuid = 'callUUID';

  @MultiplatformConstField()
  static const String handleValue = 'handleValue';

  @MultiplatformConstField()
  static const String number = 'number';

  @MultiplatformConstField()
  static const String hasVideo = 'hasVideo';

  @MultiplatformConstField()
  static const String hasSpeaker = 'hasSpeaker';

  @MultiplatformConstField()
  static const String hasMute = 'hasMute';

  @MultiplatformConstField()
  static const String hasHold = 'hasHold';

  @MultiplatformConstField()
  static const String dtmf = 'dtmf';
}
