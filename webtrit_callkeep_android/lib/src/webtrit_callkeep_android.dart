import 'dart:async';
import 'dart:convert';
import 'dart:ui';

import 'package:flutter/material.dart';

import 'package:webtrit_callkeep_android/src/common/common.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The Android implementation of [WebtritCallkeepPlatform].
class WebtritCallkeepAndroid extends WebtritCallkeepPlatform {
  /// Registers this class as the default instance of [WebtritCallkeepPlatform].
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeepAndroid();
  }

  // APIs for initializing the background service isolates to be used in the main isolate.
  final _backgroundSignalingIsolateBootstrapApi = PHostBackgroundSignalingIsolateBootstrapApi();
  final _backgroundPushNotificationIsolateBootstrapApi = PHostBackgroundPushNotificationIsolateBootstrapApi();

  // APIs for working with the background service isolates.
  final _backgroundSignalingIsolateApi = PHostBackgroundSignalingIsolateApi();
  final _backgroundPushNotificationIsolateApi = PHostBackgroundPushNotificationIsolateApi();

  final _pushRegistryApi = PPushRegistryHostApi();
  final _api = PHostApi();

  final _soundApi = PHostSoundApi();
  final _connectionsApi = PHostConnectionsApi();

  int? _signalingIsolatePluginCallbackHandle;
  int? _pushNotificationIsolatePluginCallbackHandle;

  int? _onSignalingServiceChangedLifecycleHandle;
  int? _onSignalingServiceStartHandle;
  int? _onPushNotificationNotificationSync;

  final _permissionsApi = PHostPermissionsApi();

  @override
  void setDelegate(
    CallkeepDelegate? delegate,
  ) {
    if (delegate != null) {
      PDelegateFlutterApi.setUp(_CallkeepDelegateRelay(delegate));
    } else {
      PDelegateFlutterApi.setUp(null);
    }
  }

  @override
  void setPushRegistryDelegate(
    PushRegistryDelegate? delegate,
  ) {
    if (delegate != null) {
      PPushRegistryDelegateFlutterApi.setUp(_PushRegistryDelegateRelay(delegate));
    } else {
      PPushRegistryDelegateFlutterApi.setUp(null);
    }
  }

  @override
  void setLogsDelegate(
    CallkeepLogsDelegate? delegate,
  ) {
    if (delegate != null) {
      PDelegateLogsFlutterApi.setUp(_LogsDelegateRelay(delegate));
    } else {
      PDelegateLogsFlutterApi.setUp(null);
    }
  }

  @override
  Future<String?> pushTokenForPushTypeVoIP() {
    return _pushRegistryApi.pushTokenForPushTypeVoIP();
  }

  @override
  Future<bool> isSetUp() {
    return _api.isSetUp();
  }

  @override
  Future<void> setUp(
    CallkeepOptions options,
  ) {
    return _api.setUp(options.toPigeon());
  }

  @override
  Future<void> tearDown() {
    return _api.tearDown();
  }

  @override
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    return _api
        .reportNewIncomingCall(callId, handle.toPigeon(), displayName, hasVideo)
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<void> reportConnectingOutgoingCall(
    String callId,
  ) {
    return _api.reportConnectingOutgoingCall(callId);
  }

  @override
  Future<void> reportConnectedOutgoingCall(
    String callId,
  ) {
    return _api.reportConnectedOutgoingCall(callId);
  }

  @override
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  ) {
    return _api.reportUpdateCall(callId, handle?.toPigeon(), displayName, hasVideo, proximityEnabled);
  }

  @override
  Future<void> reportEndCall(
    String callId,
    String displayName,
    CallkeepEndCallReason reason,
  ) {
    return _api.reportEndCall(callId, displayName, PEndCallReason(value: reason.toPigeon()));
  }

  @override
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  ) {
    return _api
        .startCall(callId, handle.toPigeon(), displayNameOrContactIdentifier, video, proximityEnabled)
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> answerCall(
    String callId,
  ) {
    return _api.answerCall(callId).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> endCall(
    String callId,
  ) {
    return _api.endCall(callId).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setHeld(
    String callId,
    bool onHold,
  ) {
    return _api.setHeld(callId, onHold).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setMuted(
    String callId,
    bool muted,
  ) {
    return _api.setMuted(callId, muted).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setSpeaker(
    String callId,
    bool enabled,
  ) {
    return _api.setSpeaker(callId, enabled).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> sendDTMF(
    String callId,
    String key,
  ) {
    return _api.sendDTMF(callId, key).then((value) => value?.value.toCallkeep());
  }

  @override
  void setBackgroundServiceDelegate(
    CallkeepBackgroundServiceDelegate? delegate,
  ) {
    if (delegate != null) {
      PDelegateBackgroundServiceFlutterApi.setUp(_CallkeepBackgroundServiceDelegateRelay(delegate));
    } else {
      PDelegateBackgroundServiceFlutterApi.setUp(null);
    }
  }

  @override
  Future<CallkeepSpecialPermissionStatus> getFullScreenIntentPermissionStatus() {
    return _permissionsApi.getFullScreenIntentPermissionStatus().then((value) => value.toCallkeep());
  }

  @override
  Future<bool> openFullScreenIntentSettings() {
    return _permissionsApi.openFullScreenIntentSettings();
  }

  @override
  Future<CallkeepAndroidBatteryMode> getBatteryMode() {
    return _permissionsApi.getBatteryMode().then((value) => value.toCallkeep());
  }

  @override
  Future<void> playRingbackSound() {
    return _soundApi.playRingbackSound();
  }

  @override
  Future<void> stopRingbackSound() {
    return _soundApi.stopRingbackSound();
  }

  @override
  Future<CallkeepConnection?> getConnection(String callId) async {
    return _connectionsApi.getConnection(callId).then((value) => value?.toCallkeep());
  }

  @override
  Future<List<CallkeepConnection>> getConnections() async {
    return _connectionsApi.getConnections().then((value) => value.map((it) => it.toCallkeep()).toList());
  }

  @override
  Future<void> updateActivitySignalingStatus(CallkeepSignalingStatus status) {
    return _connectionsApi.updateActivitySignalingStatus(status.toPigeon());
  }

// Android background signaling service
// ------------------------------------------------------------------------------------------------

  @override
  Future<void> initializeBackgroundSignalingServiceCallback(
      {ForegroundStartServiceHandle? onStart, ForegroundChangeLifecycleHandle? onChangedLifecycle}) async {
    // Initialization callback handle for the isolate plugin only once;
    _signalingIsolatePluginCallbackHandle = _signalingIsolatePluginCallbackHandle ??
        PluginUtilities.getCallbackHandle(
          _isolatePluginCallbackDispatcher,
        )?.toRawHandle();

    _onSignalingServiceStartHandle = _onSignalingServiceStartHandle ??
        PluginUtilities.getCallbackHandle(
          onStart!,
        )?.toRawHandle();

    _onSignalingServiceChangedLifecycleHandle = _onSignalingServiceChangedLifecycleHandle ??
        PluginUtilities.getCallbackHandle(
          onChangedLifecycle!,
        )?.toRawHandle();

    if (_signalingIsolatePluginCallbackHandle != null &&
        _onSignalingServiceStartHandle != null &&
        _onSignalingServiceChangedLifecycleHandle != null) {
      await _backgroundSignalingIsolateBootstrapApi.initializeSignalingServiceCallback(
        callbackDispatcher: _signalingIsolatePluginCallbackHandle!,
        onStartHandler: _onSignalingServiceStartHandle!,
        onChangedLifecycleHandler: _onSignalingServiceChangedLifecycleHandle!,
      );
    }
  }

  @override
  Future<void> configureBackgroundSignalingService({
    String? androidNotificationName,
    String? androidNotificationDescription,
  }) async {
    await _backgroundSignalingIsolateBootstrapApi.configureSignalingService(
      androidNotificationName: androidNotificationName,
      androidNotificationDescription: androidNotificationDescription,
    );
  }

  @override
  Future<dynamic> startBackgroundSignalingService() {
    return _backgroundSignalingIsolateBootstrapApi.startService();
  }

  @override
  Future<dynamic> stopBackgroundSignalingService() {
    return _backgroundSignalingIsolateBootstrapApi.stopService();
  }

  @override
  Future<dynamic> incomingCallBackgroundSignalingService(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    return _backgroundSignalingIsolateApi.incomingCall(
      callId,
      handle.toPigeon(),
      displayName,
      hasVideo,
    );
  }

  @override
  Future<dynamic> endCallsBackgroundSignalingService() {
    return _backgroundSignalingIsolateApi.endAllCalls();
  }

  @override
  Future<dynamic> endCallBackgroundSignalingService(
    String callId,
  ) {
    return _backgroundSignalingIsolateApi.endCall(callId);
  }

// ------------------------------------------------------------------------------------------------
// Android background signaling service

// Android background push notification service
// ------------------------------------------------------------------------------------------------
  @override
  Future<void> initializePushNotificationCallback(CallKeepPushNotificationSyncStatusHandle onNotificationSync) async {
    // Initialization callback handle for the isolate plugin only once;
    _pushNotificationIsolatePluginCallbackHandle = _pushNotificationIsolatePluginCallbackHandle ??
        PluginUtilities.getCallbackHandle(
          _isolatePluginCallbackDispatcher,
        )?.toRawHandle();

    _onPushNotificationNotificationSync = _onPushNotificationNotificationSync ??
        PluginUtilities.getCallbackHandle(
          onNotificationSync,
        )?.toRawHandle();

    if (_pushNotificationIsolatePluginCallbackHandle != null && _onPushNotificationNotificationSync != null) {
      await _backgroundPushNotificationIsolateBootstrapApi.initializePushNotificationCallback(
        callbackDispatcher: _pushNotificationIsolatePluginCallbackHandle!,
        onNotificationSync: _onPushNotificationNotificationSync!,
      );
    }
  }

  @override
  Future<void> configurePushNotificationSignalingService({
    bool launchBackgroundIsolateEvenIfAppIsOpen = false,
  }) async {
    await _backgroundPushNotificationIsolateBootstrapApi.configureSignalingService(
      launchBackgroundIsolateEvenIfAppIsOpen: launchBackgroundIsolateEvenIfAppIsOpen,
    );
  }

  @override
  Future<CallkeepIncomingCallError?> incomingCallPushNotificationService(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) {
    return _backgroundPushNotificationIsolateBootstrapApi
        .reportNewIncomingCall(callId, handle.toPigeon(), displayName, hasVideo)
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<dynamic> endCallsBackgroundPushNotificationService() {
    return _backgroundPushNotificationIsolateApi.endAllCalls();
  }

  @override
  Future<dynamic> endCallBackgroundPushNotificationService(String callId) {
    return _backgroundPushNotificationIsolateApi.endCall(callId);
  }

// ------------------------------------------------------------------------------------------------
// Android background push notification service
}

class _CallkeepDelegateRelay implements PDelegateFlutterApi {
  const _CallkeepDelegateRelay(this._delegate);

  final CallkeepDelegate _delegate;

  @override
  void continueStartCallIntent(
    PHandle handle,
    String? displayName,
    bool video,
  ) {
    _delegate.continueStartCallIntent(handle.toCallkeep(), displayName, video);
  }

  @override
  void didPushIncomingCall(
    PHandle handle,
    String? displayName,
    bool video,
    String callId,
    PIncomingCallError? error,
  ) {
    _delegate.didPushIncomingCall(handle.toCallkeep(), displayName, video, callId, error?.value.toCallkeep());
  }

  @override
  Future<bool> performStartCall(
    String callId,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) {
    return _delegate.performStartCall(callId, handle.toCallkeep(), displayNameOrContactIdentifier, video);
  }

  @override
  Future<bool> performAnswerCall(
    String callId,
  ) {
    return _delegate.performAnswerCall(callId);
  }

  @override
  Future<bool> performEndCall(
    String callId,
  ) {
    return _delegate.performEndCall(callId);
  }

  @override
  Future<bool> performSetHeld(
    String callId,
    bool onHold,
  ) {
    return _delegate.performSetHeld(callId, onHold);
  }

  @override
  Future<bool> performSetMuted(
    String callId,
    bool muted,
  ) {
    return _delegate.performSetMuted(callId, muted);
  }

  @override
  Future<bool> performSendDTMF(
    String callId,
    String key,
  ) {
    return _delegate.performSendDTMF(callId, key);
  }

  @override
  void didActivateAudioSession() {
    _delegate.didActivateAudioSession();
  }

  @override
  void didDeactivateAudioSession() {
    _delegate.didDeactivateAudioSession();
  }

  @override
  void didReset() {
    _delegate.didReset();
  }

  @override
  Future<bool> performSetSpeaker(
    String callId,
    bool enabled,
  ) {
    return _delegate.performSetSpeaker(callId, enabled);
  }
}

class _PushRegistryDelegateRelay implements PPushRegistryDelegateFlutterApi {
  const _PushRegistryDelegateRelay(this._delegate);

  final PushRegistryDelegate _delegate;

  @override
  void didUpdatePushTokenForPushTypeVoIP(
    String? token,
  ) {
    _delegate.didUpdatePushTokenForPushTypeVoIP(token);
  }
}

class _LogsDelegateRelay implements PDelegateLogsFlutterApi {
  const _LogsDelegateRelay(this._delegate);

  final CallkeepLogsDelegate _delegate;

  @override
  void onLog(PLogTypeEnum type, String tag, String message) {
    _delegate.onLog(type.toCallkeep(), tag, message);
  }
}

class _CallkeepBackgroundServiceDelegateRelay implements PDelegateBackgroundServiceFlutterApi {
  const _CallkeepBackgroundServiceDelegateRelay(this._delegate);

  final CallkeepBackgroundServiceDelegate _delegate;

  @override
  Future<void> performEndCall(
    String callId,
  ) async {
    return _delegate.performServiceEndCall(callId);
  }

  @override
  Future<void> endCallReceived(
    String callId,
    String number,
    bool video,
    int createdTime,
    int? acceptedTime,
    int? hungUpTime,
  ) async {
    return _delegate.endCallReceived(
      callId,
      number,
      DateTime.fromMillisecondsSinceEpoch(createdTime),
      acceptedTime != null ? DateTime.fromMillisecondsSinceEpoch(acceptedTime) : null,
      hungUpTime != null ? DateTime.fromMillisecondsSinceEpoch(hungUpTime) : null,
      video: video,
    );
  }

  @override
  Future<void> performAnswerCall(String callId) async {
    return _delegate.performServiceAnswerCall(callId);
  }
}

@pragma('vm:entry-point')
void _isolatePluginCallbackDispatcher() {
  // Initialize the Flutter framework necessary for method channels and Pigeon.
  WidgetsFlutterBinding.ensureInitialized();

  // WebtritCallkeepAndroid().wakeUpBackgroundHandler();
  // Set up the Pigeon API for the background service.
  PDelegateBackgroundRegisterFlutterApi.setUp(_BackgroundServiceDelegate());
}

class _BackgroundServiceDelegate implements PDelegateBackgroundRegisterFlutterApi {
  @override
  Future<void> onWakeUpBackgroundHandler(
    int userCallbackHandle,
    PCallkeepServiceStatus status,
  ) async {
    final handle = CallbackHandle.fromRawHandle(userCallbackHandle);
    final closure = PluginUtilities.getCallbackFromHandle(handle)! as ForegroundStartServiceHandle;

    await closure(status.toCallkeep());
  }

  @override
  Future<void> onApplicationStatusChanged(
    int applicationStatusCallbackHandle,
    PCallkeepServiceStatus status,
  ) async {
    final handle = CallbackHandle.fromRawHandle(applicationStatusCallbackHandle);
    final closure = PluginUtilities.getCallbackFromHandle(handle)! as ForegroundChangeLifecycleHandle;

    await closure(status.toCallkeep());
  }

  @override
  Future<void> onNotificationSync(
    int pushNotificationSyncStatusHandle,
    PCallkeepPushNotificationSyncStatus status,
  ) async {
    final handle = CallbackHandle.fromRawHandle(pushNotificationSyncStatusHandle);
    final closure = PluginUtilities.getCallbackFromHandle(handle)! as CallKeepPushNotificationSyncStatusHandle;
    await closure(status.toCallkeep());
  }
}
