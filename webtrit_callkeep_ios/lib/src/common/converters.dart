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
      case PIncomingCallErrorEnum.callUuidAlreadyExists:
        return CallkeepIncomingCallError.callIdAlreadyExists;
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
      ringbackSound: ringbackSound,
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
    return PAndroidOptions();
  }
}
