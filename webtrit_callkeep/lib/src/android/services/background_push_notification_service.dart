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
  Future<dynamic> endCall(String callId) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.endCallBackgroundPushNotificationService(callId);
  }

  /// Ends all background calls (Android only).
  ///
  /// Deprecated: use [releaseCall] with a specific callId instead.
  /// [endCalls] routes through a teardown path that does not stop
  /// [IncomingCallService], leaving the incoming call notification visible.
  @Deprecated('Use releaseCall(callId) instead')
  Future<dynamic> endCalls() {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.endCallsBackgroundPushNotificationService();
  }

  /// Terminates the PhoneConnection and stops IncomingCallService for [callId] (Android only).
  ///
  /// Use for unanswered calls: missed, declined by server, signaling error, or hangup
  /// received before the user answered. Sends a decline signal to the ConnectionService
  /// which destroys the PhoneConnection before stopping the service.
  Future<dynamic> releaseCall(String callId) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.releaseCallBackgroundPushNotificationService(callId);
  }

  /// Stops IncomingCallService for [callId] without terminating the PhoneConnection (Android only).
  ///
  /// Use when the call was already answered via the push notification path and the Activity
  /// is taking over. The PhoneConnection stays alive so the Activity can adopt it via
  /// the CALL_ID_ALREADY_EXISTS_AND_ANSWERED path in reportNewIncomingCall.
  Future<dynamic> handoffCall(String callId) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.handoffCallBackgroundPushNotificationService(callId);
  }
}
