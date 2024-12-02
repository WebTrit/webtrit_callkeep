import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The [WebtritCallkeepSound] class is used to set the sound playback delegate.
class WebtritCallkeepSound {
  /// The singleton constructor of [WebtritCallkeepSound].
  factory WebtritCallkeepSound() => _instance;

  WebtritCallkeepSound._();

  static final _instance = WebtritCallkeepSound._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Play ringback sound
  /// Use this method to play `calling` sound when the outgoing call is connecting.
  /// e.g on 'SIP 180 Ringing' sdps.
  ///
  /// Returns [Future] that resolves on sound was successfully played.
  Future<void> playRingbackSound() {
    return platform.playRingbackSound();
  }

  /// Stop ringback sound
  /// Use this method to stop `calling` sound when the outgoing call is connected.
  ///
  /// Returns [Future] that resolves on sound was successfully stopped.
  Future<void> stopRingbackSound() {
    return platform.stopRingbackSound();
  }
}
