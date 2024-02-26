import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The Linux implementation of [WebtritCallkeepPlatform].
class WebtritCallkeepLinux extends WebtritCallkeepPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('webtrit_callkeep_linux');

  /// Registers this class as the default instance of [WebtritCallkeepPlatform]
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeepLinux();
  }

  @override
  Future<String?> getPlatformName() {
    return methodChannel.invokeMethod<String>('getPlatformName');
  }
}
