import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

class MockWebtritCallkeepPlatformInterfacePlatform extends Mock
    with MockPlatformInterfaceMixin
    implements WebtritCallkeepPlatform {
  CallkeepDelegate? _callkeepDelegate;
  CallkeepBackgroundServiceDelegate? _androidServiceDelegate;

  @override
  Future<CallkeepCallRequestError?> answerCall(String callId) {
    _callkeepDelegate?.performAnswerCall(callId);
    return Future.value();
  }

  @override
  Future<CallkeepCallRequestError?> endCall(String callId) {
    _callkeepDelegate?.performEndCall(callId);
    return Future.value();
  }

  @override
  Future<dynamic> endCallBackgroundSignalingService(String callId) {
    _androidServiceDelegate?.performEndCall(callId);
    return Future.value();
  }

  @override
  Future<dynamic> endCallsBackgroundSignalingService() {
    return Future.value();
  }

  @override
  Future<dynamic> incomingCallBackgroundSignalingService(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    return Future.value();
  }

  // TODO: remove, nothing to override

  // @override
  // Future<bool> isLockScreenAndroidService() {
  //   return Future.value(false);
  // }

  @override
  Future<bool> isSetUp() {
    return Future.value(true);
  }

  @override
  Future<String?> pushTokenForPushTypeVoIP() {
    return Future.value('token');
  }

  @override
  Future<void> reportConnectedOutgoingCall(String callId) {
    return Future.value();
  }

  @override
  Future<void> reportConnectingOutgoingCall(String callId) {
    return Future.value();
  }

  // @override
  // Future<void> reportEndCall(String callId, CallkeepEndCallReason reason) {
  //   _callkeepDelegate?.performEndCall(callId);
  //   return Future.value();
  // }

  @override
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    return Future.value();
  }

  @override
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  ) {
    return Future.value();
  }

  @override
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) {
    _callkeepDelegate?.performSendDTMF(callId, key);
    return Future.value();
  }

  @override
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {
    _androidServiceDelegate = delegate;
  }

  @override
  void setDelegate(CallkeepDelegate? delegate) {
    _callkeepDelegate = delegate;
  }

  @override
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) {
    _callkeepDelegate?.performSetHeld(callId, onHold);
    return Future.value();
  }

  @override
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) {
    _callkeepDelegate?.performSetMuted(callId, muted);
    return Future.value();
  }

  @override
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {}

  @override
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) {
    _callkeepDelegate?.performSetSpeaker(callId, enabled);
    return Future.value();
  }

  @override
  Future<void> setUp(CallkeepOptions options) {
    return Future.value();
  }

  // TODO: remove, nothing to override

  // @override
  // void shutDownAppAndroidService({
  //   String? path,
  //   onlyWhenLock = false,
  // }) {}

  @override
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  ) {
    _callkeepDelegate?.performStartCall(callId, handle, displayNameOrContactIdentifier, video);
    return Future.value();
  }

  @override
  Future<void> tearDown() {
    return Future.value();
  }

  // TODO: remove, nothing to override

  // @override
  // Future<void> wakeUpAppAndroidService({
  //   String? path,
  // }) {
  //   return Future.value();
  // }

  @override
  void setLogsDelegate(CallkeepLogsDelegate? delegate) {}

  // TODO: test if it'l not removed
  @override
  Future<String?> getPlatformName() {
    return Future.value('mock');
  }
}
