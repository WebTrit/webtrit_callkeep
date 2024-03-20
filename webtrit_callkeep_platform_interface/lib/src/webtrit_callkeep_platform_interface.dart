import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'delegate/delegate.dart';
import 'models/models.dart';

class _PlaceholderImplementation extends WebtritCallkeepPlatform {}

abstract class WebtritCallkeepPlatform extends PlatformInterface {
  WebtritCallkeepPlatform() : super(token: _token);

  static final Object _token = Object();

  static WebtritCallkeepPlatform _instance = _PlaceholderImplementation();

  static WebtritCallkeepPlatform get instance => _instance;

  static set instance(WebtritCallkeepPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformName() {
    throw UnimplementedError('getPlatformName() has not been implemented.');
  }

  void setDelegate(
    CallkeepDelegate? delegate,
  ) {
    throw UnimplementedError('setDelegate() has not been implemented.');
  }

  void setAndroidDelegate(
    CallkeepAndroidServiceDelegate? delegate,
  ) {
    throw UnimplementedError('setAndroidServiceDelegate() has not been implemented.');
  }

  void setLogsDelegate(
    CallkeepLogsDelegate? delegate,
  ) {
    throw UnimplementedError('setLogsDelegate() has not been implemented.');
  }

  void setPushRegistryDelegate(
    PushRegistryDelegate? delegate,
  ) {
    throw UnimplementedError('setPushRegistryDelegate() has not been implemented.');
  }

  Future<String?> pushTokenForPushTypeVoIP() {
    throw UnimplementedError('pushTokenForPushTypeVoIP() has not been implemented.');
  }

  Future<bool> isSetUp() {
    throw UnimplementedError('isSetUp() has not been implemented.');
  }

  Future<void> setUp(
    CallkeepOptions options,
  ) {
    throw UnimplementedError('setUp() has not been implemented.');
  }

  Future<void> tearDown() {
    throw UnimplementedError('tearDown() has not been implemented.');
  }

  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    throw UnimplementedError('reportNewIncomingCall() has not been implemented.');
  }

  Future<void> reportConnectingOutgoingCall(
    String callId,
  ) {
    throw UnimplementedError('reportConnectingOutgoingCall() has not been implemented.');
  }

  Future<void> reportConnectedOutgoingCall(
    String callId,
  ) {
    throw UnimplementedError('reportConnectedOutgoingCall() has not been implemented.');
  }

  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
  ) {
    throw UnimplementedError('reportUpdateCall() has not been implemented.');
  }

  Future<void> reportEndCall(
    String callId,
    CallkeepEndCallReason reason,
  ) {
    throw UnimplementedError('reportEndCall() has not been implemented.');
  }

  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) {
    throw UnimplementedError('startCall() has not been implemented.');
  }

  Future<CallkeepCallRequestError?> answerCall(
    String callId,
  ) {
    throw UnimplementedError('answerCall() has not been implemented.');
  }

  Future<CallkeepCallRequestError?> endCall(
    String callId,
  ) {
    throw UnimplementedError('endCall() has not been implemented.');
  }

  Future<CallkeepCallRequestError?> setHeld(
    String callId,
    bool onHold,
  ) {
    throw UnimplementedError('setHeld() has not been implemented.');
  }

  Future<CallkeepCallRequestError?> setMuted(
    String callId,
    bool muted,
  ) {
    throw UnimplementedError('setMuted() has not been implemented.');
  }

  Future<CallkeepCallRequestError?> sendDTMF(
    String callId,
    String key,
  ) {
    throw UnimplementedError('sendDTMF() has not been implemented.');
  }

  Future<CallkeepCallRequestError?> setSpeaker(
    String callId,
    bool enabled,
  ) {
    throw UnimplementedError('setSpeaker() has not been implemented.');
  }

  // Android
  Future endCallAndroidService(
    String callId,
  ) {
    throw UnimplementedError('hungUpAndroidService() has not been implemented.');
  }

  Future incomingCallAndroidService(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    throw UnimplementedError('incomingCallAndroidService() has not been implemented.');
  }
}
