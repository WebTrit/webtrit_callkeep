// ignore_for_file: avoid_positional_boolean_parameters, one_member_abstracts

import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/common/callkeep.pigeon.dart',
    dartTestOut: 'test/src/common/test_callkeep.pigeon.dart',
    kotlinOut: 'android/src/main/kotlin/com/webtrit/callkeep/Generated.kt',
    kotlinOptions: KotlinOptions(
      package: 'com.webtrit.callkeep',
      errorClassName: 'HostCallsDartPigeonFlutterError',
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

enum PCallkeepLifecycleType {
  onCreate,
  onStart,
  onResume,
  onPause,
  onStop,
  onDestroy,
  onAny,
}

class PCallkeepServiceStatus {
  late PCallkeepLifecycleType lifecycle;
  late bool autoRestartOnTerminate;
  late bool autoStartOnBoot;
  late bool lockScreen;
  late bool activityReady;
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
abstract class PHostIsolateApi {
  @async
  void setUp({
    int? callbackDispatcher,
    int? onStartHandler,
    int? onChangedLifecycleHandler,
    bool autoRestartOnTerminate = false,
    bool autoStartOnBoot = false,
    String? androidNotificationName,
    String? androidNotificationDescription,
  });

  @async
  void startService({
    required String data,
  });

  @async
  void stopService();

  @async
  void finishActivity();
}

@FlutterApi()
abstract class PDelegateBackgroundRegisterFlutterApi {
  @async
  void onWakeUpBackgroundHandler(int userCallbackHandle, PCallkeepServiceStatus status, String data);

  @async
  void onApplicationStatusChanged(int applicationStatusCallbackHandle, PCallkeepServiceStatus status);
}

@HostApi()
abstract class PHostApi {
  bool isSetUp();

  @async
  void setUp(POptions options);

  @async
  void tearDown();

  @async
  PIncomingCallError? reportNewIncomingCall(
    String callId,
    PHandle handle,
    String? displayName,
    bool hasVideo,
  );

  @async
  void reportConnectingOutgoingCall(String callId);

  @async
  void reportConnectedOutgoingCall(String callId);

  @async
  void reportUpdateCall(
    String callId,
    PHandle? handle,
    String? displayName,
    bool? hasVideo,
    bool? proximityEnabled,
  );

  @async
  void reportEndCall(String callId, PEndCallReason reason);

  @async
  PCallRequestError? startCall(
    String callId,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
    bool proximityEnabled,
  );

  @async
  PCallRequestError? answerCall(String callId);

  @async
  PCallRequestError? endCall(String callId);

  @async
  PCallRequestError? setHeld(String callId, bool onHold);

  @async
  PCallRequestError? setMuted(String callId, bool muted);

  @async
  PCallRequestError? setSpeaker(String callId, bool enabled);

  @async
  PCallRequestError? sendDTMF(String callId, String key);
}

@FlutterApi()
abstract class PDelegateFlutterApi {
  void continueStartCallIntent(
    PHandle handle,
    String? displayName,
    bool video,
  );

  void didPushIncomingCall(
    PHandle handle,
    String? displayName,
    bool video,
    String callId,
    PIncomingCallError? error,
  );

  @async
  bool performStartCall(
    String callId,
    PHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  );

  @async
  bool performAnswerCall(String callId);

  @async
  bool performEndCall(String callId);

  @async
  bool performSetHeld(String callId, bool onHold);

  @async
  bool performSetMuted(String callId, bool muted);

  @async
  bool performSetSpeaker(String callId, bool enabled);

  @async
  bool performSendDTMF(String callId, String key);

  void didActivateAudioSession();

  void didDeactivateAudioSession();

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
  String? pushTokenForPushTypeVoIP();
}

@FlutterApi()
abstract class PPushRegistryDelegateFlutterApi {
  void didUpdatePushTokenForPushTypeVoIP(String? token);
}

@FlutterApi()
abstract class PDelegateLogsFlutterApi {
  void onLog(PLogTypeEnum type, String tag, String message);
}
