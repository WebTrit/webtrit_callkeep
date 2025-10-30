import 'dart:io' show Platform;

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The [ActivityControl] class is used to manage
/// Android Activity properties and state.
///
/// This is an Android-only feature set.
class ActivityControl {
  /// The singleton constructor of [ActivityControl].
  factory ActivityControl() => _instance;

  ActivityControl._();

  static final _instance = ActivityControl._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Allows the app's activity to be shown over the device lock screen.
  ///
  /// This is an Android-only feature. Does nothing on other platforms.
  Future<void> showOverLockscreen([bool enable = true]) {
    if (!Platform.isAndroid) {
      return Future.value();
    }
    return platform.showOverLockscreen(enable);
  }

  /// Turns the screen on when the app's window is shown.
  ///
  /// Typically used in conjunction with [showOverLockscreen].
  /// This is an Android-only feature. Does nothing on other platforms.
  Future<void> wakeScreenOnShow([bool enable = true]) {
    if (!Platform.isAndroid) {
      return Future.value();
    }
    return platform.wakeScreenOnShow(enable);
  }

  /// Moves the entire task (app) to the background.
  ///
  /// This is an Android-only feature.
  /// Returns `Future.value(false)` on non-Android platforms.
  Future<bool> sendToBackground() {
    if (!Platform.isAndroid) {
      return Future.value(false);
    }
    return platform.sendToBackground();
  }

  /// Checks if the device screen is currently locked (keyguard is active).
  ///
  /// Returns `Future.value(false)` on non-Android platforms.
  Future<bool> isDeviceLocked() {
    if (!Platform.isAndroid) {
      return Future.value(false);
    }
    return platform.isDeviceLocked();
  }
}
