import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The Web implementation of [WebtritCallkeepPlatform].
class WebtritCallkeepWeb extends WebtritCallkeepPlatform {
  /// Registers this class as the default instance of [WebtritCallkeepPlatform]
  static void registerWith([Object? registrar]) {
    WebtritCallkeepPlatform.instance = WebtritCallkeepWeb();
  }

  @override
  Future<String?> getPlatformName() async => 'Web';
}
