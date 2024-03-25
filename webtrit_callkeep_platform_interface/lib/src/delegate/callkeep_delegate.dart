import 'package:webtrit_callkeep_platform_interface/src/models/models.dart';

abstract class CallkeepDelegate {
  void continueStartCallIntent(
    CallkeepHandle handle,
    String? displayName,
    bool video,
  );

  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  );

  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  );

  Future<bool> performAnswerCall(
    String callId,
  );

  Future<bool> performEndCall(
    String callId,
  );

  Future<bool> performSetHeld(
    String callId,
    bool onHold,
  );

  Future<bool> performSetMuted(
    String callId,
    bool muted,
  );

  Future<bool> performSendDTMF(
    String callId,
    String key,
  );

  Future<bool> performSetSpeaker(
    String callId,
    bool enabled,
  );

  void didActivateAudioSession();

  void didDeactivateAudioSession();

  void didReset();
}
