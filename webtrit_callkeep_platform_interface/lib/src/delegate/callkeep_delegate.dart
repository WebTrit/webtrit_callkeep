import 'package:webtrit_callkeep_platform_interface/src/models/models.dart';

/// Common callkeep delegate
/// Used to handle callkeep from the platform side
abstract class CallkeepDelegate {
  /// Confirmation for outgoing call
  void continueStartCallIntent(
    CallkeepHandle handle,
    String? displayName,
    bool video,
  );

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

  /// Perform reject call
  Future<bool> performSetSpeaker(String callId, bool enabled);

  /// Audio session activated
  void didActivateAudioSession();

  /// Audio session deactivated
  void didDeactivateAudioSession();

  /// reset
  void didReset();
}
