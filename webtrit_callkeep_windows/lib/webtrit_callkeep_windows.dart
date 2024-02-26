import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The Windows implementation of [WebtritCallkeepPlatform].
class WebtritCallkeepWindows extends WebtritCallkeepPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('webtrit_callkeep_windows');

  /// Registers this class as the default instance of [WebtritCallkeepPlatform]
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeepWindows();
  }

  @override
  Future<String?> getPlatformName() {
    return methodChannel.invokeMethod<String>('getPlatformName');
  }
}
