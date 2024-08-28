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

  /// Sets the delegate for receiving calkeep events from the native side.
  /// [CallkeepDelegate] needs to be implemented to receive callkeep events.
  void setDelegate(CallkeepDelegate? delegate) {
    throw UnimplementedError('setDelegate() has not been implemented.');
  }

  /// Sets the background service delegate.
  /// [CallkeepBackgroundServiceDelegate] needs to be implemented to receive events.
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {
    throw UnimplementedError('setAndroidServiceDelegate() has not been implemented.');
  }

  /// Sets the logs delegate.
  /// [CallkeepLogsDelegate] needs to be implemented to receive logs.
  void setLogsDelegate(CallkeepLogsDelegate? delegate) {
    throw UnimplementedError('setLogsDelegate() has not been implemented.');
  }

  /// Sets the delegate for receiving push registry events from the native side.
  /// [PushRegistryDelegate] needs to be implemented to receive push registry events.
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {
    throw UnimplementedError('setPushRegistryDelegate() has not been implemented.');
  }

  /// Push token for push type VOIP.
  // TODO: unused, need clarification
  Future<String?> pushTokenForPushTypeVoIP() {
    throw UnimplementedError('pushTokenForPushTypeVoIP() has not been implemented.');
  }

  /// Check if CallKeep has been set up.
  /// Returns [Future] that completes with a [bool] value.
  Future<bool> isSetUp() {
    throw UnimplementedError('isSetUp() has not been implemented.');
  }

  /// Perform setup with the given [options].
  /// Returns [Future] that completes when the setup is done.
  Future<void> setUp(CallkeepOptions options) {
    throw UnimplementedError('setUp() has not been implemented.');
  }

  /// Report the teardown state
  Future<void> tearDown() {
    throw UnimplementedError('tearDown() has not been implemented.');
  }

  /// Report a new incoming call with the given [callId], [handle], [displayName] and [hasVideo] flag.
  /// Returns [CallkeepIncomingCallError] if there is an error.
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    throw UnimplementedError('reportNewIncomingCall() has not been implemented.');
  }

  /// Report that an outgoing call with given [callId] is connecting.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportConnectingOutgoingCall(String callId) {
    throw UnimplementedError('reportConnectingOutgoingCall() has not been implemented.');
  }

  /// Report that an outgoing call with given [callId] has been connected.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportConnectedOutgoingCall(String callId) {
    throw UnimplementedError('reportConnectedOutgoingCall() has not been implemented.');
  }

  /// Report an update to the call metadata.
  /// The [displayName] and [hasVideo] flag can be updated.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  ) {
    throw UnimplementedError('reportUpdateCall() has not been implemented.');
  }

  /// Report the end of call with the given [callId].
  /// The [reason] for ending the call is required.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportEndCall(String callId, CallkeepEndCallReason reason) {
    throw UnimplementedError('reportEndCall() has not been implemented.');
  }

  /// Start a call with the given [callId], [handle], [displayNameOrContactIdentifier] and [video] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  ) {
    throw UnimplementedError('startCall() has not been implemented.');
  }

  /// Answer a call with the given [callId].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> answerCall(String callId) {
    throw UnimplementedError('answerCall() has not been implemented.');
  }

  /// End a call with the given [callId].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> endCall(String callId) {
    throw UnimplementedError('endCall() has not been implemented.');
  }

  /// Set the call on hold with the given [callId] and [onHold] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) {
    throw UnimplementedError('setHeld() has not been implemented.');
  }

  /// Set the call on mute with the given [callId] and [muted] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) {
    throw UnimplementedError('setMuted() has not been implemented.');
  }

  /// Send DTMF with the given [callId] and [key].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) {
    throw UnimplementedError('sendDTMF() has not been implemented.');
  }

  /// Set the speaker with the given [callId] and [enabled] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) {
    throw UnimplementedError('setSpeaker() has not been implemented.');
  }

  /// Ends up an ongoing call and cancels the active notification if any
  /// with the given [callId].
  ///
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  // TODO: specify the return type
  Future<dynamic> endBackgroundCall(String callId) {
    throw UnimplementedError('hungUpAndroidService() has not been implemented.');
  }

  /// Ends all ongoing calls and cancels all active notifications.
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  /// This method is used to end all calls and cancel all active notifications.
  Future<dynamic> endAllBackgroundCalls() {
    throw UnimplementedError('endAllCalls() has not been implemented.');
  }

  /// Hangs up an ongoing call and cancels the active notification if any
  /// with the given [callId].
  ///
  /// Returns a [Future] that resolves after completition with unsafe result and may cause error in production.
  // TODO: specify the return type
  Future<dynamic> incomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    throw UnimplementedError('incomingCallAndroidService() has not been implemented.');
  }

// Permissions section

  // Check if the permission for full screen intent is available.
  // https://source.android.com/docs/core/permissions/fsi-limits
  Future<CallkeepSpecialPermissionStatus> getFullScreenIntentPermissionStatus() {
    throw UnimplementedError('getFullScreenIntentPermissionStatus() has not been implemented.');
  }

  // Open the settings screen for full screen intent permission.
  Future<bool> openFullScreenIntentSettings() {
    throw UnimplementedError('launchFullScreenIntentSettings() has not been implemented.');
  }
}
