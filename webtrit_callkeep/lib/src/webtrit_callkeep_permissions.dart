import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// The [WebtritCallkeepPermissions] class is used to set the logs delegate.
// The logs delegate is used to receive logs from the native side.
class WebtritCallkeepPermissions {
  // The singleton constructor of [WebtritCallkeepPermissions].
  factory WebtritCallkeepPermissions() => _instance;

  WebtritCallkeepPermissions._();

  static final _instance = WebtritCallkeepPermissions._();

  // The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  // Checks if the full screen intent permission is available.
  // Returns a [Future] that resolves to a boolean indicating the availability.
  Future<CallkeepSpecialPermissionStatus> getFullScreenIntentPermissionStatus() {
    return platform.getFullScreenIntentPermissionStatus();
  }

  // Launches the settings screen for full screen intent permission.
  void launchFullScreenIntentSettings() {
    platform.launchFullScreenIntentSettings();
  }
}

extension CallkeepSpecialPermissionsExtension on CallkeepSpecialPermissions {
  // Gets the status of the special permission.
  //
  // If the permission is [CallkeepSpecialPermissions.fullScreenIntent], it checks the full screen intent permission status.
  // Returns a [Future] that resolves to a [CallkeepSpecialPermissionStatus] indicating the status of the permission.
  Future<CallkeepSpecialPermissionStatus> status() async {
    if (this == CallkeepSpecialPermissions.fullScreenIntent) {
      final _callkeepPermissions = WebtritCallkeepPermissions();
      return _callkeepPermissions.getFullScreenIntentPermissionStatus();
    }
    return CallkeepSpecialPermissionStatus.granted;
  }
}
