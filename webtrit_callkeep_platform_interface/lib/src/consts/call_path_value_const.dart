import 'package:webtrit_callkeep_platform_interface/src/annotation/annotation.dart';

/// This class is used to define the constant values for the call path.
@MultiplatformConstFile()
class CallPathValueConst {
  /// Default call path value.
  @MultiplatformConstField()
  static const String callPathDefault = '/main/call';

  /// Default main path value.
  @MultiplatformConstField()
  static const String mainPathDefault = '/main';
}
