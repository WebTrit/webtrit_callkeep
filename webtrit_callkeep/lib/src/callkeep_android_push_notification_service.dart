import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO:
// - convert to static abstract

/// The [CallkeepBackgroundService] class is used to set the backgroud service delegate
/// and invoke methods on the native side for the background tasks.
/// The android service delegate is used to receive events from the native side.
class CallkeepAndroidPushNotificationService {
  /// The singleton constructor of [CallkeepBackgroundService].
  factory CallkeepAndroidPushNotificationService() => _instance;

  CallkeepAndroidPushNotificationService._();

  static final _instance = CallkeepAndroidPushNotificationService._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  static String incomingCallType = 'call-incoming-type';

  /// Initializes the push notification callback.
  ///
  /// This method sets up a callback function that gets triggered when there is a change
  /// in the push notification sync status.
  ///
  /// [onNotificationSync] - A callback function that handles the push notification sync status change.
  ///
  /// Throws an [UnimplementedError] if this method is not yet implemented.
  static Future<void> initializePushNotificationCallback(CallKeepPushNotificationSyncStatusHandle onNotificationSync) {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }

    return platform.initializePushNotificationCallback(onNotificationSync);
  }

  /// Report a new incoming call with the given [callId], [handle], [displayName] and [hasVideo] flag.
  /// Returns [CallkeepIncomingCallError] if there is an error.
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle, {
    String? displayName,
    bool hasVideo = false,
  }) {
    return platform.incomingCallPushNotificationService(callId, handle, displayName, hasVideo);
  }
}
