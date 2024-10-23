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

  /// Sets up the Android background service with optional handlers and configurations.
  ///
  /// [onStart] - A callback triggered when the service starts in the foreground. It provides
  /// the current service status and additional data..
  ///
  /// [onChangedLifecycle] - A callback triggered when there is a change in the lifecycle
  /// of the foreground service (e.g., when the service is paused, resumed, or stopped). .
  ///
  /// [autoRestartOnTerminate] - If true, the service will automatically restart if it is
  /// unexpectedly terminated by the system or the user. Default is false.
  ///
  /// [autoStartOnBoot] - If true, the service will automatically start after the device reboots.
  /// Default is false.
  ///
  /// [androidNotificationName] - Specifies the name of the notification channel for Android
  /// when the service runs in the background.
  ///
  /// [androidNotificationDescription] - Specifies the description of the notification channel
  /// for Android..
  ///
  /// Throws an [UnimplementedError] if this method is not yet implemented.
  Future<void> setUpAndroidBackgroundService({
    ForegroundStartServiceHandle? onStart,
    ForegroundChangeLifecycleHandle? onChangedLifecycle,
    bool autoRestartOnTerminate = false,
    bool autoStartOnBoot = false,
    String? androidNotificationName,
    String? androidNotificationDescription,
  }) {
    throw UnimplementedError('setUpAndroidBackgroundService() is not implemented');
  }

  /// Starts the background service with the provided [data].
  ///
  /// [data] - A map containing any additional parameters or configurations required by
  /// the service at the time of starting. Defaults to an empty map.
  ///
  /// Throws an [UnimplementedError] if this method is not yet implemented.
  void startService({
    Map<String, dynamic> data = const {},
  }) {
    throw UnimplementedError('startService() is not implemented');
  }

  /// Stops the background service.
  ///
  /// This method will stop the currently running background service. Once stopped,
  /// the service will no longer be running until explicitly started again.
  ///
  /// Throws an [UnimplementedError] if this method is not yet implemented.
  void stopService() {
    throw UnimplementedError('stopService() is not implemented');
  }

  /// Finishes the current activity.
  ///
  /// This method is used to terminate or finish the current activity associated
  /// with the service. Typically used when the activity is no longer needed or should
  /// be cleaned up.
  ///
  /// Throws an [UnimplementedError] if this method is not yet implemented.
  void finishActivity() {
    throw UnimplementedError('finishActivity() is not implemented');
  }
}
