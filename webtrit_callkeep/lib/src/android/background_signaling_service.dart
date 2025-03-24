import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// Manages background signaling events for Android.
class BackgroundSignalingService {
  /// Returns the singleton instance.
  factory BackgroundSignalingService() => _instance;

  BackgroundSignalingService._();

  static final _instance = BackgroundSignalingService._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Sets the delegate for background signaling callbacks (Android only).
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {
    if (kIsWeb || !Platform.isAndroid) return;
    platform.setBackgroundServiceDelegate(delegate);
  }

  /// Reports an incoming call in the background (Android only).
  Future<dynamic> incomingCall(
    String callId,
    CallkeepHandle handle, {
    String? displayName,
    bool hasVideo = false,
  }) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.incomingCallBackgroundSignalingService(callId, handle, displayName, hasVideo);
  }

  /// Ends a background call by [callId] (Android only).
  Future<dynamic> endBackgroundCall(String callId) {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.endCallBackgroundSignalingService(callId);
  }

  /// Ends all background calls (Android only).
  Future<dynamic> endAllBackgroundCalls() {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.endCallsBackgroundSignalingService();
  }
}
