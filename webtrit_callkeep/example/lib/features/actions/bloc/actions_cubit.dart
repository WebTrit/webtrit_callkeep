import 'package:bloc/bloc.dart';
import 'package:meta/meta.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import '../../../app/constants.dart';

part 'actions_state.dart';

class ActionsCubit extends Cubit<ActionsState> implements CallkeepDelegate, CallkeepBackgroundServiceDelegate {
  ActionsCubit(
    this._callkeep,
  ) : super(const ActionsUpdate([])) {
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
      emit(state.update.addAction(action: "Setup success"));
    } catch (error) {
      emit(state.update.addAction(action: "Setup error: $error"));
    }
  }

  void isSetup() async {
    try {
      var result = await _callkeep.isSetUp();
      emit(state.update.addAction(action: "Is setup: $result"));
    } catch (error) {
      emit(state.update.addAction(action: "Is setup error: $error"));
    }
  }

  void incomingCallAndroid() async {
    try {
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
        call1Identifier,
        call1Number,
        displayName: 'User Name',
      );

      emit(state.update.addAction(action: "[Android]: Incoming  cal"));
    } catch (error) {
      emit(state.update.addAction(action: "[Android]: Is setup error: $error"));
    }
  }

  void tearDown() async {
    try {
      await _callkeep.tearDown();
      emit(state.update.addAction(action: "Tear down success"));
    } catch (error) {
      emit(state.update.addAction(action: "Error tear down: $error"));
    }
  }

  void reportNewIncomingCall() async {
    try {
      var result = await _callkeep.reportNewIncomingCall(
        call1Identifier,
        call1Number,
        displayName: 'User Name',
        hasVideo: true,
      );
      if (result != null) {
        emit(state.update.addAction(action: "Error report new incoming call error: ${result.name}"));
      } else {
        emit(state.update.addAction(action: "Success  report new incoming call"));
      }
    } catch (error) {
      emit(state.update.addAction(action: "Error report new incoming call error: $error"));
    }
  }

  void reportNewIncomingCallV2() async {
    try {
      var result = await _callkeep.reportNewIncomingCall(
        call2Identifier,
        call2Number,
        displayName: 'User Name 1',
        hasVideo: true,
      );
      if (result != null) {
        emit(state.update.addAction(action: "Error report new incoming call error: ${result.name}"));
      } else {
        emit(state.update.addAction(action: "Success  report new incoming call"));
      }
    } catch (error) {
      emit(state.update.addAction(action: "Error report new incoming call error: $error"));
    }
  }

  void startOutgoingCall() async {
    try {
      var result = await _callkeep.startCall(
        call1Identifier,
        call1Number,
        displayNameOrContactIdentifier: 'User Name',
        hasVideo: true,
      );
      if (result != null) {
        emit(state.update.addAction(action: "Error start outgoing call error: ${result.name}"));
      } else {
        emit(state.update.addAction(action: "Success start outgoing call"));
      }
    } catch (error) {
      emit(state.update.addAction(action: "Error start outgoing call error: $error"));
    }
  }

  void reportConnectedOutgoingCall() async {
    try {
      await _callkeep.reportConnectedOutgoingCall(call1Identifier);
      emit(state.update.addAction(action: "Success report connected outgoing call"));
    } catch (error) {
      emit(state.update.addAction(action: "Error report connected outgoing call error: $error"));
    }
  }

  void reportConnectingOutgoingCall() async {
    try {
      await _callkeep.reportConnectingOutgoingCall(call1Identifier);
      emit(state.update.addAction(action: "Success report connecting outgoing call"));
    } catch (error) {
      emit(state.update.addAction(action: "Error report connecting outgoing call error: $error"));
    }
  }

  void reportUpdateCall() async {
    try {
      await _callkeep.reportUpdateCall(call1Identifier, handle: call1Number, displayName: 'User Name', hasVideo: true);

      emit(state.update.addAction(action: "Success report update call"));
    } catch (error) {
      emit(state.update.addAction(action: "Error report update  error: $error"));
    }
  }

  void reportEndCall() async {
    try {
      await _callkeep.reportEndCall(call1Identifier, "Display Name", CallkeepEndCallReason.declinedElsewhere);
      emit(state.update.addAction(action: "Success report end call"));
    } catch (error) {
      emit(state.update.addAction(action: "Error eeport  end  error: $error"));
    }
  }

  void answerCall() async {
    try {
      await _callkeep.answerCall(call1Identifier);
      emit(state.update.addAction(action: "Success report answer call"));
    } catch (error) {
      emit(state.update.addAction(action: "Error answer  error: $error"));
    }
  }

  void endCall() async {
    try {
      await _callkeep.endCall(call1Identifier);
      emit(state.update.addAction(action: "Success end call"));
    } catch (error) {
      emit(state.update.addAction(action: "Error end  error: $error"));
    }
  }

  void setHeld() async {
    try {
      await _callkeep.setHeld(call1Identifier, onHold: !_isHold);
      emit(state.update.addAction(action: "Held action sent"));
    } catch (error) {
      emit(state.update.addAction(action: "Error set held  error: $error"));
    }
  }

  void setMuted() async {
    try {
      await _callkeep.setMuted(call1Identifier, muted: !_isMuted);
      emit(state.update.addAction(action: "Mute action sent"));
    } catch (error) {
      emit(state.update.addAction(action: "Error set muted  error: $error"));
    }
  }

  void setSpeaker() async {
    try {
      await _callkeep.setSpeaker(call1Identifier, enabled: !_speakerEnabled);
      emit(state.update.addAction(action: "Speaker action sent"));
    } catch (error) {
      emit(state.update.addAction(action: "Error  set speaker  error: $error"));
    }
  }

  void setDTMF() async {
    try {
      await _callkeep.sendDTMF(call1Identifier, "A");
      emit(state.update.addAction(action: "DTMF action sent"));
    } catch (error) {
      emit(state.update.addAction(action: "Error set DTMF  error: $error"));
    }
  }

  @override
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video) {
    emit(state.update.addAction(action: "Perform continue start call intent"));
  }

  @override
  void didActivateAudioSession() {
    emit(state.update.addAction(action: "Perform did activate audio session"));
  }

  @override
  void didDeactivateAudioSession() {
    emit(state.update.addAction(action: "Perform did deactivate audio session"));
  }

  @override
  void didPushIncomingCall(
      CallkeepHandle handle, String? displayName, bool video, String callId, CallkeepIncomingCallError? error) {
    emit(state.update.addAction(action: "Perform did push incoming call"));
  }

  @override
  void didReset() {
    emit(state.update.addAction(action: "Perform did reset"));
  }

  @override
  Future<bool> performAnswerCall(String callId) {
    emit(state.update.addAction(action: "Delegate answer call"));
    return Future.value(true);
  }

  @override
  Future<bool> performEndCall(String callId) {
    emit(state.update.addAction(action: "Delegate end call"));
    return Future.value(true);
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) {
    emit(state.update.addAction(action: "Delegate dtmf pressed: $key"));
    return Future.value(true);
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) {
    _isHold = onHold;
    emit(state.update.addAction(action: "Delegate held: $onHold"));
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    _isMuted = muted;
    emit(state.update.addAction(action: "Delegate muted: $muted"));
    return Future.value(true);
  }

  @override
  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) {
    emit(state.update.addAction(action: "Perform start call"));
    return Future.value(true);
  }

  @override
  Future<bool> performSetSpeaker(String callId, bool enabled) {
    _speakerEnabled = enabled;
    emit(state.update.addAction(action: "Delegate set speaker: $enabled"));

    return Future.value(true);
  }

  @override
  void performServiceEndCall(String callId) {
    emit(state.update.addAction(action: "Delegate service end call$callId"));
  }

  @override
  void endCallReceived(String callId, String number, DateTime createdTime, DateTime? acceptedTime, DateTime? hungUpTime,
      {bool video = false}) {
    emit(state.update.addAction(action: "End call received"));
  }

  @override
  void performServiceAnswerCall(String callId) {
    // TODO: implement performServiceAnswerCall
  }
}
