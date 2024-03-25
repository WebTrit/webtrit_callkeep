import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'package:webtrit_callkeep_platform_interface/src/delegate/delegate.dart';
import 'package:webtrit_callkeep_platform_interface/src/models/models.dart';

class _PlaceholderImplementation extends WebtritCallkeepPlatform {}

/// The interface that implementations of webtrit_callkeep must implement.
abstract class WebtritCallkeepPlatform extends PlatformInterface {
  /// Constructs a WebtritCallkeepPlatform.
  WebtritCallkeepPlatform() : super(token: _token);

  static final Object _token = Object();

  static WebtritCallkeepPlatform _instance = _PlaceholderImplementation();

  /// Imlemented instance of [WebtritCallkeepPlatform] to use.
  static WebtritCallkeepPlatform get instance => _instance;

  static set instance(WebtritCallkeepPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Gets the platform name.
  Future<String?> getPlatformName() {
    throw UnimplementedError('getPlatformName() has not been implemented.');
  }

  /// Manually sets the delegate.
  void setDelegate(CallkeepDelegate? delegate) {
    throw UnimplementedError('setDelegate() has not been implemented.');
  }

  /// Sets the Android delegate.
  void setAndroidDelegate(CallkeepAndroidServiceDelegate? delegate) {
    throw UnimplementedError('setAndroidServiceDelegate() has not been implemented.');
  }

  /// Sets the logs delegate.
  void setLogsDelegate(CallkeepLogsDelegate? delegate) {
    throw UnimplementedError('setLogsDelegate() has not been implemented.');
  }

  /// Sets the push registry delegate.
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {
    throw UnimplementedError('setPushRegistryDelegate() has not been implemented.');
  }

  /// Requests the push token for VoIP.
  Future<String?> pushTokenForPushTypeVoIP() {
    throw UnimplementedError('pushTokenForPushTypeVoIP() has not been implemented.');
  }

  /// Checks if the platform options is set up.
  Future<bool> isSetUp() {
    throw UnimplementedError('isSetUp() has not been implemented.');
  }

  /// Sets platform-specific options.
  Future<void> setUp(CallkeepOptions options) {
    throw UnimplementedError('setUp() has not been implemented.');
  }

  /// Tears down the platform.
  Future<void> tearDown() {
    throw UnimplementedError('tearDown() has not been implemented.');
  }

  /// Report the incoming call event.
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    throw UnimplementedError('reportNewIncomingCall() has not been implemented.');
  }

  /// Report the incoming call connected event.
  Future<void> reportConnectingOutgoingCall(String callId) {
    throw UnimplementedError('reportConnectingOutgoingCall() has not been implemented.');
  }

  /// Report the incoming call connected event.
  Future<void> reportConnectedOutgoingCall(String callId) {
    throw UnimplementedError('reportConnectedOutgoingCall() has not been implemented.');
  }

  /// Report the incoming call connected event.
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
  ) {
    throw UnimplementedError('reportUpdateCall() has not been implemented.');
  }

  /// Report the incoming call connected event.
  Future<void> reportEndCall(String callId, CallkeepEndCallReason reason) {
    throw UnimplementedError('reportEndCall() has not been implemented.');
  }

  /// Start a call.
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) {
    throw UnimplementedError('startCall() has not been implemented.');
  }

  /// Answer a call.
  Future<CallkeepCallRequestError?> answerCall(String callId) {
    throw UnimplementedError('answerCall() has not been implemented.');
  }

  /// Reject a call.
  Future<CallkeepCallRequestError?> endCall(String callId) {
    throw UnimplementedError('endCall() has not been implemented.');
  }

  /// Set the hold status of a call.
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) {
    throw UnimplementedError('setHeld() has not been implemented.');
  }

  /// Set the muted status of a call.
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) {
    throw UnimplementedError('setMuted() has not been implemented.');
  }

  /// Send DTMF tones.
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) {
    throw UnimplementedError('sendDTMF() has not been implemented.');
  }

  /// Set the speaker status of a call.
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) {
    throw UnimplementedError('setSpeaker() has not been implemented.');
  }

  // Android
  Future<void> endCallAndroidService(String callId) {
    throw UnimplementedError('hungUpAndroidService() has not been implemented.');
  }

  /// Report the incoming call event.
  Future<dynamic> incomingCallAndroidService(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    throw UnimplementedError('incomingCallAndroidService() has not been implemented.');
  }
}
