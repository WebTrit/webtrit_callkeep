import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The Web implementation of [WebtritCallkeepPlatform].
///
/// Manages call state in memory and forwards events to the registered
/// [CallkeepDelegate]. Platform-specific features (Android background
/// services, SMS, lock-screen control, iOS PushKit) are no-ops.
class WebtritCallkeepWeb extends WebtritCallkeepPlatform {
  /// Registers this class as the default instance of [WebtritCallkeepPlatform].
  static void registerWith([Object? registrar]) {
    WebtritCallkeepPlatform.instance = WebtritCallkeepWeb();
  }

  CallkeepDelegate? _delegate;
  bool _isSetUp = false;

  /// Active connections keyed by callId.
  final Map<String, CallkeepConnection> _connections = {};

  /// Call IDs that have been terminated since the last [setUp].
  ///
  /// Populated by [endCall], [reportEndCall], and [tearDown].
  /// Cleared on [tearDown] (after notifying the delegate) and on [cleanConnections],
  /// so it only guards within a single setUp/tearDown session.
  /// Used to return [CallkeepIncomingCallError.callIdAlreadyTerminated]
  /// on a re-report of the same ID within the same session.
  final Set<String> _terminatedCallIds = {};

  // ---------------------------------------------------------------------------
  // Platform identity
  // ---------------------------------------------------------------------------

  @override
  Future<String?> getPlatformName() async => 'Web';

  // ---------------------------------------------------------------------------
  // Delegates
  // ---------------------------------------------------------------------------

  @override
  void setDelegate(CallkeepDelegate? delegate) {
    _delegate = delegate;
  }

  @override
  void setBackgroundServiceDelegate(CallkeepBackgroundServiceDelegate? delegate) {}

  @override
  void setLogsDelegate(CallkeepLogsDelegate? delegate) {}

  @override
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {}

  // ---------------------------------------------------------------------------
  // Push token
  // ---------------------------------------------------------------------------

  @override
  Future<String?> pushTokenForPushTypeVoIP() async => null;

  // ---------------------------------------------------------------------------
  // Setup / teardown
  // ---------------------------------------------------------------------------

  @override
  Future<bool> isSetUp() async => _isSetUp;

  @override
  Future<void> setUp(CallkeepOptions options) async {
    _isSetUp = true;
  }

  @override
  Future<void> tearDown() async {
    _isSetUp = false;
    final activeIds = _connections.keys.toList();
    for (final callId in activeIds) {
      _terminatedCallIds.add(callId);
      await _delegate?.performEndCall(callId);
      _delegate?.didDeactivateAudioSession();
    }
    _connections.clear();
    _terminatedCallIds.clear();
    _delegate?.didReset();
  }

  // ---------------------------------------------------------------------------
  // Incoming call reporting
  // ---------------------------------------------------------------------------

  @override
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) async {
    if (_connections.containsKey(callId)) {
      final state = _connections[callId]!.state;
      if (state == CallkeepConnectionState.stateActive) {
        return CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered;
      }
      return CallkeepIncomingCallError.callIdAlreadyExists;
    }
    if (_terminatedCallIds.contains(callId)) {
      return CallkeepIncomingCallError.callIdAlreadyTerminated;
    }
    _connections[callId] = CallkeepConnection(
      callId: callId,
      state: CallkeepConnectionState.stateRinging,
      disconnectCause: null,
    );
    _delegate?.didPushIncomingCall(handle, displayName, hasVideo, callId, null);
    return null;
  }

  // ---------------------------------------------------------------------------
  // Outgoing call reporting
  // ---------------------------------------------------------------------------

  @override
  Future<void> reportConnectingOutgoingCall(String callId) async {
    _updateConnectionState(callId, CallkeepConnectionState.stateDialing);
  }

  @override
  Future<void> reportConnectedOutgoingCall(String callId) async {
    _updateConnectionState(callId, CallkeepConnectionState.stateActive);
    _delegate?.didActivateAudioSession();
  }

  @override
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  ) async {}

  @override
  Future<void> reportEndCall(String callId, String displayName, CallkeepEndCallReason reason) async {
    _connections.remove(callId);
    _terminatedCallIds.add(callId);
    _delegate?.didDeactivateAudioSession();
  }

  // ---------------------------------------------------------------------------
  // Call actions
  // ---------------------------------------------------------------------------

  @override
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  ) async {
    if (_connections.containsKey(callId)) {
      return CallkeepCallRequestError.callUuidAlreadyExists;
    }
    _connections[callId] = CallkeepConnection(
      callId: callId,
      state: CallkeepConnectionState.stateDialing,
      disconnectCause: null,
    );
    await _delegate?.performStartCall(callId, handle, displayNameOrContactIdentifier, video);
    return null;
  }

  @override
  Future<CallkeepCallRequestError?> answerCall(String callId) async {
    if (!_connections.containsKey(callId)) {
      return CallkeepCallRequestError.unknownCallUuid;
    }
    _updateConnectionState(callId, CallkeepConnectionState.stateActive);
    await _delegate?.performAnswerCall(callId);
    _delegate?.didActivateAudioSession();
    return null;
  }

  @override
  Future<CallkeepCallRequestError?> endCall(String callId) async {
    if (!_connections.containsKey(callId)) {
      return CallkeepCallRequestError.unknownCallUuid;
    }
    _terminatedCallIds.add(callId);
    await _delegate?.performEndCall(callId);
    _connections.remove(callId);
    _delegate?.didDeactivateAudioSession();
    return null;
  }

  @override
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) async {
    if (!_connections.containsKey(callId)) {
      return CallkeepCallRequestError.unknownCallUuid;
    }
    _updateConnectionState(callId, onHold ? CallkeepConnectionState.stateHolding : CallkeepConnectionState.stateActive);
    await _delegate?.performSetHeld(callId, onHold);
    return null;
  }

  @override
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) async {
    if (!_connections.containsKey(callId)) {
      return CallkeepCallRequestError.unknownCallUuid;
    }
    await _delegate?.performSetMuted(callId, muted);
    return null;
  }

  @override
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) async {
    if (!_connections.containsKey(callId)) {
      return CallkeepCallRequestError.unknownCallUuid;
    }
    await _delegate?.performSendDTMF(callId, key);
    return null;
  }

  @override
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) async => null;

  @override
  Future<CallkeepCallRequestError?> setAudioDevice(String callId, CallkeepAudioDevice device) async {
    await _delegate?.performAudioDeviceSet(callId, device);
    return null;
  }

  // ---------------------------------------------------------------------------
  // Connections
  // ---------------------------------------------------------------------------

  @override
  Future<CallkeepConnection?> getConnection(String callId) async => _connections[callId];

  @override
  Future<List<CallkeepConnection>> getConnections() async => _connections.values.toList();

  @override
  Future<void> cleanConnections() async {
    _connections.clear();
    _terminatedCallIds.clear();
  }

  // ---------------------------------------------------------------------------
  // Signaling status
  // ---------------------------------------------------------------------------

  @override
  Future<void> updateActivitySignalingStatus(CallkeepSignalingStatus status) async {}

  // ---------------------------------------------------------------------------
  // Permissions (web grants all by default)
  // ---------------------------------------------------------------------------

  @override
  Future<CallkeepSpecialPermissionStatus> getFullScreenIntentPermissionStatus() async {
    return CallkeepSpecialPermissionStatus.granted;
  }

  @override
  Future<void> openFullScreenIntentSettings() async {}

  @override
  Future<void> openSettings() async {}

  @override
  Future<CallkeepAndroidBatteryMode> getBatteryMode() async => CallkeepAndroidBatteryMode.unknown;

  @override
  Future<Map<CallkeepPermission, CallkeepSpecialPermissionStatus>> requestPermissions(
    List<CallkeepPermission> permissions,
  ) async {
    return {for (final p in permissions) p: CallkeepSpecialPermissionStatus.granted};
  }

  @override
  Future<Map<CallkeepPermission, CallkeepSpecialPermissionStatus>> checkPermissionsStatus(
    List<CallkeepPermission> permissions,
  ) async {
    return {for (final p in permissions) p: CallkeepSpecialPermissionStatus.granted};
  }

  // ---------------------------------------------------------------------------
  // Diagnostics
  // ---------------------------------------------------------------------------

  @override
  Future<Map<String, dynamic>> getDiagnosticReport() async {
    return {'platform': 'web', 'isSetUp': _isSetUp, 'activeConnections': _connections.length};
  }

  // ---------------------------------------------------------------------------
  // Ringback
  // ---------------------------------------------------------------------------

  @override
  Future<void> playRingbackSound() async {}

  @override
  Future<void> stopRingbackSound() async {}

  // ---------------------------------------------------------------------------
  // Android background signaling service (no-op on web)
  // ---------------------------------------------------------------------------

  @override
  Future<void> initializeBackgroundSignalingServiceCallback(ForegroundStartServiceHandle onSync) async {}

  @override
  Future<void> configureBackgroundSignalingService({
    String? androidNotificationName,
    String? androidNotificationDescription,
  }) async {}

  @override
  void startBackgroundSignalingService() {}

  @override
  void stopBackgroundSignalingService() {}

  @override
  Future<dynamic> endCallsBackgroundSignalingService() async {}

  @override
  Future<dynamic> endCallBackgroundSignalingService(String callId) async {}

  @override
  Future<dynamic> incomingCallBackgroundSignalingService(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) async {}

  // ---------------------------------------------------------------------------
  // Android background push notification service (no-op on web)
  // ---------------------------------------------------------------------------

  @override
  Future<void> initializePushNotificationCallback(CallKeepPushNotificationSyncStatusHandle onSync) async {}

  @override
  Future<void> configurePushNotificationSignalingService({bool launchBackgroundIsolateEvenIfAppIsOpen = false}) async {}

  @override
  Future<CallkeepIncomingCallError?> incomingCallPushNotificationService(
    String callId,
    CallkeepHandle handle,
    String? displayName,
    bool hasVideo,
  ) async => null;

  @override
  Future<dynamic> endCallsBackgroundPushNotificationService() async {}

  @override
  Future<dynamic> endCallBackgroundPushNotificationService(String callId) async {}

  // ---------------------------------------------------------------------------
  // Android SMS reception (no-op on web)
  // ---------------------------------------------------------------------------

  @override
  Future<void> initializeSmsReception({required String messagePrefix, required String regexPattern}) async {}

  // ---------------------------------------------------------------------------
  // Android activity control (no-op on web)
  // ---------------------------------------------------------------------------

  @override
  Future<void> showOverLockscreen([bool enable = true]) async {}

  @override
  Future<void> wakeScreenOnShow([bool enable = true]) async {}

  @override
  Future<bool> sendToBackground() async => false;

  @override
  Future<bool> isDeviceLocked() async => false;

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  void _updateConnectionState(String callId, CallkeepConnectionState state) {
    final conn = _connections[callId];
    if (conn != null) {
      _connections[callId] = CallkeepConnection(callId: callId, state: state, disconnectCause: conn.disconnectCause);
    }
  }
}
