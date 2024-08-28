// ignore_for_file: avoid_positional_boolean_parameters, one_member_abstracts

import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/common/callkeep.pigeon.dart',
    dartTestOut: 'test/src/common/test_callkeep.pigeon.dart',
    kotlinOut: 'android/src/main/kotlin/com/webtrit/callkeep/Generated.kt',
    kotlinOptions: KotlinOptions(
      package: 'com.webtrit.callkeep',
    ),
  ),
)
class PIOSOptions {
  late String localizedName;
  late String? ringtoneSound;
  late String? iconTemplateImageAssetName;
  late int maximumCallGroups;
  late int maximumCallsPerCallGroup;
  late bool? supportsHandleTypeGeneric;
  late bool? supportsHandleTypePhoneNumber;
  late bool? supportsHandleTypeEmailAddress;
  late bool supportsVideo;
  late bool includesCallsInRecents;
  late bool driveIdleTimerDisabled;
}

class PAndroidOptions {
  late String? ringtoneSound;
  late String incomingPath;
  late String rootPath;
}

class POptions {
  late PIOSOptions ios;
  late PAndroidOptions android;
}

enum PLogTypeEnum {
  debug,
  error,
  info,
  verbose,
  warn,
}

enum PSpecialPermissionStatusTypeEnum {
  denied,
  granted,
}

enum PHandleTypeEnum {
  generic,
  number,
  email,
}

enum PCallInfoConsts {
  uuid,
  dtmf,
  isVideo,
  number,
  name,
}

class PHandle {
  late PHandleTypeEnum type;
  late String value;
}

enum PEndCallReasonEnum {
  failed,
  remoteEnded,
  unanswered,
  answeredElsewhere,
  declinedElsewhere,
  missed,
}

// TODO: See https://github.com/flutter/flutter/issues/87307
class PEndCallReason {
  late PEndCallReasonEnum value;
}

enum PIncomingCallErrorEnum {
  unknown,
  unentitled,
  callIdAlreadyExists,
  callIdAlreadyExistsAndAnswered,
  callIdAlreadyTerminated,
  filteredByDoNotDisturb,
  filteredByBlockList,
  internal,
}

// TODO: See https://github.com/flutter/flutter/issues/87307
class PIncomingCallError {
  late PIncomingCallErrorEnum value;
}

enum PCallRequestErrorEnum {
  unknown,
  unentitled,
  unknownCallUuid,
  callUuidAlreadyExists,
  maximumCallGroupsReached,
  internal,
  emergencyNumber,
}

// TODO: See https://github.com/flutter/flutter/issues/87307
class PCallRequestError {
  late PCallRequestErrorEnum value;
}

@HostApi()
abstract class PHostBackgroundServiceApi {
  @async
  void incomingCall(
    String callId,
    PHandle handle,
    String? displayName,
    bool hasVideo,
  );

  @async
  void endCall(
    String callId,
  );

  @async
  void endAllCalls();
}

@HostApi()
abstract class PHostPermissionsApi {
  @async
  PSpecialPermissionStatusTypeEnum getFullScreenIntentPermissionStatus();
}

@HostApi()
abstract class PHostApi {
  @ObjCSelector('isSetUp')
  bool isSetUp();

  @ObjCSelector('setUp:')
  @async
  void setUp(POptions options);

  @ObjCSelector('tearDown')
  @async
  void tearDown();

  @ObjCSelector('reportNewIncomingCall:handle:displayName:hasVideo:')
  @async
  PIncomingCallError? reportNewIncomingCall(
    String callId,
    PHandle handle,
    String? displayName,
    bool hasVideo,
  );

  @ObjCSelector('reportConnectingOutgoingCall:')
  @async
  void reportConnectingOutgoingCall(String callId);

  @ObjCSelector('reportConnectedOutgoingCall:')
  @async
  void reportConnectedOutgoingCall(String callId);

  @ObjCSelector('reportUpdateCall:handle:displayName:hasVideo:proximityEnabled:')
  @async
  void reportUpdateCall(
    String callId,
    PHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  );

  @ObjCSelector('reportEndCall:reason:')
  @async
  void reportEndCall(String callId, PEndCallReason reason);

  @ObjCSelector('startCall:handle:displayNameOrContactIdentifier:video:proximityEnabled:')
  @async
  PCallRequestError? startCall(
    String callId,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  );

  @ObjCSelector('answerCall:')
  @async
  PCallRequestError? answerCall(String callId);

  @ObjCSelector('endCall:')
  @async
  PCallRequestError? endCall(String callId);

  @ObjCSelector('setHeld:onHold:')
  @async
  PCallRequestError? setHeld(String callId, bool onHold);

  @ObjCSelector('setMuted:muted:')
  @async
  PCallRequestError? setMuted(String callId, bool muted);

  @ObjCSelector('setSpeaker:enabled:')
  @async
  PCallRequestError? setSpeaker(String callId, bool enabled);

  @ObjCSelector('sendDTMF:key:')
  @async
  PCallRequestError? sendDTMF(String callId, String key);
}

@FlutterApi()
abstract class PDelegateFlutterApi {
  @ObjCSelector('continueStartCallIntentHandle:displayName:video:')
  void continueStartCallIntent(
    PHandle handle,
    String? displayName,
    bool video,
  );

  @ObjCSelector('didPushIncomingCallHandle:displayName:video:id:error:')
  void didPushIncomingCall(
    PHandle handle,
    String? displayName,
    bool video,
    String callId,
    PIncomingCallError? error,
  );

  @ObjCSelector('performStartCall:handle:displayNameOrContactIdentifier:video:')
  @async
  bool performStartCall(
    String callId,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  );

  @ObjCSelector('performAnswerCall:')
  @async
  bool performAnswerCall(String callId);

  @ObjCSelector('performEndCall:')
  @async
  bool performEndCall(String callId);

  @ObjCSelector('performSetHeld:onHold:')
  @async
  bool performSetHeld(String callId, bool onHold);

  @ObjCSelector('performSetMuted:muted:')
  @async
  bool performSetMuted(String callId, bool muted);

  @ObjCSelector('performSetSpeaker:enabled:')
  @async
  bool performSetSpeaker(String callId, bool enabled);

  @ObjCSelector('performSendDTMF:key:')
  @async
  bool performSendDTMF(String callId, String key);

  @ObjCSelector('didActivateAudioSession')
  void didActivateAudioSession();

  @ObjCSelector('didDeactivateAudioSession')
  void didDeactivateAudioSession();

  @ObjCSelector('didReset')
  void didReset();
}

@FlutterApi()
abstract class PDelegateBackgroundServiceFlutterApi {
  @async
  void performEndCall(String callId);

  @async
  void endCallReceived(
    String callId,
    String number,
    bool video,
    int createdTime,
    int? acceptedTime,
    int? hungUpTime,
  );
}

@HostApi()
abstract class PPushRegistryHostApi {
  @ObjCSelector('pushTokenForPushTypeVoIP')
  String? pushTokenForPushTypeVoIP();
}

@FlutterApi()
abstract class PPushRegistryDelegateFlutterApi {
  @ObjCSelector('didUpdatePushTokenForPushTypeVoIP:')
  void didUpdatePushTokenForPushTypeVoIP(String? token);
}

@FlutterApi()
abstract class PDelegateLogsFlutterApi {
  void onLog(PLogTypeEnum type, String tag, String message);
}
