import 'dart:io' show Platform;

import 'package:bloc/bloc.dart';
import 'package:flutter/foundation.dart' show kIsWeb;

import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/constants.dart';
import 'package:webtrit_callkeep_example/core/log_entry.dart';

part 'tests_state.dart';

class TestsCubit extends Cubit<TestsState> implements CallkeepDelegate, CallkeepBackgroundServiceDelegate {
  TestsCubit(this._callkeep, this._pushService) : super(const TestsState()) {
    _callkeep.setDelegate(this);
    if (!kIsWeb && Platform.isAndroid) {
      _pushService.setBackgroundServiceDelegate(this);
    }
  }

  final Callkeep _callkeep;
  final BackgroundPushNotificationService _pushService;

  @override
  Future<void> close() {
    _callkeep.setDelegate(null);
    if (!kIsWeb && Platform.isAndroid) {
      _pushService.setBackgroundServiceDelegate(null);
    }
    return super.close();
  }

  void clearLog() => emit(state.clearLog());

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  Future<void> _run(String label, Future<void> Function() body) async {
    if (state.isRunning) return;
    emit(state.copyWith(isRunning: true).log(LogEntry.info('▶ $label')));
    try {
      await body();
      emit(state.copyWith(isRunning: false).log(LogEntry.success('✓ $label done')));
    } catch (e) {
      emit(state.copyWith(isRunning: false).log(LogEntry.error('✗ $label: $e')));
    }
  }

  Future<void> _setup() async {
    await _callkeep.setUp(const CallkeepOptions(
      ios: CallkeepIOSOptions(
        localizedName: 'en',
        maximumCallGroups: 2,
        maximumCallsPerCallGroup: 1,
        supportedHandleTypes: {CallkeepHandleType.number},
      ),
      android: CallkeepAndroidOptions(),
    ));
    emit(state.log(LogEntry.success('setUp: ok')));
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  void testSpamSameIncomingCalls() => _run('Spam same incoming calls (direct)', () async {
        await _setup();
        for (var i = 0; i < 4; i++) {
          final err = await _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Name);
          emit(state.log(err == null ? LogEntry.success('reportIncoming[$i]: ok') : LogEntry.error('reportIncoming[$i]: ${err.name}')));
        }
      });

  void testSpamDifferentIncomingCalls() => _run('Spam different incoming calls', () async {
        await _setup();
        for (final id in [call1Identifier, call2Identifier]) {
          final handle = id == call1Identifier ? call1Number : call2Number;
          final err = await _callkeep.reportNewIncomingCall(id, handle, displayName: 'Call $id');
          _callkeep.reportNewIncomingCall(id, handle, displayName: 'Call $id');
          emit(state.log(err == null ? LogEntry.success('reportIncoming[$id]: ok') : LogEntry.error('reportIncoming[$id]: ${err.name}')));
        }
      });

  void testSpamPushSameIncomingCalls() => _run('Spam same incoming calls (push)', () async {
        await _setup();
        for (var i = 0; i < 4; i++) {
          AndroidCallkeepServices.backgroundPushNotificationBootstrapService
              .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Name);
          emit(state.log(LogEntry.info('push reportIncoming[$i]: dispatched')));
        }
      });

  void testMixedSpam() => _run('Mixed push + direct spam', () async {
        await _setup();
        for (var i = 0; i < 3; i++) {
          AndroidCallkeepServices.backgroundPushNotificationBootstrapService
              .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Name);
          emit(state.log(LogEntry.info('push[$i]: dispatched')));
          final err = await _callkeep.reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Name);
          emit(state.log(err == null ? LogEntry.success('direct[$i]: ok') : LogEntry.error('direct[$i]: ${err.name}')));
        }
      });

  void tearDown() => _run('Tear down', () async {
        await _callkeep.tearDown();
      });

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
  void didReset() => emit(state.log(LogEntry.event('[cb] didReset')));

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
    emit(state.log(LogEntry.event('[cb] performSetHeld id=$callId held=$onHold')));
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    emit(state.log(LogEntry.event('[cb] performSetMuted id=$callId muted=$muted')));
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
