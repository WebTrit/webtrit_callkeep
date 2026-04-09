import 'dart:async';
import 'dart:ui';

import 'package:flutter/services.dart' show BackgroundIsolateBinaryMessenger, BinaryMessenger;
import 'package:flutter/widgets.dart' show WidgetsFlutterBinding;

import 'package:webtrit_callkeep_android/src/common/common.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The Android implementation of [WebtritCallkeepPlatform].
class WebtritCallkeepAndroid extends WebtritCallkeepPlatform {
  /// Registers this class as the default instance of [WebtritCallkeepPlatform].
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeepAndroid();
  }

  // APIs for initializing the background service isolates to be used in the main isolate.
  final _backgroundPushNotificationIsolateBootstrapApi = PHostBackgroundPushNotificationIsolateBootstrapApi();

  // APIs for working with the background service isolates.
  final _backgroundPushNotificationIsolateApi = PHostBackgroundPushNotificationIsolateApi();
  final _pHostSmsReceptionApi = PHostSmsReceptionConfigApi();

  final _pushRegistryApi = PPushRegistryHostApi();
  final _api = PHostApi();

  final _soundApi = PHostSoundApi();
  final _connectionsApi = PHostConnectionsApi();
  final _activityControlApi = PHostActivityControlApi();

  int? _pushNotificationIsolatePluginCallbackHandle;

  int? _onPushNotificationNotificationSync;

  final _permissionsApi = PHostPermissionsApi();

  final _diagnosticsApi = PHostDiagnosticsApi();

  @override
  void setDelegate(CallkeepDelegate? delegate) {
    if (delegate != null) {
      PDelegateFlutterApi.setUp(_CallkeepDelegateRelay(delegate));
    } else {
      PDelegateFlutterApi.setUp(null);
    }

    // Notify the Pigeon API that the delegate has been set.
    _api.onDelegateSet();
  }

  @override
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {
    if (delegate != null) {
      PPushRegistryDelegateFlutterApi.setUp(_PushRegistryDelegateRelay(delegate));
    } else {
      PPushRegistryDelegateFlutterApi.setUp(null);
    }
  }

  @override
  void setLogsDelegate(CallkeepLogsDelegate? delegate) {
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
  Future<void> setUp(CallkeepOptions options) {
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
  Future<void> reportConnectingOutgoingCall(String callId) {
    return _api.reportConnectingOutgoingCall(callId);
  }

  @override
  Future<void> reportConnectedOutgoingCall(String callId) {
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
  Future<void> reportEndCall(String callId, String displayName, CallkeepEndCallReason reason) {
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
  Future<CallkeepCallRequestError?> answerCall(String callId) {
    return _api.answerCall(callId).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> endCall(String callId) {
    return _api.endCall(callId).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) {
    return _api.setHeld(callId, onHold).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) {
    return _api.setMuted(callId, muted).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) {
    return _api.setSpeaker(callId, enabled).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setAudioDevice(String callId, CallkeepAudioDevice device) {
    return _api
        .setAudioDevice(
          callId,
          PAudioDevice(type: PAudioDeviceType.values.byName(device.type.name), id: device.id, name: device.name),
        )
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) {
    return _api.sendDTMF(callId, key).then((value) => value?.value.toCallkeep());
  }

  @override
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {
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
  Future<void> openFullScreenIntentSettings() {
    return _permissionsApi.openFullScreenIntentSettings();
  }

  @override
  Future<void> openSettings() {
    return _permissionsApi.openSettings();
  }

  @override
  Future<CallkeepAndroidBatteryMode> getBatteryMode() {
    return _permissionsApi.getBatteryMode().then((value) => value.toCallkeep());
  }

  @override
  Future<Map<String, dynamic>> getDiagnosticReport() async {
    final rawData = await _diagnosticsApi.getDiagnosticReport();
    return rawData.cast<String, dynamic>();
  }

  @override
  Future<Map<CallkeepPermission, CallkeepSpecialPermissionStatus>> requestPermissions(
    List<CallkeepPermission> permissions,
  ) async {
    final pigeonList = permissions.map((e) => e.toPigeon()).toList();
    final results = await _permissionsApi.requestPermissions(pigeonList);
    return {for (final result in results) result.permission.toCallkeep(): result.status.toCallkeep()};
  }

  @override
  Future<Map<CallkeepPermission, CallkeepSpecialPermissionStatus>> checkPermissionsStatus(
    List<CallkeepPermission> permissions,
  ) async {
    final pigeonList = permissions.map((e) => e.toPigeon()).toList();
    final results = await _permissionsApi.checkPermissionsStatus(pigeonList);
    return {for (final result in results) result.permission.toCallkeep(): result.status.toCallkeep()};
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
  Future<void> cleanConnections() {
    return _connectionsApi.cleanConnections();
  }

  @override
  Future<List<CallkeepConnection>> getConnections() async {
    return _connectionsApi.getConnections().then((value) => value.map((it) => it.toCallkeep()).toList());
  }

  // Android background push notification service
  // ------------------------------------------------------------------------------------------------
  @override
  Future<void> initializePushNotificationCallback(CallKeepPushNotificationSyncStatusHandle onSync) async {
    // Initialization callback handle for the isolate plugin only once;
    _pushNotificationIsolatePluginCallbackHandle =
        _pushNotificationIsolatePluginCallbackHandle ??
        PluginUtilities.getCallbackHandle(_isolatePluginCallbackDispatcher)?.toRawHandle();

    _onPushNotificationNotificationSync =
        _onPushNotificationNotificationSync ?? PluginUtilities.getCallbackHandle(onSync)?.toRawHandle();

    if (_pushNotificationIsolatePluginCallbackHandle != null && _onPushNotificationNotificationSync != null) {
      await _backgroundPushNotificationIsolateBootstrapApi.initializePushNotificationCallback(
        callbackDispatcher: _pushNotificationIsolatePluginCallbackHandle!,
        onNotificationSync: _onPushNotificationNotificationSync!,
      );
    }
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

  @override
  Future<dynamic> releaseCallBackgroundPushNotificationService(String callId) {
    return _backgroundPushNotificationIsolateApi.releaseCall(callId);
  }

  // ------------------------------------------------------------------------------------------------
  // Android background push notification service

  // ------------------------------------------------------------------------------------------------
  // Android SMS reception section
  // ------------------------------------------------------------------------------------------------

  /// Initializes SMS reception with the given prefix used to filter messages.
  ///
  /// Only messages that begin with [messagePrefix] will be delivered to the Dart handler.
  /// Initializes SMS reception with the given prefix and regex pattern used to filter and parse messages.
  ///
  /// Only messages that begin with [messagePrefix] and match [regexPattern] will be delivered and parsed.
  @override
  Future<void> initializeSmsReception({
    /// Prefix to match incoming SMS messages (e.g. "<#> WEBTRIT:")
    required String messagePrefix,

    /// Regex pattern with 4 capture groups: callId, handle, displayName, hasVideo
    required String regexPattern,
  }) {
    return _pHostSmsReceptionApi.initializeSmsReception(messagePrefix: messagePrefix, regexPattern: regexPattern);
  }

  // ------------------------------------------------------------------------------------------------
  // Android Activity Control section
  // ------------------------------------------------------------------------------------------------

  /// Allows the app's activity to be shown over the device lock screen.
  ///
  /// This is an Android-only feature.
  @override
  Future<void> showOverLockscreen([bool enable = true]) {
    return _activityControlApi.showOverLockscreen(enable);
  }

  /// Turns the screen on when the app's window is shown.
  ///
  /// Typically used in conjunction with [showOverLockscreen].
  /// This is an Android-only feature.
  @override
  Future<void> wakeScreenOnShow([bool enable = true]) {
    return _activityControlApi.wakeScreenOnShow(enable);
  }

  /// Moves the entire task (app) to the background.
  ///
  /// This is an Android-only feature.
  /// Returns `true` if successful.
  @override
  Future<bool> sendToBackground() {
    return _activityControlApi.sendToBackground();
  }

  /// Checks if the device screen is currently locked (keyguard is active).
  ///
  /// Returns `false` on non-Android platforms.
  @override
  Future<bool> isDeviceLocked() {
    return _activityControlApi.isDeviceLocked();
  }
}

class _CallkeepDelegateRelay implements PDelegateFlutterApi {
  const _CallkeepDelegateRelay(this._delegate);

  final CallkeepDelegate _delegate;

  @override
  void continueStartCallIntent(PHandle handle, String? displayName, bool video) {
    _delegate.continueStartCallIntent(handle.toCallkeep(), displayName, video);
  }

  @override
  void didPushIncomingCall(PHandle handle, String? displayName, bool video, String callId, PIncomingCallError? error) {
    _delegate.didPushIncomingCall(handle.toCallkeep(), displayName, video, callId, error?.value.toCallkeep());
  }

  @override
  Future<bool> performStartCall(String callId, PHandle handle, String? displayNameOrContactIdentifier, bool video) {
    return _delegate.performStartCall(callId, handle.toCallkeep(), displayNameOrContactIdentifier, video);
  }

  @override
  Future<bool> performAnswerCall(String callId) {
    return _delegate.performAnswerCall(callId);
  }

  @override
  Future<bool> performEndCall(String callId) {
    return _delegate.performEndCall(callId);
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) {
    return _delegate.performSetHeld(callId, onHold);
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) {
    return _delegate.performSetMuted(callId, muted);
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) {
    return _delegate.performSendDTMF(callId, key);
  }

  @override
  Future<bool> performAudioDeviceSet(String callId, PAudioDevice device) {
    return _delegate.performAudioDeviceSet(
      callId,
      CallkeepAudioDevice(
        type: CallkeepAudioDeviceType.values.byName(device.type.name),
        id: device.id,
        name: device.name,
      ),
    );
  }

  @override
  Future<bool> performAudioDevicesUpdate(String callId, List<PAudioDevice> devices) {
    return _delegate.performAudioDevicesUpdate(
      callId,
      devices.map((device) {
        return CallkeepAudioDevice(
          type: CallkeepAudioDeviceType.values.byName(device.type.name),
          id: device.id,
          name: device.name,
        );
      }).toList(),
    );
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
}

class _PushRegistryDelegateRelay implements PPushRegistryDelegateFlutterApi {
  const _PushRegistryDelegateRelay(this._delegate);

  final PushRegistryDelegate _delegate;

  @override
  void didUpdatePushTokenForPushTypeVoIP(String? token) {
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
  Future<void> performEndCall(String callId) async {
    return _delegate.performEndCall(callId);
  }

  @override
  Future<void> performAnswerCall(String callId) async {
    return _delegate.performAnswerCall(callId);
  }
}

@pragma('vm:entry-point')
void _isolatePluginCallbackDispatcher() {
  final messenger = _ensureBinaryMessengerInitialized();
  PDelegateBackgroundRegisterFlutterApi.setUp(_BackgroundServiceDelegate(), binaryMessenger: messenger);
}

/// Initialises a [BinaryMessenger] for platform channels in this background
/// isolate without touching the full Flutter rendering stack.
///
/// Returns [BackgroundIsolateBinaryMessenger.instance] when
/// [RootIsolateToken.instance] is available (background Flutter engine root
/// isolate), avoiding RendererBinding / Impeller / Vulkan initialisation.
/// Falls back to [WidgetsFlutterBinding] and returns `null` (Pigeon uses the
/// binding's default messenger) when the token is unexpectedly absent.
BinaryMessenger? _ensureBinaryMessengerInitialized() {
  final token = RootIsolateToken.instance;
  if (token != null) {
    BackgroundIsolateBinaryMessenger.ensureInitialized(token);
    return BackgroundIsolateBinaryMessenger.instance;
  } else {
    WidgetsFlutterBinding.ensureInitialized();
    return null;
  }
}

class _BackgroundServiceDelegate implements PDelegateBackgroundRegisterFlutterApi {
  @override
  Future<void> onWakeUpBackgroundHandler(
    int userCallbackHandle,
    PCallkeepServiceStatus status,
    PCallkeepIncomingCallData? callData,
  ) async {
    final handle = CallbackHandle.fromRawHandle(userCallbackHandle);
    final closure = PluginUtilities.getCallbackFromHandle(handle)! as ForegroundStartServiceHandle;

    await closure(status.toCallkeep(), callData?.toCallkeep());
  }

  @override
  Future<void> onApplicationStatusChanged(int applicationStatusCallbackHandle, PCallkeepServiceStatus status) async {
    final handle = CallbackHandle.fromRawHandle(applicationStatusCallbackHandle);
    final closure = PluginUtilities.getCallbackFromHandle(handle)! as ForegroundChangeLifecycleHandle;

    await closure(status.toCallkeep());
  }

  @override
  Future<void> onNotificationSync(int pushNotificationSyncStatusHandle, PCallkeepIncomingCallData? callData) async {
    final handle = CallbackHandle.fromRawHandle(pushNotificationSyncStatusHandle);
    final closure = PluginUtilities.getCallbackFromHandle(handle)! as CallKeepPushNotificationSyncStatusHandle;
    await closure(callData?.toCallkeep());
  }
}
