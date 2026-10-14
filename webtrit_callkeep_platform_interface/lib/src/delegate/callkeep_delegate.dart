import 'package:webtrit_callkeep_platform_interface/src/models/models.dart';

/// Common callkeep delegate
/// Used to handle callkeep from the platform side
abstract class CallkeepDelegate {
  /// Confirmation for outgoing call.
  ///
  /// iOS only. The system delivers `INStartAudioCallIntent`, `INStartVideoCallIntent`
  /// or `INStartCallIntent` to CallKit applications through `continueUserActivity`.
  /// Android has no equivalent: self-managed calls are not written to the system
  /// call log by default, and the platform entry points (`ACTION_CALL`, App Actions)
  /// arrive as activity intents, so a redial reaches the application as an ordinary
  /// outgoing call instead of this callback. Never invoked on Android.
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video);

  /// Confirmation for incoming call processing
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  );

  /// Perform start call
  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  );

  /// Perform answer call
  Future<bool> performAnswerCall(String callId);

  /// Perform end call
  Future<bool> performEndCall(String callId);

  /// Perform reject call
  Future<bool> performSetHeld(String callId, bool onHold);

  /// Perform reject call
  Future<bool> performSetMuted(String callId, bool muted);

  /// Perform reject call
  Future<bool> performSendDTMF(String callId, String key);

  /// Perform audio device changed
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device);

  /// Perform audio devices update
  Future<bool> performAudioDevicesUpdate(String callId, List<CallkeepAudioDevice> devices);

  /// Audio session activated
  void didActivateAudioSession();

  /// Audio session deactivated
  void didDeactivateAudioSession();

  /// Reset.
  ///
  /// iOS only. Reported by CallKit when the provider resets and every call it
  /// tracked is gone. Android's Telecom framework has no provider-level reset,
  /// so this callback is never invoked there.
  void didReset();
}
