import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

import 'package:bloc/bloc.dart';
import 'package:meta/meta.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/constants.dart';

part 'actions_state.dart';

class ActionsCubit extends Cubit<ActionsState> implements CallkeepDelegate, CallkeepAndroidServiceDelegate {
  ActionsCubit(
    this._callkeep,
    this._callkeepAndroidService,
  ) : super(const ActionsUpdate([])) {
    _callkeep.setDelegate(this);

    if (!kIsWeb) {
      if (Platform.isAndroid) {
        _callkeepAndroidService.setAndroidServiceDelegate(this);
      }
    }
  }

  final Callkeep _callkeep;
  final CallkeepAndroidService _callkeepAndroidService;

  final call1Identifier = '0';
  final call2Identifier = '1';

  final numberMock = const CallkeepHandle.number('380000000000');
  final numberMock1 = const CallkeepHandle.number('380000000001');

  bool _speakerEnabled = false;
  bool _isMuted = false;
  bool _isHold = false;

  @override
  Future<void> close() {
    _callkeep.setDelegate(null);
    if (!kIsWeb) {
      if (Platform.isAndroid) {
        _callkeepAndroidService.setAndroidServiceDelegate(null);
      }
    }
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
        android: CallkeepAndroidOptions(
          incomingPath: initialCallRout,
          rootPath: initialMainRout,
        ),
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

  // TODO: remove, action deprecated

  // void shutDownAppAndroid() async {
  //   try {
  //     _callkeepAndroidService.shutDownApp(path: "/", onlyWhenLock: false);
  //     emit(state.update.addAction(action: "[Android]: Shut down app android: success"));
  //   } catch (error) {
  //     emit(state.update.addAction(action: "[Android]: Is setup error: $error"));
  //   }
  // }

  // TODO: remove, action deprecated

  // void isLookScreenAndroid() async {
  //   try {
  //     var result = await _callkeepAndroidService.isLockScreen();
  //     emit(state.update.addAction(action: "[Android]: Is look screen: $result"));
  //   } catch (error) {
  //     emit(state.update.addAction(action: "[Android]: Is setup error: $error"));
  //   }
  // }

  void incomingCallAndroid() async {
    try {
      var result = await _callkeepAndroidService.incomingCall(call1Identifier, numberMock, 'User Name', true);
      emit(state.update.addAction(action: "[Android]: Incoming  call: $result"));
    } catch (error) {
      emit(state.update.addAction(action: "[Android]: Is setup error: $error"));
    }
  }

  void hungUpAndroid() async {
    try {
      var result = await _callkeepAndroidService.hungUp(call1Identifier);
      emit(state.update.addAction(action: "[Android]:Hung up: $result"));
    } catch (error) {
      emit(state.update.addAction(action: "[Android]: Hung up error: $error"));
    }
  }

  // TODO: remove, action deprecated

  // void wakeUpAndroid() async {
  //   try {
  //     await _callkeepAndroidService.wakeUpApp(path: "/");
  //     emit(state.update.addAction(action: "[Android]:wake up android:"));
  //   } catch (error) {
  //     emit(state.update.addAction(action: "[Android]: wake up android error: $error"));
  //   }
  // }

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
      var result = await _callkeep.reportNewIncomingCall(call1Identifier, numberMock, 'User Name', true);
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
      var result = await _callkeep.reportNewIncomingCall(call2Identifier, numberMock1, 'User Name 1', true);
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
      var result = await _callkeep.startCall(call1Identifier, numberMock, 'User Name', true);
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
      await _callkeep.reportUpdateCall(call1Identifier, numberMock, 'User Name', true);

      emit(state.update.addAction(action: "Success report update call"));
    } catch (error) {
      emit(state.update.addAction(action: "Error report update  error: $error"));
    }
  }

  void reportEndCall() async {
    try {
      await _callkeep.reportEndCall(call1Identifier, CallkeepEndCallReason.declinedElsewhere);
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
      await _callkeep.setHeld(call1Identifier, !_isHold);
      emit(state.update.addAction(action: "Held action sent"));
    } catch (error) {
      emit(state.update.addAction(action: "Error set held  error: $error"));
    }
  }

  void setMuted() async {
    try {
      await _callkeep.setMuted(call1Identifier, !_isMuted);
      emit(state.update.addAction(action: "Mute action sent"));
    } catch (error) {
      emit(state.update.addAction(action: "Error set muted  error: $error"));
    }
  }

  void setSpeaker() async {
    try {
      await _callkeep.setSpeaker(call1Identifier, !_speakerEnabled);
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
  void didPushIncomingCall(CallkeepHandle handle, String? displayName, bool video, String callId, CallkeepIncomingCallError? error) {
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
  void endCallReceived(String callId, String number, bool video, DateTime createdTime, DateTime? acceptedTime, DateTime? hungUpTime) {
    emit(state.update.addAction(action: "End call received"));
  }
}
