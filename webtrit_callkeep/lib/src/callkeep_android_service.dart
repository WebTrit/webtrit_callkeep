import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO:
// - convert to static abstract

/// The [CallkeepBackgroundService] class is used to set the backgroud service delegate
/// and invoke methods on the native side for the background tasks.
/// The android service delegate is used to receive events from the native side.
class CallkeepBackgroundService {
  /// The singleton constructor of [CallkeepBackgroundService].
  factory CallkeepBackgroundService() => _instance;

  CallkeepBackgroundService._();

  static final _instance = CallkeepBackgroundService._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  static String incomingCallType = 'call-incoming-type';

  /// Configures the background service with optional lifecycle and startup handlers.
  ///
  /// [onStart] - Callback triggered when the service starts in the foreground. It provides
  /// the current service status and additional data. Optional.
  ///
  /// [onChangedLifecycle] - Callback triggered when the lifecycle of the foreground service
  /// changes, such as when it is paused, resumed, or stopped. Optional.
  ///
  /// This method configures and sets up the Android background service using the provided
  /// parameters and handlers.
  static Future<void> initializeSignalingServiceCallback({
    required ForegroundStartServiceHandle onStart,
    required ForegroundChangeLifecycleHandle onChangedLifecycle,
  }) {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }

    return platform.initializeBackgroundSignalingServiceCallback(
      onStart: onStart,
      onChangedLifecycle: onChangedLifecycle,
    );
  }

  /// Configures the background service with optional lifecycle and startup handlers.
  /// [androidNotificationName] - The name of the Android notification channel used when
  /// running the service in the background. Defaults to 'WebTrit Inbound Calls'.
  ///
  /// [androidNotificationDescription] - The description of the Android notification channel
  /// used when running the service. Defaults to 'This is required to receive incoming calls'.
  ///
  /// This method configures and sets up the Android background service using the provided
  /// parameters and handlers.
  Future<void> setUp({
    String androidNotificationName = 'WebTrit Inbound Calls',
    String androidNotificationDescription = 'This is required to receive incoming calls',
  }) {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }

    return platform.configureBackgroundSignalingService(
      androidNotificationName: androidNotificationName,
      androidNotificationDescription: androidNotificationDescription,
    );
  }

  /// Starts the background service with the given [data].
  ///
  /// [data] - A map containing any additional parameters or configurations required
  /// by the service at the time of starting. Defaults to an empty map.
  ///
  /// This method invokes the platform-specific implementation to start the service
  /// with the provided data.
  Future<void> startService({
    Map<String, dynamic> data = const {},
  }) async {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }
    return platform.startBackgroundSignalingService(data: data);
  }

  /// Stops the running background service.
  ///
  /// This method triggers the platform-specific implementation to stop the background
  /// service that is currently running.
  Future<void> stopService() async {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }
    platform.stopBackgroundSignalingService();
  }

  /// Sets the android service delegate.
  /// [CallkeepBackgroundServiceDelegate] needs to be implemented to receive events.
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {
    if (kIsWeb) {
      return;
    }

    if (!Platform.isAndroid) {
      return;
    }

    platform.setBackgroundServiceDelegate(delegate);
  }

  /// Ends up an ongoing call and cancels the active notification if any
  /// with the given [callId].
  ///
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  Future<dynamic> endBackgroundCall(String callId) {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }

    return platform.endCallBackgroundSignalingService(callId);
  }

  /// Ends all ongoing calls and cancels all active notifications.
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  /// This method is used to end all calls and cancel all active notifications.
  Future<dynamic> endAllBackgroundCalls() {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }

    return platform.endCallsBackgroundSignalingService();
  }

  /// Initiates an incoming call notification
  /// with the given [callId], [handle], [displayName] and [hasVideo] flag.
  ///
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  Future<dynamic> incomingCall(
    String callId,
    CallkeepHandle handle, {
    String? displayName,
    bool hasVideo = false,
  }) {
    if (kIsWeb) {
      return Future.value();
    }

    if (!Platform.isAndroid) {
      return Future.value();
    }

    return platform.incomingCallBackgroundSignalingService(callId, handle, displayName, hasVideo);
  }
}

@Deprecated('Use CallkeepBackgroundService instead')
class CallkeepAndroidService {}
// TODO remove CallkeepAndroidService
