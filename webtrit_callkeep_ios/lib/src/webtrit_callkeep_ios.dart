import 'dart:async';

import 'package:uuid/uuid.dart';
import 'package:webtrit_callkeep_ios/src/common/callkeep.pigeon.dart';
import 'package:webtrit_callkeep_ios/src/common/converters.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// Ios implementation of [WebtritCallkeepPlatform].
class WebtritCallkeep extends WebtritCallkeepPlatform {
  /// Registers this class as the default instance of [WebtritCallkeepPlatform].
  static void registerWith() {
    WebtritCallkeepPlatform.instance = WebtritCallkeep();
  }

  final _pushRegistryApi = PPushRegistryHostApi();
  final _api = PHostApi();

  final _UuidIdMapping _uuidIdMapping = _UuidIdMapping();

  @override
  void setDelegate(CallkeepDelegate? delegate) {
    if (delegate != null) {
      PDelegateFlutterApi.setup(_CallkeepDelegateRelay(delegate, _uuidIdMapping));
    } else {
      PDelegateFlutterApi.setup(null);
    }
  }

  @override
  void setPushRegistryDelegate(PushRegistryDelegate? delegate) {
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
  ) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api
        .reportNewIncomingCall(uuid, handle.toPigeon(), displayName, hasVideo)
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<void> reportConnectingOutgoingCall(String callId) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.reportConnectingOutgoingCall(uuid);
  }

  @override
  Future<void> reportConnectedOutgoingCall(String callId) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.reportConnectedOutgoingCall(uuid);
  }

  @override
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  ) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.reportUpdateCall(
      uuid,
      handle?.toPigeon(),
      displayName,
      hasVideo,
      proximityEnabled,
    );
  }

  @override
  Future<void> reportEndCall(String callId, CallkeepEndCallReason reason) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.reportEndCall(uuid, PEndCallReason(value: reason.toPigeon()));
  }

  @override
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  ) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api
        .startCall(
          uuid,
          handle.toPigeon(),
          displayNameOrContactIdentifier,
          video,
          proximityEnabled,
        )
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> answerCall(String callId) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.answerCall(uuid).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> endCall(String callId) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.endCall(uuid).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.setHeld(uuid, onHold).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.setMuted(uuid, muted).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.setSpeaker(uuid, enabled).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) async {
    final uuid = _uuidIdMapping.convertToUUID(callId: callId);
    _uuidIdMapping.add(callId: callId, uuid: uuid);

    return _api.sendDTMF(uuid, key).then((value) => value?.value.toCallkeep());
  }
}

class _CallkeepDelegateRelay implements PDelegateFlutterApi {
  const _CallkeepDelegateRelay(this._delegate, this._uuidIdMapping);

  final CallkeepDelegate _delegate;

  final _UuidIdMapping _uuidIdMapping;

  @override
  void continueStartCallIntent(PHandle handle, String? displayName, bool video) {
    _delegate.continueStartCallIntent(handle.toCallkeep(), displayName, video);
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
    _uuidIdMapping.add(callId: callId, uuid: uuidString, validate: true);

    _delegate.didPushIncomingCall(handle.toCallkeep(), displayName, video, callId, error?.value.toCallkeep());
  }

  @override
  Future<bool> performStartCall(
    String uuid,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) async {
    return _delegate.performStartCall(
      _uuidIdMapping.getCallId(uuid: uuid),
      handle.toCallkeep(),
      displayNameOrContactIdentifier,
      video,
    );
  }

  @override
  Future<bool> performAnswerCall(String uuid) async {
    final callId = _uuidIdMapping.getCallId(uuid: uuid);
    return _delegate.performAnswerCall(callId);
  }

  @override
  Future<bool> performEndCall(String uuid) async {
    return _delegate.performEndCall(_uuidIdMapping.getCallId(uuid: uuid));
  }

  @override
  Future<bool> performSetHeld(String uuid, bool onHold) async {
    return _delegate.performSetHeld(_uuidIdMapping.getCallId(uuid: uuid), onHold);
  }

  @override
  Future<bool> performSetMuted(String uuid, bool muted) async {
    return _delegate.performSetMuted(_uuidIdMapping.getCallId(uuid: uuid), muted);
  }

  @override
  Future<bool> performSendDTMF(String uuid, String key) async {
    return _delegate.performSendDTMF(_uuidIdMapping.getCallId(uuid: uuid), key);
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
  Future<bool> performSetSpeaker(String uuid, bool enabled) async {
    return _delegate.performSetSpeaker(_uuidIdMapping.getCallId(uuid: uuid), enabled);
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

class _UuidIdMapping {
  final Map<String, String> _mappings = {};

  // Retrieves the Call ID associated with the given UUID.
  // Throws a StateError if the UUID is not found in the mapping.
  String getCallId({
    required String uuid,
  }) {
    final callId = _mappings[uuid.toLowerCase()];
    if (callId == null) {
      throw StateError('UUID not found');
    }
    return callId;
  }

  // Retrieves the UUID associated with the given Call ID.
  // Throws a StateError if the Call ID is not found in the mapping.
  String getUUID({
    required String callId,
  }) {
    final uuid = _mappings.entries
        .firstWhere((entry) => entry.value == callId.toLowerCase(), orElse: () => throw StateError('Call ID not found'))
        .key;
    return uuid;
  }

  // Stores the mapping of Call ID and UUID directly.
  // If [validate] is true, checks if the provided [callId] can be converted to the specified [uuid] using a version 5 UUID algorithm.
  void add({
    required String callId,
    required String uuid,
    bool validate = false,
  }) {
    if (validate) {
      final originalUUID = convertToUUID(callId: callId);
      if (originalUUID.toLowerCase() != uuid.toLowerCase()) {
        throw ArgumentError('The provided callId does not match the specified UUID.');
      }
    }
    _mappings[uuid.toLowerCase()] = callId;
  }

  // Converts a Call ID to a UUID using a version 5 UUID algorithm.
  String convertToUUID({
    required String callId,
  }) {
    return const Uuid().v5obj(Uuid.NAMESPACE_OID, callId).uuid;
  }
}
