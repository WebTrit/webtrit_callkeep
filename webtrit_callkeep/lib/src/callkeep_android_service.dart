import 'dart:async';

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

  /// Sets the android service delegate.
  /// [CallkeepBackgroundServiceDelegate] needs to be implemented to receive events.
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {
    platform.setBackgroundServiceDelegate(delegate);
  }

  /// Ends up an ongoing call and cancels the active notification if any
  /// with the given [callId].
  ///
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  Future<dynamic> endBackgroundCall(String callId) {
    return platform.endBackgroundCall(callId);
  }

  /// Ends all ongoing calls and cancels all active notifications.
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  /// This method is used to end all calls and cancel all active notifications.
  Future<dynamic> endAllBackgroundCalls() {
    return platform.endAllBackgroundCalls();
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
    return platform.incomingCall(callId, handle, displayName, hasVideo);
  }
}

@Deprecated('Use CallkeepBackgroundService instead')
class CallkeepAndroidService {}
// TODO remove CallkeepAndroidService