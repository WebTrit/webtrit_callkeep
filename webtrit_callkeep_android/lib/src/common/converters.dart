// ignore_for_file: public_member_api_docs, always_use_package_imports

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

import 'callkeep.pigeon.dart';

extension PHandleTypeEnumConverter on PHandleTypeEnum {
  CallkeepHandleType toCallkeep() {
    switch (this) {
      case PHandleTypeEnum.generic:
        return CallkeepHandleType.generic;
      case PHandleTypeEnum.number:
        return CallkeepHandleType.number;
      case PHandleTypeEnum.email:
        return CallkeepHandleType.email;
    }
  }
}

extension PLogTypeEnumConverter on PLogTypeEnum {
  CallkeepLogType toCallkeep() {
    switch (this) {
      case PLogTypeEnum.debug:
        return CallkeepLogType.debug;
      case PLogTypeEnum.error:
        return CallkeepLogType.error;
      case PLogTypeEnum.info:
        return CallkeepLogType.info;
      case PLogTypeEnum.verbose:
        return CallkeepLogType.verbose;
      case PLogTypeEnum.warn:
        return CallkeepLogType.warn;
    }
  }
}

extension PHandleConverter on PHandle {
  CallkeepHandle toCallkeep() {
    return CallkeepHandle(
      type: type.toCallkeep(),
      value: value,
    );
  }
}

extension PIncomingCallErrorEnumConverter on PIncomingCallErrorEnum {
  CallkeepIncomingCallError toCallkeep() {
    switch (this) {
      case PIncomingCallErrorEnum.unknown:
        return CallkeepIncomingCallError.unknown;
      case PIncomingCallErrorEnum.unentitled:
        return CallkeepIncomingCallError.unentitled;
      case PIncomingCallErrorEnum.callIdAlreadyExists:
        return CallkeepIncomingCallError.callIdAlreadyExists;
      case PIncomingCallErrorEnum.callIdAlreadyExistsAndAnswered:
        return CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered;
      case PIncomingCallErrorEnum.callIdAlreadyTerminated:
        return CallkeepIncomingCallError.callIdAlreadyTerminated;
      case PIncomingCallErrorEnum.filteredByDoNotDisturb:
        return CallkeepIncomingCallError.filteredByDoNotDisturb;
      case PIncomingCallErrorEnum.filteredByBlockList:
        return CallkeepIncomingCallError.filteredByBlockList;
      case PIncomingCallErrorEnum.internal:
        return CallkeepIncomingCallError.internal;
    }
  }
}

extension PCallRequestErrorEnumConverter on PCallRequestErrorEnum {
  CallkeepCallRequestError toCallkeep() {
    switch (this) {
      case PCallRequestErrorEnum.unknown:
        return CallkeepCallRequestError.unknown;
      case PCallRequestErrorEnum.unentitled:
        return CallkeepCallRequestError.unentitled;
      case PCallRequestErrorEnum.unknownCallUuid:
        return CallkeepCallRequestError.unknownCallUuid;
      case PCallRequestErrorEnum.callUuidAlreadyExists:
        return CallkeepCallRequestError.callUuidAlreadyExists;
      case PCallRequestErrorEnum.maximumCallGroupsReached:
        return CallkeepCallRequestError.maximumCallGroupsReached;
      case PCallRequestErrorEnum.internal:
        return CallkeepCallRequestError.internal;
      case PCallRequestErrorEnum.emergencyNumber:
        return CallkeepCallRequestError.emergencyNumber;
    }
  }
}

extension CallkeepTypeEnumConverter on CallkeepLogType {
  PLogTypeEnum toPigeon() {
    switch (this) {
      case CallkeepLogType.debug:
        return PLogTypeEnum.debug;
      case CallkeepLogType.error:
        return PLogTypeEnum.error;
      case CallkeepLogType.info:
        return PLogTypeEnum.info;
      case CallkeepLogType.verbose:
        return PLogTypeEnum.verbose;
      case CallkeepLogType.warn:
        return PLogTypeEnum.warn;
    }
  }
}

extension CallkeepHandleTypeConverter on CallkeepHandleType {
  PHandleTypeEnum toPigeon() {
    switch (this) {
      case CallkeepHandleType.generic:
        return PHandleTypeEnum.generic;
      case CallkeepHandleType.number:
        return PHandleTypeEnum.number;
      case CallkeepHandleType.email:
        return PHandleTypeEnum.email;
    }
  }
}

extension CallkeepHandleConverter on CallkeepHandle {
  PHandle toPigeon() {
    return PHandle(
      type: type.toPigeon(),
      value: value,
    );
  }
}

extension CallkeepEndCallReasonConverter on CallkeepEndCallReason {
  PEndCallReasonEnum toPigeon() {
    switch (this) {
      case CallkeepEndCallReason.failed:
        return PEndCallReasonEnum.failed;
      case CallkeepEndCallReason.remoteEnded:
        return PEndCallReasonEnum.remoteEnded;
      case CallkeepEndCallReason.unanswered:
        return PEndCallReasonEnum.unanswered;
      case CallkeepEndCallReason.answeredElsewhere:
        return PEndCallReasonEnum.answeredElsewhere;
      case CallkeepEndCallReason.declinedElsewhere:
        return PEndCallReasonEnum.declinedElsewhere;
      case CallkeepEndCallReason.missed:
        return PEndCallReasonEnum.missed;
    }
  }
}

extension CallkeepOptionsConverter on CallkeepOptions {
  POptions toPigeon() {
    return POptions(
      ios: ios.toPigeon(),
      android: android.toPigeon(),
    );
  }
}

extension CallkeepIOSOptionsConverter on CallkeepIOSOptions {
  PIOSOptions toPigeon() {
    return PIOSOptions(
      localizedName: localizedName,
      ringtoneSound: ringtoneSound,
      iconTemplateImageAssetName: iconTemplateImageAssetName,
      maximumCallGroups: maximumCallGroups,
      maximumCallsPerCallGroup: maximumCallsPerCallGroup,
      supportsHandleTypeGeneric: supportedHandleTypes.contains(CallkeepHandleType.generic),
      supportsHandleTypePhoneNumber: supportedHandleTypes.contains(CallkeepHandleType.number),
      supportsHandleTypeEmailAddress: supportedHandleTypes.contains(CallkeepHandleType.email),
      supportsVideo: supportsVideo,
      includesCallsInRecents: includesCallsInRecents,
      driveIdleTimerDisabled: driveIdleTimerDisabled,
    );
  }
}

extension CallkeepAndroidOptionsConverter on CallkeepAndroidOptions {
  PAndroidOptions toPigeon() {
    return PAndroidOptions(
      ringtoneSound: ringtoneSound,
      incomingPath: incomingPath,
      rootPath: rootPath,
    );
  }
}

extension CallkeepLifecycleTypeConverter on CallkeepLifecycleType {
  PCallkeepLifecycleType toPigeon() {
    switch (this) {
      case CallkeepLifecycleType.onCreate:
        return PCallkeepLifecycleType.onCreate;
      case CallkeepLifecycleType.onStart:
        return PCallkeepLifecycleType.onStart;
      case CallkeepLifecycleType.onResume:
        return PCallkeepLifecycleType.onResume;
      case CallkeepLifecycleType.onPause:
        return PCallkeepLifecycleType.onPause;
      case CallkeepLifecycleType.onStop:
        return PCallkeepLifecycleType.onStop;
      case CallkeepLifecycleType.onDestroy:
        return PCallkeepLifecycleType.onDestroy;
      case CallkeepLifecycleType.onAny:
        return PCallkeepLifecycleType.onAny;
    }
  }
}

extension PCallkeepLifecycleTypeConverter on PCallkeepLifecycleType {
  CallkeepLifecycleType toCallkeep() {
    switch (this) {
      case PCallkeepLifecycleType.onCreate:
        return CallkeepLifecycleType.onCreate;
      case PCallkeepLifecycleType.onStart:
        return CallkeepLifecycleType.onStart;
      case PCallkeepLifecycleType.onResume:
        return CallkeepLifecycleType.onResume;
      case PCallkeepLifecycleType.onPause:
        return CallkeepLifecycleType.onPause;
      case PCallkeepLifecycleType.onStop:
        return CallkeepLifecycleType.onStop;
      case PCallkeepLifecycleType.onDestroy:
        return CallkeepLifecycleType.onDestroy;
      case PCallkeepLifecycleType.onAny:
        return CallkeepLifecycleType.onAny;
    }
  }
}

extension PCallkeepServiceStatusConverter on PCallkeepServiceStatus {
  CallkeepServiceStatus toCallkeep() {
    return CallkeepServiceStatus(
      lifecycle: lifecycle.toCallkeep(),
      autoStartOnBoot: autoStartOnBoot,
      autoRestartOnTerminate: autoRestartOnTerminate,
      lockScreen: lockScreen,
      activityReady: activityReady,
      activeCalls: activeCalls,
    );
  }
}

extension CallkeepServiceStatusConverter on CallkeepServiceStatus {
  PCallkeepServiceStatus toPigeon() {
    return PCallkeepServiceStatus(
      lifecycle: lifecycle.toPigeon(),
      autoStartOnBoot: autoStartOnBoot,
      autoRestartOnTerminate: autoRestartOnTerminate,
      lockScreen: lockScreen,
      activityReady: activityReady,
      activeCalls: activeCalls,
    );
  }
}
