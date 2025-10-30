import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// Configures the background push notification service on Android.
class BackgroundPushNotificationBootstrapService {
  /// Returns the singleton instance.
  factory BackgroundPushNotificationBootstrapService() => _instance;

  BackgroundPushNotificationBootstrapService._();

  static final _instance = BackgroundPushNotificationBootstrapService._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Initializes the callback used for syncing push notification status (Android only).
  Future<void> initializeCallback(CallKeepPushNotificationSyncStatusHandle onNotificationSync) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.initializePushNotificationCallback(onNotificationSync);
  }

  /// Configures the push notification signaling service (Android only).
  Future<void> configurePushNotificationSignalingService({
    bool launchBackgroundIsolateEvenIfAppIsOpen = false,
  }) async {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.configurePushNotificationSignalingService(
      launchBackgroundIsolateEvenIfAppIsOpen: launchBackgroundIsolateEvenIfAppIsOpen,
    );
  }

  /// Reports a new incoming call triggered by a push notification.
  ///
  /// Returns a [CallkeepIncomingCallError] if reporting fails.
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle, {
    String? displayName,
    bool hasVideo = false,
  }) {
    return platform.incomingCallPushNotificationService(callId, handle, displayName, hasVideo);
  }
}
