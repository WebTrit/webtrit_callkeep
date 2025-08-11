import 'package:bloc/bloc.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:meta/meta.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import '../../../app/constants.dart';

part 'actions_state.dart';

part 'actions_cubit.freezed.dart';

class ActionsCubit extends Cubit<ActionsState> implements CallkeepDelegate, CallkeepBackgroundServiceDelegate {
  ActionsCubit(
    this._callkeep,
  ) : super(const ActionsState(actions: [])) {
    _callkeep.setDelegate(this);
  }

  final Callkeep _callkeep;

  bool _speakerEnabled = false;
  bool _isMuted = false;
  bool _isHold = false;

  @override
  Future<void> close() {
    _callkeep.setDelegate(null);
    return super.close();
  }

  void setup() async {
    try {
      await _callkeep.setUp(const CallkeepOptions(
        ios: CallkeepIOSOptions(
          localizedName: "en",
          maximumCallGroups: 2,
          maximumCallsPerCallGroup: 1,
          supportedHandleTypes: {CallkeepHandleType.number},
        ),
        android: CallkeepAndroidOptions(),
      ));
      emit(state.addAction("Setup success"));
    } catch (error) {
      emit(state.addAction("Setup error: $error"));
    }
  }

  void isSetup() async {
    try {
      final result = await _callkeep.isSetUp();
      emit(state.addAction("Is setup: $result"));
    } catch (error) {
      emit(state.addAction("Is setup error: $error"));
    }
  }

  void incomingCallAndroid() async {
    try {
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
        call1Identifier,
        call1Number,
        displayName: 'User Name',
      );
      emit(state.addAction("[Android]: Incoming  cal"));
    } catch (error) {
      emit(state.addAction("[Android]: Is setup error: $error"));
    }
  }

  void tearDown() async {
    try {
      await _callkeep.tearDown();
      emit(state.addAction("Tear down success"));
    } catch (error) {
      emit(state.addAction("Error tear down: $error"));
    }
  }

  void reportNewIncomingCall() async {
    try {
      final result = await _callkeep.reportNewIncomingCall(
        call1Identifier,
        call1Number,
        displayName: 'User Name',
        hasVideo: true,
      );
      if (result != null) {
        emit(state.addAction("Error report new incoming call error: ${result.name}"));
      } else {
        emit(state.addAction("Success  report new incoming call"));
      }
    } catch (error) {
      emit(state.addAction("Error report new incoming call error: $error"));
    }
  }

  void reportNewIncomingCallV2() async {
    try {
      final result = await _callkeep.reportNewIncomingCall(
        call2Identifier,
        call2Number,
        displayName: 'User Name 1',
        hasVideo: true,
      );
      if (result != null) {
        emit(state.addAction("Error report new incoming call error: ${result.name}"));
      } else {
        emit(state.addAction("Success  report new incoming call"));
      }
    } catch (error) {
      emit(state.addAction("Error report new incoming call error: $error"));
    }
  }

  void startOutgoingCall() async {
    try {
      final result = await _callkeep.startCall(
        call1Identifier,
        call1Number,
        displayNameOrContactIdentifier: 'User Name',
        hasVideo: true,
      );
      if (result != null) {
        emit(state.addAction("Error start outgoing call error: ${result.name}"));
      } else {
        emit(state.addAction("Success start outgoing call"));
      }
    } catch (error) {
      emit(state.addAction("Error start outgoing call error: $error"));
    }
  }

  void reportConnectedOutgoingCall() async {
    try {
      await _callkeep.reportConnectedOutgoingCall(call1Identifier);
      emit(state.addAction("Success report connected outgoing call"));
    } catch (error) {
      emit(state.addAction("Error report connected outgoing call error: $error"));
    }
  }

  void reportConnectingOutgoingCall() async {
    try {
      await _callkeep.reportConnectingOutgoingCall(call1Identifier);
      emit(state.addAction("Success report connecting outgoing call"));
    } catch (error) {
      emit(state.addAction("Error report connecting outgoing call error: $error"));
    }
  }

  void reportUpdateCall() async {
    try {
      await _callkeep.reportUpdateCall(
        call1Identifier,
        handle: call1Number,
        displayName: 'User Name',
        hasVideo: true,
      );
      emit(state.addAction("Success report update call"));
    } catch (error) {
      emit(state.addAction("Error report update  error: $error"));
    }
  }

  void reportEndCall() async {
    try {
      await _callkeep.reportEndCall(
        call1Identifier,
        "Display Name",
        CallkeepEndCallReason.declinedElsewhere,
      );
      emit(state.addAction("Success report end call"));
    } catch (error) {
      emit(state.addAction("Error eeport  end  error: $error"));
    }
  }

  void answerCall() async {
    try {
      await _callkeep.answerCall(call1Identifier);
      emit(state.addAction("Success report answer call"));
    } catch (error) {
      emit(state.addAction("Error answer  error: $error"));
    }
  }

  void endCall() async {
    try {
      await _callkeep.endCall(call1Identifier);
      emit(state.addAction("Success end call"));
    } catch (error) {
      emit(state.addAction("Error end  error: $error"));
    }
  }

  void setHeld() async {
    try {
      await _callkeep.setHeld(call1Identifier, onHold: !_isHold);
      emit(state.addAction("Held action sent"));
    } catch (error) {
      emit(state.addAction("Error set held  error: $error"));
    }
  }

  void setMuted() async {
    try {
      final muted = !_isMuted;
      await _callkeep.setMuted(call1Identifier, muted: muted);
      emit(state.addAction("Mute action sent (muted: $muted)"));
    } catch (error) {
      emit(state.addAction("Error set muted  error: $error"));
    }
  }

  void setSpeaker() async {
    try {
      await _callkeep.setSpeaker(call1Identifier, enabled: !_speakerEnabled);
      emit(state.addAction("Speaker action sent"));
    } catch (error) {
      emit(state.addAction("Error  set speaker  error: $error"));
    }
  }

  void setDTMF() async {
    try {
      await _callkeep.sendDTMF(call1Identifier, "A");
      emit(state.addAction("DTMF action sent"));
    } catch (error) {
      emit(state.addAction("Error set DTMF  error: $error"));
    }
  }

  @override
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video) {
    emit(state.addAction("Perform continue start call intent"));
  }

  @override
  void didActivateAudioSession() {
    emit(state.addAction("Perform did activate audio session"));
  }

  @override
  void didDeactivateAudioSession() {
    emit(state.addAction("Perform did deactivate audio session"));
  }

  @override
  void didPushIncomingCall(
      CallkeepHandle handle, String? displayName, bool video, String callId, CallkeepIncomingCallError? error) {
    emit(state.addAction("Perform did push incoming call"));
  }

  @override
  void didReset() {
    emit(state.addAction("Perform did reset"));
  }

  @override
  Future<bool> performAnswerCall(String callId) {
    emit(state.addAction("Delegate answer call"));
    return Future.value(true);
  }

  @override
  Future<bool> performEndCall(String callId) {
    emit(state.addAction("Delegate end call"));
    return Future.value(true);
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) {
    emit(state.addAction("Delegate dtmf pressed: $key"));
    return Future.value(true);
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) {
    _isHold = onHold;
    emit(state.addAction("Delegate held: $onHold"));
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    _isMuted = muted;
    emit(state.addAction("Delegate muted: $muted"));
    return Future.value(true);
  }

  @override
  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) {
    emit(state.addAction("Perform start call"));
    return Future.value(true);
  }

  @override
  Future<bool> performSetSpeaker(String callId, bool enabled) {
    _speakerEnabled = enabled;
    emit(state.addAction("Delegate set speaker: $enabled"));
    return Future.value(true);
  }

  @override
  void performReceivedCall(
    String callId,
    String number,
    DateTime createdTime,
    String? displayName,
    DateTime? acceptedTime,
    DateTime? hungUpTime, {
    bool video = false,
  }) {
    emit(state.addAction("End call received"));
  }
}
