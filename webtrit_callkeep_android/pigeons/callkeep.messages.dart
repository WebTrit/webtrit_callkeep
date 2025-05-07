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
  late String? ringbackSound;
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
  late String? ringbackSound;
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

enum PCallkeepAndroidBatteryMode {
  unrestricted,
  optimized,
  restricted,
  unknown,
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

enum PCallkeepLifecycleEvent {
  onCreate,
  onStart,
  onResume,
  onPause,
  onStop,
  onDestroy,
  onAny,
}

enum PCallkeepPushNotificationSyncStatus {
  synchronizeCallStatus,
  releaseResources,
}

class PCallkeepServiceStatus {
  late PCallkeepLifecycleEvent lifecycleEvent;
  late PCallkeepSignalingStatus? mainSignalingStatus;
}

enum PCallkeepConnectionState {
  stateInitializing,
  stateNew,
  stateRinging,
  stateDialing,
  stateActive,
  stateHolding,
  stateDisconnected,
  statePullingCall;
}

enum PCallkeepDisconnectCauseType {
  unknown,
  error,
  local,
  remote,
  canceled,
  missed,
  rejected,
  busy,
  restricted,
  other,
  connectionManagerNotSupported,
  answeredElsewhere,
  callPulled,
}

enum PCallkeepSignalingStatus {
  disconnecting,
  disconnect,
  connecting,
  connect,
  failure,
}

class PCallkeepDisconnectCause {
  late PCallkeepDisconnectCauseType type;
  late String? reason;
}

class PCallkeepConnection {
  late String callId;
  late PCallkeepConnectionState state;
  late PCallkeepDisconnectCause disconnectCause;
}

@HostApi()
abstract class PHostBackgroundSignalingIsolateBootstrapApi {
  @async
  void initializeSignalingServiceCallback({
    required int callbackDispatcher,
    required int onSync,
  });

  @async
  void configureSignalingService({
    String? androidNotificationName,
    String? androidNotificationDescription,
  });

  @async
  void startService();

  @async
  void stopService();
}

@HostApi()
abstract class PHostBackgroundSignalingIsolateApi {
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
abstract class PHostBackgroundPushNotificationIsolateBootstrapApi {
  @async
  void initializePushNotificationCallback({
    required int callbackDispatcher,
    required int onNotificationSync,
  });

  @async
  void configureSignalingService({
    bool launchBackgroundIsolateEvenIfAppIsOpen = false,
  });

  @async
  PIncomingCallError? reportNewIncomingCall(
    String callId,
    PHandle handle,
    String? displayName,
    bool hasVideo,
  );
}

@HostApi()
abstract class PHostBackgroundPushNotificationIsolateApi {
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

  @async
  void openFullScreenIntentSettings();

  @async
  void openSettings();

  @async
  PCallkeepAndroidBatteryMode getBatteryMode();
}

@HostApi()
abstract class PHostSoundApi {
  @async
  void playRingbackSound();

  @async
  void stopRingbackSound();
}

@FlutterApi()
abstract class PDelegateBackgroundRegisterFlutterApi {
  @async
  void onWakeUpBackgroundHandler(int userCallbackHandle, PCallkeepServiceStatus status);

  @async
  void onApplicationStatusChanged(int applicationStatusCallbackHandle, PCallkeepServiceStatus status);

  @async
  void onNotificationSync(int pushNotificationSyncStatusHandle, PCallkeepPushNotificationSyncStatus status);
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

  @ObjCSelector('reportEndCall:displayName:reason:')
  @async
  void reportEndCall(String callId, String displayName, PEndCallReason reason);

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

@HostApi()
abstract class PHostConnectionsApi {
  @ObjCSelector('getConnection:')
  @async
  PCallkeepConnection? getConnection(String callId);

  @async
  List<PCallkeepConnection> getConnections();

  @async
  void cleanConnections();

  @async
  void updateActivitySignalingStatus(PCallkeepSignalingStatus status);
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
  void performAnswerCall(String callId);

  @async
  void performEndCall(String callId);

  @async
  void performReceivedCall(
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
