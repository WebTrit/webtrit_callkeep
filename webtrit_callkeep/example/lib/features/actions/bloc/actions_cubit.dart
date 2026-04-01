import 'package:bloc/bloc.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/constants.dart';
import 'package:webtrit_callkeep_example/core/log_entry.dart';

part 'actions_state.dart';

class ActionsCubit extends Cubit<ActionsState>
    implements CallkeepDelegate, CallkeepBackgroundServiceDelegate, CallkeepLogsDelegate {
  ActionsCubit(this._callkeep) : super(const ActionsState()) {
    _callkeep.setDelegate(this);
  }

  final Callkeep _callkeep;
  int _lineCounter = 0;

  /// Ensures a [CallLine] with [callId] exists in [s].
  /// If absent, appends a new line using [callId] as both id and label.
  ActionsState _ensureLine(ActionsState s, String callId) {
    if (s.lines.any((l) => l.id == callId)) return s;
    return s.copyWith(lines: [...s.lines, CallLine(id: callId, label: callId)]);
  }

  /// Removes the line with [callId] and adjusts activeLineId if needed.
  ActionsState _removeLine(ActionsState s, String callId) {
    final newLines = s.lines.where((l) => l.id != callId).toList();
    ActionsState next = s.copyWith(lines: newLines);
    if (s.activeLineId == callId) {
      next = newLines.isNotEmpty ? next.copyWith(activeLineId: newLines.last.id) : next.withNoActiveLine();
    }
    return next;
  }

  @override
  Future<void> close() {
    _callkeep.setDelegate(null);
    WebtritCallkeepLogs().setLogsDelegate(null);
    return super.close();
  }

  void clearLog() => emit(state.clearLog());

  // ---------------------------------------------------------------------------
  // Line management
  // ---------------------------------------------------------------------------

  void addLine() {
    final id = 'line-${++_lineCounter}';
    final label = 'Line $_lineCounter';
    final newLine = CallLine(id: id, label: label);
    emit(state.copyWith(lines: [...state.lines, newLine], activeLineId: id));
  }

  void selectLine(String id) => emit(state.copyWith(activeLineId: id));

  void removeLine(String id) => emit(_removeLine(state, id));

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
        state.copyWith(isSetUp: false, connections: []).log(LogEntry.success('tearDown: ok')),
      );
    } catch (e) {
      emit(state.log(LogEntry.error('tearDown: $e')));
    }
  }

  void getPushToken() async {
    try {
      final token = await _callkeep.pushTokenForPushTypeVoIP();
      emit(state.log(LogEntry.info('pushToken: ${token ?? "null"}')));
    } catch (e) {
      emit(state.log(LogEntry.error('pushToken: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // Incoming calls
  // ---------------------------------------------------------------------------

  void reportIncomingCall() async {
    try {
      final err = await _callkeep.reportNewIncomingCall(
        state.currentCallId,
        call1Number,
        displayName: call1Name,
        hasVideo: false,
      );
      if (err != null) {
        emit(state.log(LogEntry.error('reportIncoming[${state.currentCallId}]: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('reportIncoming[${state.currentCallId}]: ok')));
        await _syncConnections();
      }
    } catch (e) {
      emit(state.log(LogEntry.error('reportIncoming: $e')));
    }
  }

  void incomingCallViaPush() {
    try {
      AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
        state.currentCallId,
        call1Number,
        displayName: call1Name,
      );
      emit(state.log(LogEntry.info('reportIncoming via push: dispatched')));
    } catch (e) {
      emit(state.log(LogEntry.error('reportIncoming via push: $e')));
    }
  }

  void reportEndCall(CallkeepEndCallReason reason) async {
    try {
      await _callkeep.reportEndCall(state.currentCallId, call1Name, reason);
      emit(state.log(LogEntry.success('reportEndCall(${reason.name}): ok')));
      await _syncConnections();
    } catch (e) {
      emit(state.log(LogEntry.error('reportEndCall: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // Outgoing calls
  // ---------------------------------------------------------------------------

  void startOutgoingCall(String number) async {
    final handle = CallkeepHandle.number(number);
    try {
      final err = await _callkeep.startCall(
        state.currentCallId,
        handle,
        displayNameOrContactIdentifier: number,
        hasVideo: false,
      );
      if (err != null) {
        emit(state.log(LogEntry.error('startCall → $number: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('startCall → $number: ok')));
        await _syncConnections();
      }
    } catch (e) {
      emit(state.log(LogEntry.error('startCall: $e')));
    }
  }

  void reportConnectingOutgoingCall() async {
    try {
      await _callkeep.reportConnectingOutgoingCall(state.currentCallId);
      emit(state.log(LogEntry.success('reportConnecting: ok')));
      await _syncConnections();
    } catch (e) {
      emit(state.log(LogEntry.error('reportConnecting: $e')));
    }
  }

  void reportConnectedOutgoingCall() async {
    try {
      await _callkeep.reportConnectedOutgoingCall(state.currentCallId);
      emit(state.log(LogEntry.success('reportConnected: ok')));
      await _syncConnections();
    } catch (e) {
      emit(state.log(LogEntry.error('reportConnected: $e')));
    }
  }

  void reportUpdateCall() async {
    try {
      await _callkeep.reportUpdateCall(
        state.currentCallId,
        handle: call1Number,
        displayName: call1Name,
        hasVideo: false,
      );
      emit(state.log(LogEntry.success('reportUpdate: ok')));
      await _syncConnections();
    } catch (e) {
      emit(state.log(LogEntry.error('reportUpdate: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // In-call controls
  // ---------------------------------------------------------------------------

  void answerCall() async {
    try {
      final err = await _callkeep.answerCall(state.currentCallId);
      if (err != null) {
        emit(state.log(LogEntry.error('answerCall: ${err.name}')));
      } else {
        emit(state.updateLine(state.currentCallId, isAnswered: true).log(LogEntry.success('answerCall: ok')));
        await _syncConnections();
      }
    } catch (e) {
      emit(state.log(LogEntry.error('answerCall: $e')));
    }
  }

  void endCall() async {
    try {
      final err = await _callkeep.endCall(state.currentCallId);
      if (err != null) {
        emit(state.log(LogEntry.error('endCall: ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('endCall: ok')));
        await _syncConnections();
      }
    } catch (e) {
      emit(state.log(LogEntry.error('endCall: $e')));
    }
  }

  void setHeld() async {
    try {
      final onHold = !state.isHold;
      final err = await _callkeep.setHeld(state.currentCallId, onHold: onHold);
      if (err != null) {
        emit(state.log(LogEntry.error('setHeld($onHold): ${err.name}')));
      } else {
        emit(state.updateLine(state.currentCallId, isHold: onHold).log(LogEntry.success('setHeld($onHold): ok')));
        await _syncConnections();
      }
    } catch (e) {
      emit(state.log(LogEntry.error('setHeld: $e')));
    }
  }

  void setMuted() async {
    try {
      final muted = !state.isMuted;
      final err = await _callkeep.setMuted(state.currentCallId, muted: muted);
      if (err != null) {
        emit(state.log(LogEntry.error('setMuted($muted): ${err.name}')));
      } else {
        emit(state.updateLine(state.currentCallId, isMuted: muted).log(LogEntry.success('setMuted($muted): ok')));
        await _syncConnections();
      }
    } catch (e) {
      emit(state.log(LogEntry.error('setMuted: $e')));
    }
  }

  void sendDTMF(String key) async {
    try {
      final err = await _callkeep.sendDTMF(state.currentCallId, key);
      if (err != null) {
        emit(state.log(LogEntry.error('sendDTMF($key): ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('sendDTMF($key): ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('sendDTMF: $e')));
    }
  }

  void setAudioDevice(CallkeepAudioDeviceType type) async {
    final device = CallkeepAudioDevice(type: type);
    try {
      final err = await _callkeep.setAudioDevice(state.currentCallId, device);
      if (err != null) {
        emit(state.log(LogEntry.error('setAudioDevice(${type.name}): ${err.name}')));
      } else {
        emit(state.log(LogEntry.success('setAudioDevice(${type.name}): ok')));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('setAudioDevice: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // Sound
  // ---------------------------------------------------------------------------

  void playRingback() async {
    try {
      await WebtritCallkeepSound().playRingbackSound();
      emit(state.copyWith(isRingbackPlaying: true).log(LogEntry.success('playRingback: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('playRingback: $e')));
    }
  }

  void stopRingback() async {
    try {
      await WebtritCallkeepSound().stopRingbackSound();
      emit(state.copyWith(isRingbackPlaying: false).log(LogEntry.success('stopRingback: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('stopRingback: $e')));
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

  void getConnectionByCallId() async {
    try {
      final conn = await CallkeepConnections().getConnection(state.currentCallId);
      if (conn == null) {
        emit(state.log(LogEntry.info('getConnection[${state.currentCallId}]: not found')));
      } else {
        emit(state.log(LogEntry.info(
          'getConnection[${conn.callId}]: state=${conn.state.name}'
          '${conn.disconnectCause != null ? " cause=${conn.disconnectCause!.type.name}" : ""}',
        )));
      }
    } catch (e) {
      emit(state.log(LogEntry.error('getConnection: $e')));
    }
  }

  /// Silently fetches the current native connections and updates state.
  /// Does not add a log entry so it can be called frequently without noise.
  Future<void> _syncConnections() async {
    try {
      final list = await CallkeepConnections().getConnections();
      emit(state.copyWith(connections: list));
    } catch (_) {
      // best-effort: ignore errors from background syncs
    }
  }

  // ---------------------------------------------------------------------------
  // Permissions
  // ---------------------------------------------------------------------------

  void checkPermissions() async {
    try {
      final result = await WebtritCallkeepPermissions().checkPermissionsStatus(CallkeepPermission.values);
      final str = result.entries.map((e) => '${e.key.name}=${e.value.name}').join(', ');
      emit(state.log(LogEntry.info('checkPerms: ${str.isEmpty ? "n/a" : str}')));
    } catch (e) {
      emit(state.log(LogEntry.error('checkPerms: $e')));
    }
  }

  void requestPermissions() async {
    try {
      final result = await WebtritCallkeepPermissions().requestPermissions(CallkeepPermission.values);
      final str = result.entries.map((e) => '${e.key.name}=${e.value.name}').join(', ');
      emit(state.log(LogEntry.info('requestPerms: ${str.isEmpty ? "n/a" : str}')));
    } catch (e) {
      emit(state.log(LogEntry.error('requestPerms: $e')));
    }
  }

  void getBatteryMode() async {
    try {
      final mode = await WebtritCallkeepPermissions().getBatteryMode();
      emit(state.log(LogEntry.info('batteryMode: ${mode.name}')));
    } catch (e) {
      emit(state.log(LogEntry.error('batteryMode: $e')));
    }
  }

  void getFullScreenIntentStatus() async {
    try {
      final status = await WebtritCallkeepPermissions().getFullScreenIntentPermissionStatus();
      emit(state.log(LogEntry.info('fullScreenIntent: ${status.name}')));
    } catch (e) {
      emit(state.log(LogEntry.error('fullScreenIntent: $e')));
    }
  }

  void openFullScreenIntentSettings() async {
    try {
      await WebtritCallkeepPermissions().openFullScreenIntentSettings();
      emit(state.log(LogEntry.info('openFullScreenIntentSettings: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('openFullScreenIntentSettings: $e')));
    }
  }

  void openSettings() async {
    try {
      await WebtritCallkeepPermissions().openSettings();
      emit(state.log(LogEntry.info('openSettings: ok')));
    } catch (e) {
      emit(state.log(LogEntry.error('openSettings: $e')));
    }
  }

  // ---------------------------------------------------------------------------
  // Logs
  // ---------------------------------------------------------------------------

  void toggleLogsDelegate() {
    if (state.isLogsDelegateActive) {
      WebtritCallkeepLogs().setLogsDelegate(null);
      emit(state.copyWith(isLogsDelegateActive: false).log(LogEntry.info('logs delegate: OFF')));
    } else {
      WebtritCallkeepLogs().setLogsDelegate(this);
      emit(state.copyWith(isLogsDelegateActive: true).log(LogEntry.info('logs delegate: ON')));
    }
  }

  @override
  void onLog(CallkeepLogType type, String tag, String message) {
    emit(state.log(LogEntry.event('[native/${type.name}] $tag: $message')));
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
  ) async {
    final errStr = error != null ? ' err=${error.name}' : '';
    // Ensure a line exists for this callId; select it when there is no error.
    var s = _ensureLine(state, callId);
    if (error == null) s = s.copyWith(activeLineId: callId);
    emit(s.log(LogEntry.event('[cb] didPushIncomingCall id=$callId$errStr')));
    if (error == null) await _syncConnections();
  }

  @override
  void didActivateAudioSession() => emit(state.log(LogEntry.event('[cb] didActivateAudioSession')));

  @override
  void didDeactivateAudioSession() => emit(state.log(LogEntry.event('[cb] didDeactivateAudioSession')));

  @override
  void didReset() => emit(state.log(LogEntry.event('[cb] didReset')));

  @override
  Future<bool> performStartCall(String callId, CallkeepHandle handle, String? displayName, bool video) async {
    // Ensure a line exists and select it so all controls target this call.
    final s = _ensureLine(state, callId).copyWith(activeLineId: callId);
    emit(s.log(LogEntry.event('[cb] performStartCall id=$callId')));
    await _syncConnections();
    return true;
  }

  @override
  Future<bool> performAnswerCall(String callId) async {
    emit(_ensureLine(state, callId).updateLine(callId, isAnswered: true).log(
          LogEntry.event('[cb] performAnswerCall id=$callId'),
        ));
    await _syncConnections();
    return true;
  }

  @override
  Future<bool> performEndCall(String callId) async {
    // Remove the line when the native side ends the call.
    emit(_removeLine(state, callId).log(LogEntry.event('[cb] performEndCall id=$callId')));
    await _syncConnections();
    return true;
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) async {
    emit(_ensureLine(state, callId).updateLine(callId, isHold: onHold).log(
          LogEntry.event('[cb] performSetHeld id=$callId held=$onHold'),
        ));
    await _syncConnections();
    return true;
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) async {
    emit(_ensureLine(state, callId).updateLine(callId, isMuted: muted).log(
          LogEntry.event('[cb] performSetMuted id=$callId muted=$muted'),
        ));
    await _syncConnections();
    return true;
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) {
    emit(state.log(LogEntry.event('[cb] performSendDTMF id=$callId key=$key')));
    return Future.value(true);
  }

  @override
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device) {
    emit(state.log(LogEntry.event('[cb] performAudioDeviceSet id=$callId device=${device.type.name}')));
    return Future.value(true);
  }

  @override
  Future<bool> performAudioDevicesUpdate(String callId, List<CallkeepAudioDevice> devices) {
    emit(state.log(
      LogEntry.event('[cb] performAudioDevicesUpdate id=$callId [${devices.map((d) => d.type.name).join(',')}]'),
    ));
    return Future.value(true);
  }
}
