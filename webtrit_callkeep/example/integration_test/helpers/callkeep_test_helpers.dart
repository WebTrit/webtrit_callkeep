import 'dart:async';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const kTestOptions = CallkeepOptions(
  ios: CallkeepIOSOptions(
    localizedName: 'Integration Tests',
    maximumCallGroups: 2,
    maximumCallsPerCallGroup: 1,
    supportedHandleTypes: {CallkeepHandleType.number},
  ),
  android: CallkeepAndroidOptions(),
);

const kTestHandle1 = CallkeepHandle.number('380000000000');
const kTestHandle2 = CallkeepHandle.number('380000000001');

var _testIdCounter = 0;
String nextTestId([String prefix = 'test']) => '$prefix-${_testIdCounter++}';

// ---------------------------------------------------------------------------
// Recording delegate
// ---------------------------------------------------------------------------

class RecordingDelegate implements CallkeepDelegate {
  // call-action lists
  final List<String> startCallIds = [];
  final List<String> answerCallIds = [];
  final List<String> endCallIds = [];
  final List<({String callId, bool onHold})> holdEvents = [];
  final List<({String callId, bool muted})> muteEvents = [];
  final List<({String callId, String key})> dtmfEvents = [];
  final List<({String callId, CallkeepAudioDevice device})> audioDeviceEvents = [];
  final List<({String callId, List<CallkeepAudioDevice> devices})> audioDevicesUpdateEvents = [];

  // audio session tracking
  int activateAudioSessionCount = 0;
  int deactivateAudioSessionCount = 0;

  // push-incoming tracking
  final List<({String callId, CallkeepIncomingCallError? error})> pushIncomingEvents = [];
  List<({String callId, CallkeepIncomingCallError? error})> get didPushEvents => pushIncomingEvents;

  // per-call-action callbacks
  void Function(String callId)? onPerformStartCall;
  void Function(String callId)? onPerformAnswerCall;
  void Function(String callId)? onPerformEndCall;
  void Function(String callId, bool onHold)? onPerformSetHeld;
  void Function(String callId, bool muted)? onPerformSetMuted;
  void Function(String callId, String key)? onPerformSendDTMF;
  void Function(String callId, CallkeepAudioDevice device)? onPerformAudioDeviceSet;
  void Function(String callId, List<CallkeepAudioDevice> devices)? onPerformAudioDevicesUpdate;

  // audio-session + push callbacks
  void Function()? onDidActivateAudioSession;
  void Function()? onDidDeactivateAudioSession;
  void Function(String callId, CallkeepIncomingCallError? error)? onDidPushIncomingCall;

  // injectable overrides — replace the default Future<bool> implementation
  Future<bool> Function(String callId)? performAnswerCallOverride;
  Future<bool> Function(String callId)? performEndCallOverride;

  @override
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video) {}

  @override
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  ) {
    pushIncomingEvents.add((callId: callId, error: error));
    onDidPushIncomingCall?.call(callId, error);
  }

  @override
  void didActivateAudioSession() {
    activateAudioSessionCount++;
    onDidActivateAudioSession?.call();
  }

  @override
  void didDeactivateAudioSession() {
    deactivateAudioSessionCount++;
    onDidDeactivateAudioSession?.call();
  }

  @override
  void didReset() {}

  @override
  Future<bool> performStartCall(String callId, CallkeepHandle handle, String? displayName, bool video) {
    startCallIds.add(callId);
    onPerformStartCall?.call(callId);
    return Future.value(true);
  }

  @override
  Future<bool> performAnswerCall(String callId) {
    if (performAnswerCallOverride != null) return performAnswerCallOverride!(callId);
    answerCallIds.add(callId);
    onPerformAnswerCall?.call(callId);
    return Future.value(true);
  }

  @override
  Future<bool> performEndCall(String callId) {
    if (performEndCallOverride != null) return performEndCallOverride!(callId);
    endCallIds.add(callId);
    onPerformEndCall?.call(callId);
    return Future.value(true);
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) {
    holdEvents.add((callId: callId, onHold: onHold));
    onPerformSetHeld?.call(callId, onHold);
    return Future.value(true);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    muteEvents.add((callId: callId, muted: muted));
    onPerformSetMuted?.call(callId, muted);
    return Future.value(true);
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) {
    dtmfEvents.add((callId: callId, key: key));
    onPerformSendDTMF?.call(callId, key);
    return Future.value(true);
  }

  @override
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device) {
    audioDeviceEvents.add((callId: callId, device: device));
    onPerformAudioDeviceSet?.call(callId, device);
    return Future.value(true);
  }

  @override
  Future<bool> performAudioDevicesUpdate(String callId, List<CallkeepAudioDevice> devices) {
    audioDevicesUpdateEvents.add((callId: callId, devices: devices));
    onPerformAudioDevicesUpdate?.call(callId, devices);
    return Future.value(true);
  }
}

// ---------------------------------------------------------------------------
// Async helpers
// ---------------------------------------------------------------------------

Future<T> waitFor<T>(
  Future<T> future, {
  String label = 'callback',
  Duration timeout = const Duration(seconds: 10),
}) {
  return future.timeout(
    timeout,
    onTimeout: () => throw TimeoutException('$label did not fire within timeout'),
  );
}

Future<CallkeepConnection?> waitForConnection(
  String callId, {
  Duration timeout = const Duration(seconds: 5),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    final conn = await CallkeepConnections().getConnection(callId);
    if (conn != null) return conn;
    await Future.delayed(const Duration(milliseconds: 100));
  }
  return null;
}

Future<void> waitForConnectionGone(
  String callId, {
  Duration timeout = const Duration(seconds: 5),
}) async {
  // A disconnected connection is left in the native ConnectionManager map until tearDown, so
  // getConnection() never returns null for it - treat stateDisconnected as "gone" too, otherwise
  // this just sleeps the full timeout without observing anything.
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    final conn = await CallkeepConnections().getConnection(callId);
    if (conn == null || conn.state == CallkeepConnectionState.stateDisconnected) return;
    await Future.delayed(const Duration(milliseconds: 100));
  }
}
