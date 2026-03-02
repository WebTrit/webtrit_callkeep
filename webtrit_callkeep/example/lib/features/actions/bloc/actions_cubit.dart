import 'package:bloc/bloc.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/constants.dart';
import 'package:webtrit_callkeep_example/core/log_entry.dart';

part 'actions_state.dart';

class ActionsCubit extends Cubit<ActionsState> implements CallkeepDelegate, CallkeepBackgroundServiceDelegate {
  ActionsCubit(this._callkeep) : super(const ActionsState()) {
    _callkeep.setDelegate(this);
  }

  final Callkeep _callkeep;

  @override
  Future<void> close() {
    _callkeep.setDelegate(null);
    return super.close();
  }

  void clearLog() => emit(state.clearLog());

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  void setup() async {
    try {
      await _callkeep.setUp(const CallkeepOptions(
        ios: CallkeepIOSOptions(
          localizedName: 'en',
          maximumCallGroups: 2,
          maximumCallsPerCallGroup: 1,
          supportedHandleTypes: {CallkeepHandleType.number},
        ),
        android: CallkeepAndroidOptions(),
      ));
      emit(state.copyWith(isSetUp: true).log(LogEntry.success('setUp: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('setUp: $e')));
    }
  }

  void isSetup() async {
    try {
      final result = await _callkeep.isSetUp();
      emit(state.log(LogEntry.info('isSetUp → $result')));
    } catch (e) {
      emit(state.log(LogEntry.error('isSetUp: $e')));
    }
  }

  void tearDown() async {
    try {
      await _callkeep.tearDown();
      emit(
        state
            .copyWith(isSetUp: false, isHold: false, isMuted: false, connections: [])
            .log(LogEntry.success('tearDown: ok')),
      );
    } catch (e) {
      emit(state.log(LogEntry.error('tearDown: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // Incoming calls
  // ---------------------------------------------------------------------------

  void reportIncomingCall1() async {
    try {
      final err = await _callkeep.reportNewIncomingCall(
        call1Identifier,
        call1Number,
        displayName: call1Name,
        hasVideo: false,
      );
      if (err != null) {
        emit(state.log(LogEntry.error('reportIncoming[1]: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('reportIncoming[1]: ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('reportIncoming[1]: $e')));
    }
  }

  void reportIncomingCall2() async {
    try {
      final err = await _callkeep.reportNewIncomingCall(
        call2Identifier,
        call2Number,
        displayName: call2Name,
        hasVideo: false,
      );
      if (err != null) {
        emit(state.log(LogEntry.error('reportIncoming[2]: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('reportIncoming[2]: ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('reportIncoming[2]: $e')));
    }
  }

  void incomingCallViaPush() {
    try {
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
        call1Identifier,
        call1Number,
        displayName: call1Name,
      );
      emit(state.log(LogEntry.info('reportIncoming via push: dispatched')));
    } catch (e) {
      emit(state.log(LogEntry.error('reportIncoming via push: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // Outgoing calls
  // ---------------------------------------------------------------------------

  void startOutgoingCall() async {
    try {
      final err = await _callkeep.startCall(
        call1Identifier,
        call1Number,
        displayNameOrContactIdentifier: call1Name,
        hasVideo: false,
      );
      if (err != null) {
        emit(state.log(LogEntry.error('startCall: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('startCall: ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('startCall: $e')));
    }
  }

  void reportConnectingOutgoingCall() async {
    try {
      await _callkeep.reportConnectingOutgoingCall(call1Identifier);
      emit(state.log(LogEntry.success('reportConnecting: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('reportConnecting: $e')));
    }
  }

  void reportConnectedOutgoingCall() async {
    try {
      await _callkeep.reportConnectedOutgoingCall(call1Identifier);
      emit(state.log(LogEntry.success('reportConnected: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('reportConnected: $e')));
    }
  }

  void reportUpdateCall() async {
    try {
      await _callkeep.reportUpdateCall(
        call1Identifier,
        handle: call1Number,
        displayName: call1Name,
        hasVideo: false,
      );
      emit(state.log(LogEntry.success('reportUpdate: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('reportUpdate: $e')));
    }
  }

  void reportEndCall() async {
    try {
      await _callkeep.reportEndCall(call1Identifier, call1Name, CallkeepEndCallReason.declinedElsewhere);
      emit(state.log(LogEntry.success('reportEndCall: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('reportEndCall: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // In-call controls
  // ---------------------------------------------------------------------------

  void answerCall() async {
    try {
      final err = await _callkeep.answerCall(call1Identifier);
      if (err != null) {
        emit(state.log(LogEntry.error('answerCall: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('answerCall: ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('answerCall: $e')));
    }
  }

  void endCall() async {
    try {
      final err = await _callkeep.endCall(call1Identifier);
      if (err != null) {
        emit(state.log(LogEntry.error('endCall: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('endCall: ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('endCall: $e')));
    }
  }

  void setHeld() async {
    try {
      final onHold = !state.isHold;
      final err = await _callkeep.setHeld(call1Identifier, onHold: onHold);
      if (err != null) {
        emit(state.log(LogEntry.error('setHeld($onHold): ${err.name}')));
      } else {
        emit(state.copyWith(isHold: onHold).log(LogEntry.success('setHeld($onHold): ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('setHeld: $e')));
    }
  }

  void setMuted() async {
    try {
      final muted = !state.isMuted;
      final err = await _callkeep.setMuted(call1Identifier, muted: muted);
      if (err != null) {
        emit(state.log(LogEntry.error('setMuted($muted): ${err.name}')));
      } else {
        emit(state.copyWith(isMuted: muted).log(LogEntry.success('setMuted($muted): ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('setMuted: $e')));
    }
  }

  void sendDTMF() async {
    try {
      final err = await _callkeep.sendDTMF(call1Identifier, 'A');
      if (err != null) {
        emit(state.log(LogEntry.error('sendDTMF(A): ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('sendDTMF(A): ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('sendDTMF: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // Connections
  // ---------------------------------------------------------------------------

  void refreshConnections() async {
    try {
      final list = await CallkeepConnections().getConnections();
      emit(state.copyWith(connections: list).log(LogEntry.info('connections: ${list.length} active')));
    } catch (e) {
      emit(state.log(LogEntry.error('getConnections: $e')));
    }
  }

  void cleanConnections() async {
    try {
      await CallkeepConnections().cleanConnections();
      emit(state.copyWith(connections: []).log(LogEntry.success('cleanConnections: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('cleanConnections: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // CallkeepDelegate callbacks
  // ---------------------------------------------------------------------------

  @override
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video) =>
      emit(state.log(LogEntry.event('[cb] continueStartCallIntent: ${handle.value}')));

  @override
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  ) {
    final errStr = error != null ? ' err=${error.name}' : '';
    emit(state.log(LogEntry.event('[cb] didPushIncomingCall id=$callId$errStr')));
  }

  @override
  void didActivateAudioSession() => emit(state.log(LogEntry.event('[cb] didActivateAudioSession')));

  @override
  void didDeactivateAudioSession() => emit(state.log(LogEntry.event('[cb] didDeactivateAudioSession')));

  @override
  void didReset() => emit(state.copyWith(isHold: false, isMuted: false).log(LogEntry.event('[cb] didReset')));

  @override
  Future<bool> performStartCall(String callId, CallkeepHandle handle, String? displayName, bool video) {
    emit(state.log(LogEntry.event('[cb] performStartCall id=$callId')));
    return Future.value(true);
  }

  @override
  Future<bool> performAnswerCall(String callId) {
    emit(state.log(LogEntry.event('[cb] performAnswerCall id=$callId')));
    return Future.value(true);
  }

  @override
  Future<bool> performEndCall(String callId) {
    emit(state.log(LogEntry.event('[cb] performEndCall id=$callId')));
    return Future.value(true);
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) {
    emit(state.copyWith(isHold: onHold).log(LogEntry.event('[cb] performSetHeld id=$callId held=$onHold')));
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    emit(state.copyWith(isMuted: muted).log(LogEntry.event('[cb] performSetMuted id=$callId muted=$muted')));
    return Future.value(true);
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) {
    emit(state.log(LogEntry.event('[cb] performSendDTMF id=$callId key=$key')));
    return Future.value(true);
  }

  @override
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device) {
    emit(state.log(LogEntry.event('[cb] performAudioDeviceSet id=$callId device=${device.name}')));
    return Future.value(true);
  }

  @override
  Future<bool> performAudioDevicesUpdate(String callId, List<CallkeepAudioDevice> devices) {
    emit(state.log(
      LogEntry.event('[cb] performAudioDevicesUpdate id=$callId [${devices.map((d) => d.name).join(',')}]'),
    ));
    return Future.value(true);
  }
}
