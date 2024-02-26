import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:webtrit_callkeep_platform_interface/src/method_channel_webtrit_callkeep.dart';

/// The interface that implementations of webtrit_callkeep must implement.
///
/// Platform implementations should extend this class
/// rather than implement it as `WebtritCallkeep`.
/// Extending this class (using `extends`) ensures that the subclass will get
/// the default implementation, while platform implementations that `implements`
///  this interface will be broken by newly added [WebtritCallkeepPlatform] methods.
abstract class WebtritCallkeepPlatform extends PlatformInterface {
  /// Constructs a WebtritCallkeepPlatform.
  WebtritCallkeepPlatform() : super(token: _token);

  static final Object _token = Object();

  static WebtritCallkeepPlatform _instance = MethodChannelWebtritCallkeep();

  /// The default instance of [WebtritCallkeepPlatform] to use.
  ///
  /// Defaults to [MethodChannelWebtritCallkeep].
  static WebtritCallkeepPlatform get instance => _instance;

  /// Platform-specific plugins should set this with their own platform-specific
  /// class that extends [WebtritCallkeepPlatform] when they register themselves.
  static set instance(WebtritCallkeepPlatform instance) {
    PlatformInterface.verify(instance, _token);
    _instance = instance;
  }

  /// Return the current platform name.
  Future<String?> getPlatformName();
}
