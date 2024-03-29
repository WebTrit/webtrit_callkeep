import 'dart:async';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO:
// - rename to CallkeepBackgroundService
// - convert to static abstract
// - single platform getter

/// The [CallkeepAndroidService] class is used to set the backgroud service delegate
/// and invoke methods on the native side for the background tasks.
/// The android service delegate is used to receive events from the native side.
class CallkeepAndroidService {
  /// The singleton constructor of [CallkeepAndroidService].
  factory CallkeepAndroidService() => _instance;

  CallkeepAndroidService._();
  static final _instance = CallkeepAndroidService._();

  /// Sets the android service delegate.
  /// [CallkeepAndroidServiceDelegate] needs to be implemented to receive events.
  void setAndroidServiceDelegate(CallkeepAndroidServiceDelegate? delegate) {
    WebtritCallkeepPlatform.instance.setAndroidDelegate(delegate);
  }

  /// TODO : unused, needs clarification
  Future<void> hungUp(String callId) {
    return WebtritCallkeepPlatform.instance.endCallAndroidService(callId);
  }

  /// Report an incoming call event to the native side.
  Future<void> incomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    return WebtritCallkeepPlatform.instance.incomingCallAndroidService(
      callId,
      handle,
      displayName,
      hasVideo,
    );
  }
}
