import 'dart:async';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO
// - convert to static abstract

/// Represents the status stages of the Callkeep setup process.
enum CallkeepStatus {
  /// The initial state when Callkeep is not yet set up.
  uninitialized,

  /// The state indicating Callkeep is in the process of configuring.
  configuring,

  /// The active state when Callkeep is fully set up and ready.
  active,

  /// The state when Callkeep is in the process of shutting down.
  terminating,
}

/// The [Callkeep] main class for managing platform specific callkeep operations.
/// e.g reporting incoming calls, handling outgoing calls, setting up platform VOIP integration etc.
/// The delegate is used to receive events from the native side.
class Callkeep {
  /// The singleton constructor of [Callkeep].
  factory Callkeep() => _instance;

  Callkeep._();

  static final _instance = Callkeep._();

  final StreamController<CallkeepStatus> _statusController = StreamController<CallkeepStatus>.broadcast();
  CallkeepStatus _currentStatus = CallkeepStatus.uninitialized;

  /// Getter for the current status
  CallkeepStatus get currentStatus => _currentStatus;

  /// Stream to subscribe to status updates
  Stream<CallkeepStatus> get statusStream => _statusController.stream;

  /// Method to update the status, ensuring status updates go through the stream
  void _updateStatus(CallkeepStatus newStatus) {
    if (_currentStatus != newStatus) {
      _currentStatus = newStatus;
      _statusController.add(newStatus);
    }
  }

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Sets the delegate for receiving calkeep events from the native side.
  /// [CallkeepDelegate] needs to be implemented to receive callkeep events.
  void setDelegate(CallkeepDelegate? delegate) {
    platform.setDelegate(delegate);
  }

  /// Sets the delegate for receiving push registry events from the native side.
  /// [PushRegistryDelegate] needs to be implemented to receive push registry events.
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {
    return platform.setPushRegistryDelegate(delegate);
  }

  /// Push token for push type VOIP.
  // TODO: unused, need clarification
  Future<String?> pushTokenForPushTypeVoIP() {
    return platform.pushTokenForPushTypeVoIP();
  }

  /// Check if CallKeep has been set up.
  /// Returns [Future] that completes with a [bool] value.
  Future<bool> isSetUp() {
    return platform.isSetUp();
  }

  /// Perform setup with the given [options].
  /// Returns [Future] that completes when the setup is done.
  Future<void> setUp(CallkeepOptions options) {
    _updateStatus(CallkeepStatus.configuring);
    return platform.setUp(options).then((_) => _updateStatus(CallkeepStatus.active));
  }

  /// Report the teardown state
  Future<void> tearDown() {
    _updateStatus(CallkeepStatus.terminating);
    return platform.tearDown().then((_) => _updateStatus(CallkeepStatus.uninitialized));
  }

  /// Report a new incoming call with the given [callId], [handle], [displayName] and [hasVideo] flag.
  /// Returns [CallkeepIncomingCallError] if there is an error.
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle, {
    String? displayName,
    bool hasVideo = false,
  }) {
    return platform.reportNewIncomingCall(callId, handle, displayName, hasVideo);
  }

  /// Report that an outgoing call with given [callId] is connecting.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportConnectingOutgoingCall(String callId) {
    return platform.reportConnectingOutgoingCall(callId);
  }

  /// Report that an outgoing call with given [callId] has been connected.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportConnectedOutgoingCall(String callId) {
    return platform.reportConnectedOutgoingCall(callId);
  }

  /// Report an update to the call metadata.
  /// The [displayName] and [hasVideo] flag can be updated.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportUpdateCall(
    String callId, {
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  }) {
    return platform.reportUpdateCall(callId, handle, displayName, hasVideo, proximityEnabled);
  }

  /// Report the end of call with the given [callId].
  /// The [displayName] of the call is required for reporting miseed call metadata.
  /// The [reason] for ending the call is required.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportEndCall(String callId, String displayName, CallkeepEndCallReason reason) {
    return platform.reportEndCall(callId, displayName, reason);
  }

  /// Start a call with the given [callId], [handle], [displayNameOrContactIdentifier] and [hasVideo] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle, {
    String? displayNameOrContactIdentifier,
    bool hasVideo = false,
    bool proximityEnabled = false,
  }) {
    return platform.startCall(callId, handle, displayNameOrContactIdentifier, hasVideo, proximityEnabled);
  }

  /// Answer a call with the given [callId].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> answerCall(String callId) {
    return platform.answerCall(callId);
  }

  /// End a call with the given [callId].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> endCall(String callId) {
    return platform.endCall(callId);
  }

  /// Set the call on hold with the given [callId] and [onHold] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setHeld(String callId, {required bool onHold}) {
    return platform.setHeld(callId, onHold);
  }

  /// Set the call on mute with the given [callId] and [muted] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setMuted(String callId, {required bool muted}) {
    return platform.setMuted(callId, muted);
  }

  /// Send DTMF with the given [callId] and [key].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) {
    return platform.sendDTMF(callId, key);
  }

  /// Set the speaker with the given [callId] and [enabled] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setSpeaker(String callId, {required bool enabled}) {
    return platform.setSpeaker(callId, enabled);
  }
}
