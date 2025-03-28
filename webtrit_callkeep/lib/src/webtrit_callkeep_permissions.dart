import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The [WebtritCallkeepPermissions] class is used to set the permissions delegate.
/// The logs delegate is used to receive logs from the native side.
class WebtritCallkeepPermissions {
  /// The singleton constructor of [WebtritCallkeepPermissions].
  factory WebtritCallkeepPermissions() => _instance;

  WebtritCallkeepPermissions._();

  static final _instance = WebtritCallkeepPermissions._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Checks if the full screen intent permission is available.
  /// Returns a [Future] that resolves to a boolean indicating the availability.
  Future<CallkeepSpecialPermissionStatus> getFullScreenIntentPermissionStatus() {
    if (kIsWeb) {
      return Future.value(CallkeepSpecialPermissionStatus.granted);
    }

    if (!Platform.isAndroid) {
      return Future.value(CallkeepSpecialPermissionStatus.granted);
    }

    return platform.getFullScreenIntentPermissionStatus();
  }

  /// Opens the settings screen for full screen intent permission.
  ///
  /// Returns a [Future] that resolves to a boolean indicating whether the settings screen was successfully opened.
  Future<bool> openFullScreenIntentSettings() {
    if (kIsWeb) {
      return Future.value(false);
    }

    if (!Platform.isAndroid) {
      return Future.value(false);
    }

    return platform.openFullScreenIntentSettings();
  }

  /// Gets the battery optimization status.
  Future<CallkeepAndroidBatteryMode> getBatteryMode() {
    if (kIsWeb) {
      return Future.value(CallkeepAndroidBatteryMode.unknown);
    }

    if (!Platform.isAndroid) {
      return Future.value(CallkeepAndroidBatteryMode.unknown);
    }

    return platform.getBatteryMode();
  }
}

/// Extension on [CallkeepSpecialPermissions] to get the status of special permissions.
extension CallkeepSpecialPermissionsExtension on CallkeepSpecialPermissions {
  /// Gets the status of the special permission.
  ///
  /// If the permission is [CallkeepSpecialPermissions.fullScreenIntent], it checks the full screen intent permission status.
  /// Returns a [Future] that resolves to a [CallkeepSpecialPermissionStatus] indicating the status of the permission.
  Future<CallkeepSpecialPermissionStatus> status() async {
    if (this == CallkeepSpecialPermissions.fullScreenIntent) {
      final callkeepPermissions = WebtritCallkeepPermissions();
      return callkeepPermissions.getFullScreenIntentPermissionStatus();
    }
    return CallkeepSpecialPermissionStatus.granted;
  }
}
