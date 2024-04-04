import 'package:webtrit_callkeep_platform_interface/src/annotation/annotation.dart';

/// This class is used to define the constant values for the call data.
@MultiplatformConstFile()
class CallDataConst {
  /// Display name of the call.
  @MultiplatformConstField()
  static const String displayName = 'displayName';

  /// Call Id of the call.
  @MultiplatformConstField()
  static const String callId = 'callId';

  /// Call UUID of the call.
  @MultiplatformConstField()
  static const String callUuid = 'callUUID';

  /// Handle object of the call.
  @MultiplatformConstField()
  static const String handleValue = 'handleValue';

  /// Calle number of the call.
  @MultiplatformConstField()
  static const String number = 'number';

  /// Celee has video.
  @MultiplatformConstField()
  static const String hasVideo = 'hasVideo';

  /// Is speaker on.
  @MultiplatformConstField()
  static const String hasSpeaker = 'hasSpeaker';

  /// Is call muted.
  @MultiplatformConstField()
  static const String hasMute = 'hasMute';

  /// Is call helt.
  @MultiplatformConstField()
  static const String hasHold = 'hasHold';

  /// Is call incoming.
  @MultiplatformConstField()
  static const String dtmf = 'dtmf';
}
