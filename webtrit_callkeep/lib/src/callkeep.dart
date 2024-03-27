import 'dart:async';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO
// - convert to static abstract
// - single platform getter

class Callkeep {
  static final _instance = Callkeep._();

  factory Callkeep() {
    return _instance;
  }

  Callkeep._();

  void setDelegate(CallkeepDelegate? delegate) {
    WebtritCallkeepPlatform.instance.setDelegate(delegate);
  }

  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {
    return WebtritCallkeepPlatform.instance.setPushRegistryDelegate(delegate);
  }

  Future<String?> pushTokenForPushTypeVoIP() {
    return WebtritCallkeepPlatform.instance.pushTokenForPushTypeVoIP();
  }

  Future<bool> isSetUp() {
    return WebtritCallkeepPlatform.instance.isSetUp();
  }

  Future<void> setUp(CallkeepOptions options) {
    return WebtritCallkeepPlatform.instance.setUp(options);
  }

  Future<void> tearDown() {
    return WebtritCallkeepPlatform.instance.tearDown();
  }

  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
      String callId, CallkeepHandle handle, String? displayName, bool hasVideo) {
    return WebtritCallkeepPlatform.instance.reportNewIncomingCall(callId, handle, displayName, hasVideo);
  }

  Future<void> reportConnectingOutgoingCall(String callId) {
    return WebtritCallkeepPlatform.instance.reportConnectingOutgoingCall(callId);
  }

  Future<void> reportConnectedOutgoingCall(String callId) {
    return WebtritCallkeepPlatform.instance.reportConnectedOutgoingCall(callId);
  }

  Future<void> reportUpdateCall(String callId, CallkeepHandle? handle, String? displayName, bool? hasVideo) {
    return WebtritCallkeepPlatform.instance.reportUpdateCall(callId, handle, displayName, hasVideo);
  }

  Future<void> reportEndCall(String callId, CallkeepEndCallReason reason) {
    return WebtritCallkeepPlatform.instance.reportEndCall(callId, reason);
  }

  Future<CallkeepCallRequestError?> startCall(
      String callId, CallkeepHandle handle, String? displayNameOrContactIdentifier, bool video) {
    return WebtritCallkeepPlatform.instance.startCall(callId, handle, displayNameOrContactIdentifier, video);
  }

  Future<CallkeepCallRequestError?> answerCall(String callId) {
    return WebtritCallkeepPlatform.instance.answerCall(callId);
  }

  Future<CallkeepCallRequestError?> endCall(String callId) {
    return WebtritCallkeepPlatform.instance.endCall(callId);
  }

  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) {
    return WebtritCallkeepPlatform.instance.setHeld(callId, onHold);
  }

  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) {
    return WebtritCallkeepPlatform.instance.setMuted(callId, muted);
  }

  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) {
    return WebtritCallkeepPlatform.instance.sendDTMF(callId, key);
  }

  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) {
    return WebtritCallkeepPlatform.instance.setSpeaker(callId, enabled);
  }
}
