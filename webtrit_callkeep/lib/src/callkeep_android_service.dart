import 'dart:async';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO:
// - rename to CallkeepBackgroundService
// - convert to static abstract

/// The [CallkeepAndroidService] class is used to set the backgroud service delegate
/// and invoke methods on the native side for the background tasks.
/// The android service delegate is used to receive events from the native side.
class CallkeepAndroidService {
  /// The singleton constructor of [CallkeepAndroidService].
  factory CallkeepAndroidService() => _instance;

  CallkeepAndroidService._();
  static final _instance = CallkeepAndroidService._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Sets the android service delegate.
  /// [CallkeepAndroidServiceDelegate] needs to be implemented to receive events.
  void setAndroidServiceDelegate(CallkeepAndroidServiceDelegate? delegate) {
    platform.setAndroidDelegate(delegate);
  }

  /// Hangs up an ongoing call and cancels the active notification if any
  /// with the given [callId].
  ///
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  Future<dynamic> hungUp(String callId) {
    return platform.endCallAndroidService(callId);
  }

  /// Initiates an incoming call notification
  /// with the given [callId], [handle], [displayName] and [hasVideo] flag.
  ///
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  Future<dynamic> incomingCall(String callId, CallkeepHandle handle, String? displayName, bool hasVideo) {
    return platform.incomingCallAndroidService(callId, handle, displayName, hasVideo);
  }
}
