import 'dart:async';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO
// - convert to static abstract
// - single platform getter

/// The [Callkeep] main class for managing platform specific callkeep operations.
/// e.g reporting incoming calls, handling outgoing calls, setting up platform VOIP integration etc.
/// The delegate is used to receive events from the native side.
class Callkeep {
  /// The singleton constructor of [Callkeep].
  factory Callkeep() => _instance;
  Callkeep._();
  static final _instance = Callkeep._();

  /// Sets the delegate for receiving calkeep events from the native side.
  /// [CallkeepDelegate] needs to be implemented to receive callkeep events.
  void setDelegate(CallkeepDelegate? delegate) {
    WebtritCallkeepPlatform.instance.setDelegate(delegate);
  }

  /// Sets the delegate for receiving push registry events from the native side.
  /// [PushRegistryDelegate] needs to be implemented to receive push registry events.
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {
    return WebtritCallkeepPlatform.instance.setPushRegistryDelegate(delegate);
  }

  /// Push token for push type VOIP.
  // TODO: unused, need clarification
  Future<String?> pushTokenForPushTypeVoIP() {
    return WebtritCallkeepPlatform.instance.pushTokenForPushTypeVoIP();
  }

  /// Check if CallKeep has been set up.
  /// Returns [Future] that completes with a [bool] value.
  Future<bool> isSetUp() {
    return WebtritCallkeepPlatform.instance.isSetUp();
  }

  /// Perform setup with the given [options].
  /// Returns [Future] that completes when the setup is done.
  Future<void> setUp(CallkeepOptions options) {
    return WebtritCallkeepPlatform.instance.setUp(options);
  }

  /// Report the teardown state
  Future<void> tearDown() {
    return WebtritCallkeepPlatform.instance.tearDown();
  }

  /// Report a new incoming call with the given [callId], [handle], [displayName] and [hasVideo] flag.
  /// Returns [CallkeepIncomingCallError] if there is an error.
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
      String callId, CallkeepHandle handle, String? displayName, bool hasVideo) {
    return WebtritCallkeepPlatform.instance.reportNewIncomingCall(callId, handle, displayName, hasVideo);
  }

  /// Report that an outgoing call with given [callId] is connecting.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportConnectingOutgoingCall(String callId) {
    return WebtritCallkeepPlatform.instance.reportConnectingOutgoingCall(callId);
  }

  /// Report that an outgoing call with given [callId] has been connected.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportConnectedOutgoingCall(String callId) {
    return WebtritCallkeepPlatform.instance.reportConnectedOutgoingCall(callId);
  }

  /// Report an update to the call metadata.
  /// The [displayName] and [hasVideo] flag can be updated.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportUpdateCall(String callId, CallkeepHandle? handle, String? displayName, bool? hasVideo) {
    return WebtritCallkeepPlatform.instance.reportUpdateCall(callId, handle, displayName, hasVideo);
  }

  /// Report the end of call with the given [callId].
  /// The [reason] for ending the call is required.
  /// Returns [Future] that completes when the operation is done.
  Future<void> reportEndCall(String callId, CallkeepEndCallReason reason) {
    return WebtritCallkeepPlatform.instance.reportEndCall(callId, reason);
  }

  /// Start a call with the given [callId], [handle], [displayNameOrContactIdentifier] and [video] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> startCall(
      String callId, CallkeepHandle handle, String? displayNameOrContactIdentifier, bool video) {
    return WebtritCallkeepPlatform.instance.startCall(callId, handle, displayNameOrContactIdentifier, video);
  }

  /// Answer a call with the given [callId].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> answerCall(String callId) {
    return WebtritCallkeepPlatform.instance.answerCall(callId);
  }

  /// End a call with the given [callId].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> endCall(String callId) {
    return WebtritCallkeepPlatform.instance.endCall(callId);
  }

  /// Set the call on hold with the given [callId] and [onHold] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) {
    return WebtritCallkeepPlatform.instance.setHeld(callId, onHold);
  }

  /// Set the call on mute with the given [callId] and [muted] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) {
    return WebtritCallkeepPlatform.instance.setMuted(callId, muted);
  }

  /// Send DTMF with the given [callId] and [key].
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) {
    return WebtritCallkeepPlatform.instance.sendDTMF(callId, key);
  }

  /// Set the speaker with the given [callId] and [enabled] flag.
  /// Returns [CallkeepCallRequestError] if there is an error.
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) {
    return WebtritCallkeepPlatform.instance.setSpeaker(callId, enabled);
  }
}
