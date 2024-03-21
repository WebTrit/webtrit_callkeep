import 'dart:async';

import 'package:uuid/uuid.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

import 'common/callkeep.pigeon.dart';
import 'common/converters.dart';

class WebtritCallkeep extends WebtritCallkeepPlatform {
  /// Registers this class as the default instance of [WebtritCallkeepPlatform].
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeep();
  }

  final _pushRegistryApi = PPushRegistryHostApi();
  final _api = PHostApi();

  final Map<String, String> _uuidIdMapping = {};

  String _toUUID(String callId) => const Uuid().v5obj(Uuid.NAMESPACE_OID, callId).uuid;

  @override
  void setDelegate(
    CallkeepDelegate? delegate,
  ) {
    if (delegate != null) {
      PDelegateFlutterApi.setup(_CallkeepDelegateRelay(delegate, _uuidIdMapping));
    } else {
      PDelegateFlutterApi.setup(null);
    }
  }

  @override
  void setPushRegistryDelegate(
    PushRegistryDelegate? delegate,
  ) {
    if (delegate != null) {
      PPushRegistryDelegateFlutterApi.setup(_PushRegistryDelegateRelay(delegate));
    } else {
      PPushRegistryDelegateFlutterApi.setup(null);
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
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api
        .reportNewIncomingCall(
          uuid,
          handle.toPigeon(),
          displayName,
          hasVideo,
        )
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<void> reportConnectingOutgoingCall(
    String callId,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.reportConnectingOutgoingCall(uuid);
  }

  @override
  Future<void> reportConnectedOutgoingCall(
    String callId,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.reportConnectedOutgoingCall(uuid);
  }

  @override
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.reportUpdateCall(
      uuid,
      handle?.toPigeon(),
      displayName,
      hasVideo,
    );
  }

  @override
  Future<void> reportEndCall(
    String callId,
    CallkeepEndCallReason reason,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.reportEndCall(uuid, PEndCallReason(value: reason.toPigeon()));
  }

  @override
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api
        .startCall(
          uuid,
          handle.toPigeon(),
          displayNameOrContactIdentifier,
          video,
        )
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> answerCall(
    String callId,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.answerCall(uuid).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> endCall(
    String callId,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.endCall(uuid).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setHeld(
    String callId,
    bool onHold,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.setHeld(uuid, onHold).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setMuted(
    String callId,
    bool muted,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.setMuted(uuid, muted).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setSpeaker(
    String callId,
    bool enabled,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.setSpeaker(uuid, enabled).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> sendDTMF(
    String callId,
    String key,
  ) async {
    final uuid = _toUUID(callId);
    _uuidIdMapping[uuid] = callId;

    return _api.sendDTMF(uuid, key).then((value) => value?.value.toCallkeep());
  }
}

class _CallkeepDelegateRelay implements PDelegateFlutterApi {
  const _CallkeepDelegateRelay(this._delegate, this._uuidIdMapping);

  final CallkeepDelegate _delegate;
  final Map<String, String> _uuidIdMapping;

  String _getCallId(String uuid) => _uuidIdMapping[uuid.toLowerCase()]!;

  @override
  void continueStartCallIntent(
    PHandle handle,
    String? displayName,
    bool video,
  ) {
    _delegate.continueStartCallIntent(
      handle.toCallkeep(),
      displayName,
      video,
    );
  }

  @override
  void didPushIncomingCall(
    PHandle handle,
    String? displayName,
    bool video,
    String callId,
    String uuidString,
    PIncomingCallError? error,
  ) {
    _uuidIdMapping[uuidString] = callId;

    _delegate.didPushIncomingCall(
      handle.toCallkeep(),
      displayName,
      video,
      callId,
      error?.value.toCallkeep(),
    );
  }

  @override
  Future<bool> performStartCall(
    String uuid,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) async {
    return _delegate.performStartCall(
      _getCallId(uuid),
      handle.toCallkeep(),
      displayNameOrContactIdentifier,
      video,
    );
  }

  @override
  Future<bool> performAnswerCall(
    String uuid,
  ) async {
    return _delegate.performAnswerCall(
      _getCallId(uuid),
    );
  }

  @override
  Future<bool> performEndCall(
    String uuid,
  ) async {
    return _delegate.performEndCall(
      _getCallId(uuid),
    );
  }

  @override
  Future<bool> performSetHeld(
    String uuid,
    bool onHold,
  ) async {
    return _delegate.performSetHeld(
      _getCallId(uuid),
      onHold,
    );
  }

  @override
  Future<bool> performSetMuted(
    String uuid,
    bool muted,
  ) async {
    return _delegate.performSetMuted(
      _getCallId(uuid),
      muted,
    );
  }

  @override
  Future<bool> performSendDTMF(
    String uuid,
    String key,
  ) async {
    return _delegate.performSendDTMF(
      _getCallId(uuid),
      key,
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

  @override
  Future<bool> performSetSpeaker(
    String uuid,
    bool enabled,
  ) async {
    final id = _uuidIdMapping[uuid.toLowerCase()]!;

    return _delegate.performSetSpeaker(id, enabled);
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
