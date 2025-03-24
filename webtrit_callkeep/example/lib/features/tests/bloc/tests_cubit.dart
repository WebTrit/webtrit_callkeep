import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

import 'package:bloc/bloc.dart';
import 'package:meta/meta.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import '../../../app/constants.dart';

part 'tests_state.dart';

class TestsCubit extends Cubit<TestsState> implements CallkeepDelegate, CallkeepBackgroundServiceDelegate {
  TestsCubit(
    this._callkeep,
    this._callkeepBackgroundService,
  ) : super(const TestsUpdate([])) {
    _callkeep.setDelegate(this);

    if (!kIsWeb) {
      if (Platform.isAndroid) {
        _callkeepBackgroundService.setBackgroundServiceDelegate(this);
      }
    }
  }

  final Callkeep _callkeep;
  final BackgroundPushNotificationService _callkeepBackgroundService;

  @override
  Future<void> close() {
    _callkeep.setDelegate(null);
    if (!kIsWeb) {
      if (Platform.isAndroid) {
        _callkeepBackgroundService.setBackgroundServiceDelegate(null);
      }
    }
    return super.close();
  }

  Future<void> setup() async {
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

  void spamSameIncomingCalls() async {
    await setup();
    try {
      await _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');
      _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');
      await _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');
      _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');

      emit(state.update.addAction(action: "twiceIncomingCallWithSameId success"));
    } catch (error) {
      emit(state.update.addAction(action: "twiceIncomingCallWithSameId error: $error"));
    }
  }

  void spamDifferentIncomingCalls() async {
    await setup();
    try {
      await _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);
      _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);

      await _callkeep.reportNewIncomingCall(call2Identifier, call1Number, displayName: call2Identifier);
      _callkeep.reportNewIncomingCall(call2Identifier, call2Number, displayName: call2Identifier);

      emit(state.update.addAction(action: "spamDifferentIncomingCalls success"));
    } catch (error) {
      emit(state.update.addAction(action: "spamDifferentIncomingCalls error: $error"));
    }
  }

  void spamBackgroundSameIncomingCalls() async {
    await setup();
    try {
      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);

      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);

      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);

      emit(state.update.addAction(action: "twiceIncomingCallWithSameId success"));
    } catch (error) {
      emit(state.update.addAction(action: "twiceIncomingCallWithSameId error: $error"));
    }
  }

  void spamSameIncomingCallsAndBackground() async {
    await setup();
    try {
      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);

      await _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');
      _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);

      await _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');
      _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: 'User Name');

      await AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService
          .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Identifier);

      emit(state.update.addAction(action: "twiceIncomingCallWithSameId success"));
    } catch (error) {
      emit(state.update.addAction(action: "twiceIncomingCallWithSameId error: $error"));
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
    emit(state.update.addAction(action: "Delegate held: $onHold"));
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
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
