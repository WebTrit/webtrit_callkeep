import 'package:webtrit_callkeep_platform_interface/src/delegate/delegate.dart';
import 'package:webtrit_callkeep_platform_interface/src/models/models.dart';

class WebtritCallkeepDelegateRelayMock implements CallkeepDelegate {
  WebtritCallkeepDelegateRelayMock({
    this.performStartCallListener,
    this.continueStartCallIntentListener,
    this.didActivateAudioSessionListener,
    this.didDeActivateAudioSessionListener,
    this.didResetListener,
    this.didPushIncomingCallListener,
    this.performSendDTMFListener,
    this.performEndCallListener,
    this.performAnswerCallListener,
    this.performHeldListener,
    this.performMuteListener,
    this.performSpeakerListener,
  });

  void Function(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  )? performStartCallListener;

  void Function(
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  )? continueStartCallIntentListener;

  void Function(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  )? didPushIncomingCallListener;

  void Function()? didActivateAudioSessionListener;

  void Function()? didDeActivateAudioSessionListener;

  void Function()? didResetListener;

  void Function(String callId)? performAnswerCallListener;

  void Function(String callId)? performEndCallListener;

  void Function(String callId, String key)? performSendDTMFListener;

  void Function(String callId, bool onHold)? performHeldListener;

  void Function(String callId, bool muted)? performMuteListener;

  void Function(String callId, bool enabled)? performSpeakerListener;

  @override
  void continueStartCallIntent(
    CallkeepHandle handle,
    String? displayName,
    bool video,
  ) {
    continueStartCallIntentListener?.call(
      handle,
      displayName,
      video,
    );
  }

  @override
  void didActivateAudioSession() {
    didActivateAudioSessionListener?.call();
  }

  @override
  void didDeactivateAudioSession() {
    didDeActivateAudioSessionListener?.call();
  }

  @override
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  ) {
    didPushIncomingCallListener?.call(
      handle,
      displayName,
      video,
      callId,
      error,
    );
  }

  @override
  void didReset() {
    didResetListener?.call();
  }

  @override
  Future<bool> performAnswerCall(
    String callId,
  ) {
    performAnswerCallListener?.call(callId);
    return Future.value(true);
  }

  @override
  Future<bool> performEndCall(
    String callId,
  ) {
    performEndCallListener?.call(callId);
    return Future.value(true);
  }

  @override
  Future<bool> performSendDTMF(
    String callId,
    String key,
  ) {
    performSendDTMFListener?.call(callId, key);
    return Future.value(true);
  }

  @override
  Future<bool> performSetHeld(
    String callId,
    bool onHold,
  ) {
    performHeldListener?.call(callId, onHold);
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(
    String callId,
    bool muted,
  ) {
    performMuteListener?.call(callId, muted);
    return Future.value(true);
  }

  @override
  Future<bool> performSetSpeaker(
    String callId,
    bool enabled,
  ) {
    performSpeakerListener?.call(callId, enabled);
    return Future.value(true);
  }

  @override
  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) {
    performStartCallListener?.call(callId, handle, displayNameOrContactIdentifier, video);
    return Future.value(true);
  }
}
