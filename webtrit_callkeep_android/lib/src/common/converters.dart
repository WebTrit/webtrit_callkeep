// ignore_for_file: public_member_api_docs, always_use_package_imports

import 'dart:convert';

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
      incomingPath: incomingPath,
      rootPath: rootPath,
      ringtoneSound: ringtoneSound,
      ringbackSound: ringbackSound,
    );
  }
}

extension PSpecialPermissionStatusTypeEnumConverter on PSpecialPermissionStatusTypeEnum {
  CallkeepSpecialPermissionStatus toCallkeep() {
    switch (this) {
      case PSpecialPermissionStatusTypeEnum.denied:
        return CallkeepSpecialPermissionStatus.denied;
      case PSpecialPermissionStatusTypeEnum.granted:
        return CallkeepSpecialPermissionStatus.granted;
    }
  }
}

extension PCallkeepAndroidBatteryModeConverter on PCallkeepAndroidBatteryMode {
  CallkeepAndroidBatteryMode toCallkeep() {
    switch (this) {
      case PCallkeepAndroidBatteryMode.unrestricted:
        return CallkeepAndroidBatteryMode.unrestricted;
      case PCallkeepAndroidBatteryMode.optimized:
        return CallkeepAndroidBatteryMode.optimized;
      case PCallkeepAndroidBatteryMode.restricted:
        return CallkeepAndroidBatteryMode.restricted;
      case PCallkeepAndroidBatteryMode.unknown:
        return CallkeepAndroidBatteryMode.unknown;
    }
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

extension PCallkeepSignalingStatusConverter on PCallkeepSignalingStatus {
  CallkeepSignalingStatus toCallkeep() {
    switch (this) {
      case PCallkeepSignalingStatus.disconnecting:
        return CallkeepSignalingStatus.disconnecting;
      case PCallkeepSignalingStatus.disconnect:
        return CallkeepSignalingStatus.disconnect;
      case PCallkeepSignalingStatus.connecting:
        return CallkeepSignalingStatus.connecting;
      case PCallkeepSignalingStatus.connect:
        return CallkeepSignalingStatus.connect;
      case PCallkeepSignalingStatus.failure:
        return CallkeepSignalingStatus.failure;
    }
  }
}

extension CallkeepSignalingStatusConverter on CallkeepSignalingStatus {
  PCallkeepSignalingStatus toPigeon() {
    switch (this) {
      case CallkeepSignalingStatus.disconnecting:
        return PCallkeepSignalingStatus.disconnecting;
      case CallkeepSignalingStatus.disconnect:
        return PCallkeepSignalingStatus.disconnect;
      case CallkeepSignalingStatus.connecting:
        return PCallkeepSignalingStatus.connecting;
      case CallkeepSignalingStatus.connect:
        return PCallkeepSignalingStatus.connect;
      case CallkeepSignalingStatus.failure:
        return PCallkeepSignalingStatus.failure;
    }
  }
}

extension PCallkeepPushNotificationSyncStatusConverter on PCallkeepPushNotificationSyncStatus {
  CallkeepPushNotificationSyncStatus toCallkeep() {
    switch (this) {
      case PCallkeepPushNotificationSyncStatus.synchronizeCallStatus:
        return CallkeepPushNotificationSyncStatus.synchronizeCallStatus;
      case PCallkeepPushNotificationSyncStatus.releaseResources:
        return CallkeepPushNotificationSyncStatus.releaseResources;
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
    );
  }
}

extension CallkeepServiceStatusConverter on CallkeepServiceStatus {
  PCallkeepServiceStatus toPigeon() {
    return PCallkeepServiceStatus(
      lifecycle: lifecycle.toPigeon(),
    );
  }
}

extension PCallkeepConnectionStateConverter on PCallkeepConnectionState {
  CallkeepConnectionState toCallkeep() {
    switch (this) {
      case PCallkeepConnectionState.stateInitializing:
        return CallkeepConnectionState.stateInitializing;
      case PCallkeepConnectionState.stateNew:
        return CallkeepConnectionState.stateNew;
      case PCallkeepConnectionState.stateRinging:
        return CallkeepConnectionState.stateRinging;
      case PCallkeepConnectionState.stateDialing:
        return CallkeepConnectionState.stateDialing;
      case PCallkeepConnectionState.stateActive:
        return CallkeepConnectionState.stateActive;
      case PCallkeepConnectionState.stateHolding:
        return CallkeepConnectionState.stateHolding;
      case PCallkeepConnectionState.stateDisconnected:
        return CallkeepConnectionState.stateDisconnected;
      case PCallkeepConnectionState.statePullingCall:
        return CallkeepConnectionState.statePullingCall;
    }
  }
}

extension PCallkeepDisconnectCauseTypeConverter on PCallkeepDisconnectCauseType {
  CallkeepDisconnectCauseType toCallkeep() {
    switch (this) {
      case PCallkeepDisconnectCauseType.unknown:
        return CallkeepDisconnectCauseType.unknown;
      case PCallkeepDisconnectCauseType.error:
        return CallkeepDisconnectCauseType.error;
      case PCallkeepDisconnectCauseType.local:
        return CallkeepDisconnectCauseType.local;
      case PCallkeepDisconnectCauseType.remote:
        return CallkeepDisconnectCauseType.remote;
      case PCallkeepDisconnectCauseType.canceled:
        return CallkeepDisconnectCauseType.canceled;
      case PCallkeepDisconnectCauseType.missed:
        return CallkeepDisconnectCauseType.missed;
      case PCallkeepDisconnectCauseType.rejected:
        return CallkeepDisconnectCauseType.rejected;
      case PCallkeepDisconnectCauseType.busy:
        return CallkeepDisconnectCauseType.busy;
      case PCallkeepDisconnectCauseType.restricted:
        return CallkeepDisconnectCauseType.restricted;
      case PCallkeepDisconnectCauseType.other:
        return CallkeepDisconnectCauseType.other;
      case PCallkeepDisconnectCauseType.connectionManagerNotSupported:
        return CallkeepDisconnectCauseType.connectionManagerNotSupported;
      case PCallkeepDisconnectCauseType.answeredElsewhere:
        return CallkeepDisconnectCauseType.answeredElsewhere;
      case PCallkeepDisconnectCauseType.callPulled:
        return CallkeepDisconnectCauseType.callPulled;
    }
  }
}

extension PCallkeepDisconnectCauseConverter on PCallkeepDisconnectCause {
  CallkeepDisconnectCause toCallkeep() {
    return CallkeepDisconnectCause(
      type: type.toCallkeep(),
      reason: reason,
    );
  }
}

extension PCallkeepConnectionConverter on PCallkeepConnection {
  CallkeepConnection toCallkeep() {
    return CallkeepConnection(
      callId: callId,
      state: state.toCallkeep(),
      disconnectCause: disconnectCause.toCallkeep(),
    );
  }
}
