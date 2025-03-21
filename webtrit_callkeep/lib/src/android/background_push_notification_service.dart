import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// Manages background push notification call events on Android.
class BackgroundPushNotificationService {
  /// Returns the singleton instance.
  factory BackgroundPushNotificationService() => _instance;

  BackgroundPushNotificationService._();

  static final _instance = BackgroundPushNotificationService._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Sets the delegate for handling background push notification events (Android only).
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {
    if (kIsWeb || !Platform.isAndroid) return;
    platform.setBackgroundServiceDelegate(delegate);
  }

  /// Ends a background call by [callId] (Android only).
  Future<dynamic> endBackgroundCall(String callId) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.endCallBackgroundPushNotificationService(callId);
  }

  /// Ends all background calls (Android only).
  Future<dynamic> endAllBackgroundCalls() {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.endCallsBackgroundPushNotificationService();
  }
}
