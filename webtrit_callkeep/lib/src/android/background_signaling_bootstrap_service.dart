import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// Configures and manages the background signaling service on Android.
class BackgroundSignalingBootstrapService {
  /// Returns the singleton instance.
  factory BackgroundSignalingBootstrapService() => _instance;

  BackgroundSignalingBootstrapService._();

  static final _instance = BackgroundSignalingBootstrapService._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Constant used to identify the type of incoming call.
  static String incomingCallType = 'call-incoming-type';

  /// Initializes the background service callback handlers (Android only).
  Future<void> initializeCallback({
    required ForegroundStartServiceHandle onStart,
    required ForegroundChangeLifecycleHandle onChangedLifecycle,
  }) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.initializeBackgroundSignalingServiceCallback(
      onStart: onStart,
      onChangedLifecycle: onChangedLifecycle,
    );
  }

  /// Sets up notification config for the background service (Android only).
  Future<void> setUp({
    String androidNotificationName = 'WebTrit Inbound Calls',
    String androidNotificationDescription = 'This is required to receive incoming calls',
  }) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.configureBackgroundSignalingService(
      androidNotificationName: androidNotificationName,
      androidNotificationDescription: androidNotificationDescription,
    );
  }

  /// Starts the background signaling service (Android only).
  Future<void> startService({
    Map<String, dynamic> data = const {},
  }) async {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.startBackgroundSignalingService(data: data);
  }

  /// Stops the background signaling service (Android only).
  Future<void> stopService() async {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.stopBackgroundSignalingService();
  }
}
