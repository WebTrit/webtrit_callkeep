import 'dart:async';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO:
// - rename to CallkeepBackgroundService
// - convert to static abstract
// - single platform getter

class CallkeepAndroidService {
  static final _instance = CallkeepAndroidService._();

  factory CallkeepAndroidService() {
    return _instance;
  }

  CallkeepAndroidService._();

  void setAndroidServiceDelegate(CallkeepAndroidServiceDelegate? delegate) {
    WebtritCallkeepPlatform.instance.setAndroidDelegate(
      delegate,
    );
  }

  Future hungUp(
    String callId,
  ) {
    return WebtritCallkeepPlatform.instance.endCallAndroidService(
      callId,
    );
  }

  Future incomingCall(
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
