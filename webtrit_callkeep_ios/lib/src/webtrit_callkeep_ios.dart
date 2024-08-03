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

  final _UUIDToCallIdMapping _uuidToCallIdMapping = _UUIDToCallIdMapping();

  @override
  void setDelegate(CallkeepDelegate? delegate) {
    if (delegate != null) {
      PDelegateFlutterApi.setup(_CallkeepDelegateRelay(delegate, _uuidToCallIdMapping));
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
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api
        .reportNewIncomingCall(_uuidToCallIdMapping.getUUID(callId: callId), handle.toPigeon(), displayName, hasVideo)
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<void> reportConnectingOutgoingCall(String callId) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.reportConnectingOutgoingCall(_uuidToCallIdMapping.getUUID(callId: callId));
  }

  @override
  Future<void> reportConnectedOutgoingCall(String callId) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.reportConnectedOutgoingCall(_uuidToCallIdMapping.getUUID(callId: callId));
  }

  @override
  Future<void> reportUpdateCall(
    String callId,
    CallkeepHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  ) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.reportUpdateCall(
      _uuidToCallIdMapping.getUUID(callId: callId),
      handle?.toPigeon(),
      displayName,
      hasVideo,
      proximityEnabled,
    );
  }

  @override
  Future<void> reportEndCall(String callId, CallkeepEndCallReason reason) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.reportEndCall(_uuidToCallIdMapping.getUUID(callId: callId), PEndCallReason(value: reason.toPigeon()));
  }

  @override
  Future<CallkeepCallRequestError?> startCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  ) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api
        .startCall(
          _uuidToCallIdMapping.getUUID(callId: callId),
          handle.toPigeon(),
          displayNameOrContactIdentifier,
          video,
          proximityEnabled,
        )
        .then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> answerCall(String callId) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.answerCall(_uuidToCallIdMapping.getUUID(callId: callId)).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> endCall(String callId) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.endCall(_uuidToCallIdMapping.getUUID(callId: callId)).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setHeld(String callId, bool onHold) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.setHeld(_uuidToCallIdMapping.getUUID(callId: callId), onHold).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setMuted(String callId, bool muted) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.setMuted(_uuidToCallIdMapping.getUUID(callId: callId), muted).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> setSpeaker(String callId, bool enabled) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.setSpeaker(_uuidToCallIdMapping.getUUID(callId: callId), enabled).then((value) => value?.value.toCallkeep());
  }

  @override
  Future<CallkeepCallRequestError?> sendDTMF(String callId, String key) async {
    _uuidToCallIdMapping.putUUID(callId: callId);
    return _api.sendDTMF(_uuidToCallIdMapping.getUUID(callId: callId), key).then((value) => value?.value.toCallkeep());
  }
}

class _CallkeepDelegateRelay implements PDelegateFlutterApi {
  const _CallkeepDelegateRelay(this._delegate, this._uuidToCallIdMapping);

  final CallkeepDelegate _delegate;

  final _UUIDToCallIdMapping _uuidToCallIdMapping;

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
    _uuidToCallIdMapping.add(callId: callId, uuid: uuidString, validate: true);

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
      _uuidToCallIdMapping.getCallId(uuid: uuid),
      handle.toCallkeep(),
      displayNameOrContactIdentifier,
      video,
    );
  }

  @override
  Future<bool> performAnswerCall(String uuid) async {
    final callId = _uuidToCallIdMapping.getCallId(uuid: uuid);
    return _delegate.performAnswerCall(callId);
  }

  @override
  Future<bool> performEndCall(String uuid) async {
    return _delegate.performEndCall(_uuidToCallIdMapping.getCallId(uuid: uuid));
  }

  @override
  Future<bool> performSetHeld(String uuid, bool onHold) async {
    return _delegate.performSetHeld(_uuidToCallIdMapping.getCallId(uuid: uuid), onHold);
  }

  @override
  Future<bool> performSetMuted(String uuid, bool muted) async {
    return _delegate.performSetMuted(_uuidToCallIdMapping.getCallId(uuid: uuid), muted);
  }

  @override
  Future<bool> performSendDTMF(String uuid, String key) async {
    return _delegate.performSendDTMF(_uuidToCallIdMapping.getCallId(uuid: uuid), key);
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
    return _delegate.performSetSpeaker(_uuidToCallIdMapping.getCallId(uuid: uuid), enabled);
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

class _UUIDToCallIdMapping {
  final Map<String, String> _mapping = {};

  // Retrieves the UUID associated with the given Call ID.
  // Throws a StateError if the Call ID is not found in the mapping.
  String getUUID({
    required String callId,
  }) {
    final uuid = _mapping.entries
        .firstWhere((entry) => entry.value == callId.toLowerCase(), orElse: () => throw StateError('Call ID not found'))
        .key;
    return uuid;
  }

  // Generates a UUID from the provided [callId] using a version 5 UUID algorithm.
  // Stores the mapping of the generated UUID (in lowercase) and the provided [callId].
  void putUUID({
    required String callId,
  }) {
    final uuid = _convertToUUID(callId: callId);
    _mapping[uuid.toLowerCase()] = callId;
  }

  // Retrieves the Call ID associated with the given UUID.
  // Throws a StateError if the UUID is not found in the mapping.
  String getCallId({
    required String uuid,
  }) {
    final callId = _mapping[uuid.toLowerCase()];
    if (callId == null) {
      throw StateError('UUID not found');
    }
    return callId;
  }

  // Stores the mapping of Call ID and UUID directly.
  // If [validate] is true, checks if the provided [callId] can be converted to the specified [uuid] using a version 5 UUID algorithm.
  // Throws an ArgumentError if the conversion does not match the specified UUID.
  void add({
    required String callId,
    required String uuid,
    bool validate = false,
  }) {
    if (validate) {
      final originalUUID = _convertToUUID(callId: callId);
      if (originalUUID.toLowerCase() != uuid.toLowerCase()) {
        throw ArgumentError('The provided callId does not match the specified UUID.');
      }
    }
    _mapping[uuid.toLowerCase()] = callId;
  }

  // Converts a Call ID to a UUID using a version 5 UUID algorithm.
  String _convertToUUID({
    required String callId,
  }) {
    return const Uuid().v5obj(Uuid.NAMESPACE_OID, callId).uuid;
  }
}
