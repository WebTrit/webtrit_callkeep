import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/common/callkeep.pigeon.dart',
    dartTestOut: 'test/src/common/test_callkeep.pigeon.dart',
    objcHeaderOut: 'ios/Classes/Generated.h',
    objcSourceOut: 'ios/Classes/Generated.m',
    objcOptions: ObjcOptions(
      prefix: 'WT',
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
  callUuidAlreadyExists,
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
}

// TODO: See https://github.com/flutter/flutter/issues/87307
class PCallRequestError {
  late PCallRequestErrorEnum value;
}

// TODO: Rename to background service
@HostApi()
abstract class PHostAndroidServiceApi {
  @async
  void hungUp(
    String callId,
    String uuidString,
  );

  @async
  void incomingCall(
    String callId,
    String uuidString,
    PHandle handle,
    String? displayName,
    bool hasVideo,
  );
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
    String uuidString,
    PHandle handle,
    String? displayName,
    bool hasVideo,
  );

  @ObjCSelector('reportConnectingOutgoingCall:')
  @async
  void reportConnectingOutgoingCall(String uuidString);

  @ObjCSelector('reportConnectedOutgoingCall:')
  @async
  void reportConnectedOutgoingCall(String uuidString);

  @ObjCSelector('reportUpdateCall:handle:displayName:hasVideo:proximityEnabled:')
  @async
  void reportUpdateCall(
    String uuidString,
    PHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  );

  @ObjCSelector('reportEndCall:displayName:reason:')
  @async
  void reportEndCall(String uuidString,String displayName, PEndCallReason reason);

  @ObjCSelector('startCall:handle:displayNameOrContactIdentifier:video:proximityEnabled:')
  @async
  PCallRequestError? startCall(
    String uuidString,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  );

  @ObjCSelector('answerCall:')
  @async
  PCallRequestError? answerCall(String uuidString);

  @ObjCSelector('endCall:')
  @async
  PCallRequestError? endCall(String uuidString);

  @ObjCSelector('setHeld:onHold:')
  @async
  PCallRequestError? setHeld(String uuidString, bool onHold);

  @ObjCSelector('setMuted:muted:')
  @async
  PCallRequestError? setMuted(String uuidString, bool muted);

  @ObjCSelector('setSpeaker:enabled:')
  @async
  PCallRequestError? setSpeaker(String uuidString, bool enabled);

  @ObjCSelector('sendDTMF:key:')
  @async
  PCallRequestError? sendDTMF(String uuidString, String key);
}

@FlutterApi()
abstract class PDelegateFlutterApi {
  @ObjCSelector('continueStartCallIntentHandle:displayName:video:')
  void continueStartCallIntent(
    PHandle handle,
    String? displayName,
    bool video,
  );

  @ObjCSelector('didPushIncomingCallHandle:displayName:video:callId:uuid:error:')
  void didPushIncomingCall(
    PHandle handle,
    String? displayName,
    bool video,
    String callId,
    String uuidString,
    PIncomingCallError? error,
  );

  @ObjCSelector('performStartCall:handle:displayNameOrContactIdentifier:video:')
  @async
  bool performStartCall(
    String uuidString,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  );

  @ObjCSelector('performAnswerCall:')
  @async
  bool performAnswerCall(String uuidString);

  @ObjCSelector('performEndCall:')
  @async
  bool performEndCall(String uuidString);

  @ObjCSelector('performSetHeld:onHold:')
  @async
  bool performSetHeld(String uuidString, bool onHold);

  @ObjCSelector('performSetMuted:muted:')
  @async
  bool performSetMuted(String uuidString, bool muted);

  @ObjCSelector('performSetSpeaker:enabled:')
  @async
  bool performSetSpeaker(String uuidString, bool enabled);

  @ObjCSelector('performSendDTMF:key:')
  @async
  bool performSendDTMF(String uuidString, String key);

  @ObjCSelector('didActivateAudioSession')
  void didActivateAudioSession();

  @ObjCSelector('didDeactivateAudioSession')
  void didDeactivateAudioSession();

  @ObjCSelector('didReset')
  void didReset();
}

// TODO: Rename to background service
@FlutterApi()
abstract class PDelegateAndroidServiceFlutterApi {
  @async
  void performEndCall(String uuidString);
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

@HostApi()
abstract class AndroidHelperHostApi {
  @async
  void wakeUpApp(String? path);

  @async
  bool isLockScreen();
}
