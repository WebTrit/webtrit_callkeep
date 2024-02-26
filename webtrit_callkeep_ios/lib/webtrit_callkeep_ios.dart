import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The iOS implementation of [WebtritCallkeepPlatform].
class WebtritCallkeepIOS extends WebtritCallkeepPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('webtrit_callkeep_ios');

  /// Registers this class as the default instance of [WebtritCallkeepPlatform]
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeepIOS();
  }

  @override
  Future<String?> getPlatformName() {
    return methodChannel.invokeMethod<String>('getPlatformName');
  }
}
