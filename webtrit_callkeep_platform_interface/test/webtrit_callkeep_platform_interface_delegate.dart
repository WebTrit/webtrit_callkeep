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

  final Function()? didActivateAudioSessionListener;

  final Function()? didDeActivateAudioSessionListener;

  final Function()? didResetListener;

  final Function(String callId)? performAnswerCallListener;

  final Function(String callId)? performEndCallListener;

  final Function(
    String callId,
    String key,
  )? performSendDTMFListener;

  final Function(
    String callId,
    bool onHold,
  )? performHeldListener;

  final Function(
    String callId,
    bool muted,
  )? performMuteListener;

  final Function(
    String callId,
    bool enabled,
  )? performSpeakerListener;

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
  ) {s
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
