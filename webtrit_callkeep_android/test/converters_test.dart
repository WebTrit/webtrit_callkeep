// ignore_for_file: avoid_relative_lib_imports

import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_android/src/common/callkeep.pigeon.dart';
import 'package:webtrit_callkeep_android/src/common/converters.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

void main() {
  // ---------------------------------------------------------------------------
  // PHandleTypeEnumConverter
  // ---------------------------------------------------------------------------

  group('PHandleTypeEnumConverter.toCallkeep()', () {
    test('generic maps to CallkeepHandleType.generic', () {
      expect(PHandleTypeEnum.generic.toCallkeep(), CallkeepHandleType.generic);
    });

    test('number maps to CallkeepHandleType.number', () {
      expect(PHandleTypeEnum.number.toCallkeep(), CallkeepHandleType.number);
    });

    test('email maps to CallkeepHandleType.email', () {
      expect(PHandleTypeEnum.email.toCallkeep(), CallkeepHandleType.email);
    });
  });

  // ---------------------------------------------------------------------------
  // PLogTypeEnumConverter
  // ---------------------------------------------------------------------------

  group('PLogTypeEnumConverter.toCallkeep()', () {
    test('debug maps to CallkeepLogType.debug', () {
      expect(PLogTypeEnum.debug.toCallkeep(), CallkeepLogType.debug);
    });

    test('error maps to CallkeepLogType.error', () {
      expect(PLogTypeEnum.error.toCallkeep(), CallkeepLogType.error);
    });

    test('info maps to CallkeepLogType.info', () {
      expect(PLogTypeEnum.info.toCallkeep(), CallkeepLogType.info);
    });

    test('verbose maps to CallkeepLogType.verbose', () {
      expect(PLogTypeEnum.verbose.toCallkeep(), CallkeepLogType.verbose);
    });

    test('warn maps to CallkeepLogType.warn', () {
      expect(PLogTypeEnum.warn.toCallkeep(), CallkeepLogType.warn);
    });
  });

  // ---------------------------------------------------------------------------
  // PHandleConverter
  // ---------------------------------------------------------------------------

  group('PHandleConverter.toCallkeep()', () {
    test('number handle maps type and value', () {
      final result = PHandle(type: PHandleTypeEnum.number, value: '+1234567890').toCallkeep();
      expect(result.type, CallkeepHandleType.number);
      expect(result.value, '+1234567890');
    });

    test('email handle maps type and value', () {
      final result = PHandle(type: PHandleTypeEnum.email, value: 'alice@example.com').toCallkeep();
      expect(result.type, CallkeepHandleType.email);
      expect(result.value, 'alice@example.com');
    });

    test('generic handle maps type and value', () {
      final result = PHandle(type: PHandleTypeEnum.generic, value: 'sip:alice@example.com').toCallkeep();
      expect(result.type, CallkeepHandleType.generic);
      expect(result.value, 'sip:alice@example.com');
    });
  });

  // ---------------------------------------------------------------------------
  // PIncomingCallErrorEnumConverter
  // ---------------------------------------------------------------------------

  group('PIncomingCallErrorEnumConverter.toCallkeep()', () {
    test('unknown', () {
      expect(PIncomingCallErrorEnum.unknown.toCallkeep(), CallkeepIncomingCallError.unknown);
    });

    test('unentitled', () {
      expect(PIncomingCallErrorEnum.unentitled.toCallkeep(), CallkeepIncomingCallError.unentitled);
    });

    test('callIdAlreadyExists', () {
      expect(PIncomingCallErrorEnum.callIdAlreadyExists.toCallkeep(), CallkeepIncomingCallError.callIdAlreadyExists);
    });

    test('callIdAlreadyExistsAndAnswered', () {
      expect(
        PIncomingCallErrorEnum.callIdAlreadyExistsAndAnswered.toCallkeep(),
        CallkeepIncomingCallError.callIdAlreadyExistsAndAnswered,
      );
    });

    test('callIdAlreadyTerminated', () {
      expect(
        PIncomingCallErrorEnum.callIdAlreadyTerminated.toCallkeep(),
        CallkeepIncomingCallError.callIdAlreadyTerminated,
      );
    });

    test('filteredByDoNotDisturb', () {
      expect(
        PIncomingCallErrorEnum.filteredByDoNotDisturb.toCallkeep(),
        CallkeepIncomingCallError.filteredByDoNotDisturb,
      );
    });

    test('filteredByBlockList', () {
      expect(PIncomingCallErrorEnum.filteredByBlockList.toCallkeep(), CallkeepIncomingCallError.filteredByBlockList);
    });

    test('internal', () {
      expect(PIncomingCallErrorEnum.internal.toCallkeep(), CallkeepIncomingCallError.internal);
    });

    test('callRejectedBySystem', () {
      expect(PIncomingCallErrorEnum.callRejectedBySystem.toCallkeep(), CallkeepIncomingCallError.callRejectedBySystem);
    });
  });

  // ---------------------------------------------------------------------------
  // PCallRequestErrorEnumConverter
  // ---------------------------------------------------------------------------

  group('PCallRequestErrorEnumConverter.toCallkeep()', () {
    test('unknown', () {
      expect(PCallRequestErrorEnum.unknown.toCallkeep(), CallkeepCallRequestError.unknown);
    });

    test('unentitled', () {
      expect(PCallRequestErrorEnum.unentitled.toCallkeep(), CallkeepCallRequestError.unentitled);
    });

    test('unknownCallUuid', () {
      expect(PCallRequestErrorEnum.unknownCallUuid.toCallkeep(), CallkeepCallRequestError.unknownCallUuid);
    });

    test('callUuidAlreadyExists', () {
      expect(PCallRequestErrorEnum.callUuidAlreadyExists.toCallkeep(), CallkeepCallRequestError.callUuidAlreadyExists);
    });

    test('maximumCallGroupsReached', () {
      expect(
        PCallRequestErrorEnum.maximumCallGroupsReached.toCallkeep(),
        CallkeepCallRequestError.maximumCallGroupsReached,
      );
    });

    test('internal', () {
      expect(PCallRequestErrorEnum.internal.toCallkeep(), CallkeepCallRequestError.internal);
    });

    test('emergencyNumber', () {
      expect(PCallRequestErrorEnum.emergencyNumber.toCallkeep(), CallkeepCallRequestError.emergencyNumber);
    });

    test('selfManagedPhoneAccountNotRegistered', () {
      expect(
        PCallRequestErrorEnum.selfManagedPhoneAccountNotRegistered.toCallkeep(),
        CallkeepCallRequestError.selfManagedPhoneAccountNotRegistered,
      );
    });

    test('timeout', () {
      expect(PCallRequestErrorEnum.timeout.toCallkeep(), CallkeepCallRequestError.timeout);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepTypeEnumConverter (CallkeepLogType -> PLogTypeEnum)
  // ---------------------------------------------------------------------------

  group('CallkeepTypeEnumConverter.toPigeon()', () {
    test('debug maps to PLogTypeEnum.debug', () {
      expect(CallkeepLogType.debug.toPigeon(), PLogTypeEnum.debug);
    });

    test('error maps to PLogTypeEnum.error', () {
      expect(CallkeepLogType.error.toPigeon(), PLogTypeEnum.error);
    });

    test('info maps to PLogTypeEnum.info', () {
      expect(CallkeepLogType.info.toPigeon(), PLogTypeEnum.info);
    });

    test('verbose maps to PLogTypeEnum.verbose', () {
      expect(CallkeepLogType.verbose.toPigeon(), PLogTypeEnum.verbose);
    });

    test('warn maps to PLogTypeEnum.warn', () {
      expect(CallkeepLogType.warn.toPigeon(), PLogTypeEnum.warn);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepHandleTypeConverter
  // ---------------------------------------------------------------------------

  group('CallkeepHandleTypeConverter.toPigeon()', () {
    test('generic maps to PHandleTypeEnum.generic', () {
      expect(CallkeepHandleType.generic.toPigeon(), PHandleTypeEnum.generic);
    });

    test('number maps to PHandleTypeEnum.number', () {
      expect(CallkeepHandleType.number.toPigeon(), PHandleTypeEnum.number);
    });

    test('email maps to PHandleTypeEnum.email', () {
      expect(CallkeepHandleType.email.toPigeon(), PHandleTypeEnum.email);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepHandleConverter
  // ---------------------------------------------------------------------------

  group('CallkeepHandleConverter.toPigeon()', () {
    test('number handle maps type and value', () {
      const handle = CallkeepHandle(type: CallkeepHandleType.number, value: '+1234567890');
      final result = handle.toPigeon();
      expect(result.type, PHandleTypeEnum.number);
      expect(result.value, '+1234567890');
    });

    test('email handle maps type and value', () {
      const handle = CallkeepHandle(type: CallkeepHandleType.email, value: 'alice@example.com');
      final result = handle.toPigeon();
      expect(result.type, PHandleTypeEnum.email);
      expect(result.value, 'alice@example.com');
    });

    test('round-trip: CallkeepHandle -> PHandle -> CallkeepHandle preserves data', () {
      const original = CallkeepHandle(type: CallkeepHandleType.number, value: '555-1234');
      final roundTripped = original.toPigeon().toCallkeep();
      expect(roundTripped.type, original.type);
      expect(roundTripped.value, original.value);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepEndCallReasonConverter
  // ---------------------------------------------------------------------------

  group('CallkeepEndCallReasonConverter.toPigeon()', () {
    test('failed maps to PEndCallReasonEnum.failed', () {
      expect(CallkeepEndCallReason.failed.toPigeon(), PEndCallReasonEnum.failed);
    });

    test('remoteEnded maps to PEndCallReasonEnum.remoteEnded', () {
      expect(CallkeepEndCallReason.remoteEnded.toPigeon(), PEndCallReasonEnum.remoteEnded);
    });

    test('unanswered maps to PEndCallReasonEnum.unanswered', () {
      expect(CallkeepEndCallReason.unanswered.toPigeon(), PEndCallReasonEnum.unanswered);
    });

    test('answeredElsewhere maps to PEndCallReasonEnum.answeredElsewhere', () {
      expect(CallkeepEndCallReason.answeredElsewhere.toPigeon(), PEndCallReasonEnum.answeredElsewhere);
    });

    test('declinedElsewhere maps to PEndCallReasonEnum.declinedElsewhere', () {
      expect(CallkeepEndCallReason.declinedElsewhere.toPigeon(), PEndCallReasonEnum.declinedElsewhere);
    });

    test('missed maps to PEndCallReasonEnum.missed', () {
      expect(CallkeepEndCallReason.missed.toPigeon(), PEndCallReasonEnum.missed);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepOptionsConverter
  // ---------------------------------------------------------------------------

  group('CallkeepOptionsConverter.toPigeon()', () {
    const options = CallkeepOptions(
      ios: CallkeepIOSOptions(
        localizedName: 'TestApp',
        maximumCallGroups: 2,
        maximumCallsPerCallGroup: 1,
        supportedHandleTypes: {CallkeepHandleType.number, CallkeepHandleType.email},
      ),
      android: CallkeepAndroidOptions(
        ringtoneSound: 'ring.mp3',
        ringbackSound: 'ringback.mp3',
        incomingCallFullScreen: true,
      ),
    );

    test('ios field maps to PIOSOptions', () {
      final result = options.toPigeon();
      expect(result.ios, isA<PIOSOptions>());
      expect(result.ios.localizedName, 'TestApp');
      expect(result.ios.maximumCallGroups, 2);
      expect(result.ios.maximumCallsPerCallGroup, 1);
    });

    test('android field maps to PAndroidOptions', () {
      final result = options.toPigeon();
      expect(result.android, isA<PAndroidOptions>());
      expect(result.android.ringtoneSound, 'ring.mp3');
      expect(result.android.ringbackSound, 'ringback.mp3');
      expect(result.android.incomingCallFullScreen, true);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepIOSOptionsConverter
  // ---------------------------------------------------------------------------

  group('CallkeepIOSOptionsConverter.toPigeon()', () {
    test('localizedName is forwarded', () {
      const ios = CallkeepIOSOptions(
        localizedName: 'MyApp',
        maximumCallGroups: 1,
        maximumCallsPerCallGroup: 1,
        supportedHandleTypes: {CallkeepHandleType.number},
      );
      expect(ios.toPigeon().localizedName, 'MyApp');
    });

    test('supportedHandleTypes: only number sets supportsHandleTypePhoneNumber=true, others false', () {
      const ios = CallkeepIOSOptions(
        localizedName: 'App',
        maximumCallGroups: 1,
        maximumCallsPerCallGroup: 1,
        supportedHandleTypes: {CallkeepHandleType.number},
      );
      final result = ios.toPigeon();
      expect(result.supportsHandleTypePhoneNumber, true);
      expect(result.supportsHandleTypeGeneric, false);
      expect(result.supportsHandleTypeEmailAddress, false);
    });

    test('supportedHandleTypes: all three types set all flags true', () {
      const ios = CallkeepIOSOptions(
        localizedName: 'App',
        maximumCallGroups: 1,
        maximumCallsPerCallGroup: 1,
        supportedHandleTypes: {CallkeepHandleType.number, CallkeepHandleType.email, CallkeepHandleType.generic},
      );
      final result = ios.toPigeon();
      expect(result.supportsHandleTypePhoneNumber, true);
      expect(result.supportsHandleTypeEmailAddress, true);
      expect(result.supportsHandleTypeGeneric, true);
    });

    test('maximumCallGroups and maximumCallsPerCallGroup are forwarded', () {
      const ios = CallkeepIOSOptions(
        localizedName: 'App',
        maximumCallGroups: 3,
        maximumCallsPerCallGroup: 5,
        supportedHandleTypes: {},
      );
      final result = ios.toPigeon();
      expect(result.maximumCallGroups, 3);
      expect(result.maximumCallsPerCallGroup, 5);
    });
  });

  // ---------------------------------------------------------------------------
  // PSpecialPermissionStatusTypeEnumConverter
  // ---------------------------------------------------------------------------

  group('PSpecialPermissionStatusTypeEnumConverter.toCallkeep()', () {
    test('denied maps to CallkeepSpecialPermissionStatus.denied', () {
      expect(PSpecialPermissionStatusTypeEnum.denied.toCallkeep(), CallkeepSpecialPermissionStatus.denied);
    });

    test('granted maps to CallkeepSpecialPermissionStatus.granted', () {
      expect(PSpecialPermissionStatusTypeEnum.granted.toCallkeep(), CallkeepSpecialPermissionStatus.granted);
    });

    test('unknown maps to CallkeepSpecialPermissionStatus.unknown', () {
      expect(PSpecialPermissionStatusTypeEnum.unknown.toCallkeep(), CallkeepSpecialPermissionStatus.unknown);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepPermissionConverter
  // ---------------------------------------------------------------------------

  group('CallkeepPermissionConverter.toPigeon()', () {
    test('readPhoneState maps to PCallkeepPermission.readPhoneState', () {
      expect(CallkeepPermission.readPhoneState.toPigeon(), PCallkeepPermission.readPhoneState);
    });

    test('readPhoneNumbers maps to PCallkeepPermission.readPhoneNumbers', () {
      expect(CallkeepPermission.readPhoneNumbers.toPigeon(), PCallkeepPermission.readPhoneNumbers);
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepPermissionConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepPermissionConverter.toCallkeep()', () {
    test('readPhoneState maps to CallkeepPermission.readPhoneState', () {
      expect(PCallkeepPermission.readPhoneState.toCallkeep(), CallkeepPermission.readPhoneState);
    });

    test('readPhoneNumbers maps to CallkeepPermission.readPhoneNumbers', () {
      expect(PCallkeepPermission.readPhoneNumbers.toCallkeep(), CallkeepPermission.readPhoneNumbers);
    });

    test('round-trip: CallkeepPermission -> PCallkeepPermission -> CallkeepPermission', () {
      for (final perm in CallkeepPermission.values) {
        expect(perm.toPigeon().toCallkeep(), perm);
      }
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepAndroidBatteryModeConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepAndroidBatteryModeConverter.toCallkeep()', () {
    test('unrestricted maps to CallkeepAndroidBatteryMode.unrestricted', () {
      expect(PCallkeepAndroidBatteryMode.unrestricted.toCallkeep(), CallkeepAndroidBatteryMode.unrestricted);
    });

    test('optimized maps to CallkeepAndroidBatteryMode.optimized', () {
      expect(PCallkeepAndroidBatteryMode.optimized.toCallkeep(), CallkeepAndroidBatteryMode.optimized);
    });

    test('restricted maps to CallkeepAndroidBatteryMode.restricted', () {
      expect(PCallkeepAndroidBatteryMode.restricted.toCallkeep(), CallkeepAndroidBatteryMode.restricted);
    });

    test('unknown maps to CallkeepAndroidBatteryMode.unknown', () {
      expect(PCallkeepAndroidBatteryMode.unknown.toCallkeep(), CallkeepAndroidBatteryMode.unknown);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepLifecycleTypeConverter (CallkeepLifecycleEvent -> PCallkeepLifecycleEvent)
  // ---------------------------------------------------------------------------

  group('CallkeepLifecycleTypeConverter.toPigeon()', () {
    test('onCreate', () => expect(CallkeepLifecycleEvent.onCreate.toPigeon(), PCallkeepLifecycleEvent.onCreate));
    test('onStart', () => expect(CallkeepLifecycleEvent.onStart.toPigeon(), PCallkeepLifecycleEvent.onStart));
    test('onResume', () => expect(CallkeepLifecycleEvent.onResume.toPigeon(), PCallkeepLifecycleEvent.onResume));
    test('onPause', () => expect(CallkeepLifecycleEvent.onPause.toPigeon(), PCallkeepLifecycleEvent.onPause));
    test('onStop', () => expect(CallkeepLifecycleEvent.onStop.toPigeon(), PCallkeepLifecycleEvent.onStop));
    test('onDestroy', () => expect(CallkeepLifecycleEvent.onDestroy.toPigeon(), PCallkeepLifecycleEvent.onDestroy));
    test('onAny', () => expect(CallkeepLifecycleEvent.onAny.toPigeon(), PCallkeepLifecycleEvent.onAny));
  });

  // ---------------------------------------------------------------------------
  // PCallkeepLifecycleTypeConverter (PCallkeepLifecycleEvent -> CallkeepLifecycleEvent)
  // ---------------------------------------------------------------------------

  group('PCallkeepLifecycleTypeConverter.toCallkeep()', () {
    test('onCreate', () => expect(PCallkeepLifecycleEvent.onCreate.toCallkeep(), CallkeepLifecycleEvent.onCreate));
    test('onStart', () => expect(PCallkeepLifecycleEvent.onStart.toCallkeep(), CallkeepLifecycleEvent.onStart));
    test('onResume', () => expect(PCallkeepLifecycleEvent.onResume.toCallkeep(), CallkeepLifecycleEvent.onResume));
    test('onPause', () => expect(PCallkeepLifecycleEvent.onPause.toCallkeep(), CallkeepLifecycleEvent.onPause));
    test('onStop', () => expect(PCallkeepLifecycleEvent.onStop.toCallkeep(), CallkeepLifecycleEvent.onStop));
    test('onDestroy', () => expect(PCallkeepLifecycleEvent.onDestroy.toCallkeep(), CallkeepLifecycleEvent.onDestroy));
    test('onAny', () => expect(PCallkeepLifecycleEvent.onAny.toCallkeep(), CallkeepLifecycleEvent.onAny));

    test('round-trip for all values', () {
      for (final event in CallkeepLifecycleEvent.values) {
        expect(event.toPigeon().toCallkeep(), event);
      }
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepSignalingStatusConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepSignalingStatusConverter.toCallkeep()', () {
    test('disconnecting', () {
      expect(PCallkeepSignalingStatus.disconnecting.toCallkeep(), CallkeepSignalingStatus.disconnecting);
    });

    test('disconnect', () {
      expect(PCallkeepSignalingStatus.disconnect.toCallkeep(), CallkeepSignalingStatus.disconnect);
    });

    test('connecting', () {
      expect(PCallkeepSignalingStatus.connecting.toCallkeep(), CallkeepSignalingStatus.connecting);
    });

    test('connect', () {
      expect(PCallkeepSignalingStatus.connect.toCallkeep(), CallkeepSignalingStatus.connect);
    });

    test('failure', () {
      expect(PCallkeepSignalingStatus.failure.toCallkeep(), CallkeepSignalingStatus.failure);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepSignalingStatusConverter
  // ---------------------------------------------------------------------------

  group('CallkeepSignalingStatusConverter.toPigeon()', () {
    test('disconnecting', () {
      expect(CallkeepSignalingStatus.disconnecting.toPigeon(), PCallkeepSignalingStatus.disconnecting);
    });

    test('disconnect', () {
      expect(CallkeepSignalingStatus.disconnect.toPigeon(), PCallkeepSignalingStatus.disconnect);
    });

    test('connecting', () {
      expect(CallkeepSignalingStatus.connecting.toPigeon(), PCallkeepSignalingStatus.connecting);
    });

    test('connect', () {
      expect(CallkeepSignalingStatus.connect.toPigeon(), PCallkeepSignalingStatus.connect);
    });

    test('failure', () {
      expect(CallkeepSignalingStatus.failure.toPigeon(), PCallkeepSignalingStatus.failure);
    });

    test('round-trip for all values', () {
      for (final status in CallkeepSignalingStatus.values) {
        expect(status.toPigeon().toCallkeep(), status);
      }
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepPushNotificationSyncStatusConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepPushNotificationSyncStatusConverter.toCallkeep()', () {
    test('synchronizeCallStatus', () {
      expect(
        PCallkeepPushNotificationSyncStatus.synchronizeCallStatus.toCallkeep(),
        CallkeepPushNotificationSyncStatus.synchronizeCallStatus,
      );
    });

    test('releaseResources', () {
      expect(
        PCallkeepPushNotificationSyncStatus.releaseResources.toCallkeep(),
        CallkeepPushNotificationSyncStatus.releaseResources,
      );
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepIncomingCallDataConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepIncomingCallDataConverter.toCallkeep()', () {
    test('all fields populated map correctly', () {
      final data = PCallkeepIncomingCallData(
        callId: 'call-42',
        handle: PHandle(type: PHandleTypeEnum.number, value: '555-0100'),
        displayName: 'Alice',
        hasVideo: true,
      );
      final result = data.toCallkeep();
      expect(result.callId, 'call-42');
      expect(result.handle?.type, CallkeepHandleType.number);
      expect(result.handle?.value, '555-0100');
      expect(result.displayName, 'Alice');
      expect(result.hasVideo, true);
    });

    test('null handle stays null', () {
      final data = PCallkeepIncomingCallData(callId: 'call-43', handle: null, displayName: null, hasVideo: false);
      final result = data.toCallkeep();
      expect(result.callId, 'call-43');
      expect(result.handle, isNull);
      expect(result.displayName, isNull);
      expect(result.hasVideo, false);
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepServiceStatusConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepServiceStatusConverter.toCallkeep()', () {
    test('lifecycleEvent is converted', () {
      final status = PCallkeepServiceStatus(lifecycleEvent: PCallkeepLifecycleEvent.onResume);
      final result = status.toCallkeep();
      expect(result.lifecycleEvent, CallkeepLifecycleEvent.onResume);
    });

    test('null mainSignalingStatus stays null', () {
      final status = PCallkeepServiceStatus(lifecycleEvent: PCallkeepLifecycleEvent.onStart, mainSignalingStatus: null);
      final result = status.toCallkeep();
      expect(result.mainSignalingStatus, isNull);
    });

    test('non-null mainSignalingStatus is converted', () {
      final status = PCallkeepServiceStatus(
        lifecycleEvent: PCallkeepLifecycleEvent.onResume,
        mainSignalingStatus: PCallkeepSignalingStatus.connect,
      );
      final result = status.toCallkeep();
      expect(result.mainSignalingStatus, CallkeepSignalingStatus.connect);
    });
  });

  // ---------------------------------------------------------------------------
  // CallkeepServiceStatusConverter
  // ---------------------------------------------------------------------------

  group('CallkeepServiceStatusConverter.toPigeon()', () {
    test('lifecycleEvent is forwarded', () {
      final status = CallkeepServiceStatus(lifecycleEvent: CallkeepLifecycleEvent.onDestroy);
      final result = status.toPigeon();
      expect(result.lifecycleEvent, PCallkeepLifecycleEvent.onDestroy);
    });

    test('mainSignalingStatus is intentionally dropped (not serialized to Pigeon)', () {
      final status = CallkeepServiceStatus(
        lifecycleEvent: CallkeepLifecycleEvent.onResume,
        mainSignalingStatus: CallkeepSignalingStatus.connect,
      );
      final result = status.toPigeon();
      expect(result.mainSignalingStatus, isNull);
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepConnectionStateConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepConnectionStateConverter.toCallkeep()', () {
    test('stateInitializing', () {
      expect(PCallkeepConnectionState.stateInitializing.toCallkeep(), CallkeepConnectionState.stateInitializing);
    });

    test('stateNew', () {
      expect(PCallkeepConnectionState.stateNew.toCallkeep(), CallkeepConnectionState.stateNew);
    });

    test('stateRinging', () {
      expect(PCallkeepConnectionState.stateRinging.toCallkeep(), CallkeepConnectionState.stateRinging);
    });

    test('stateDialing', () {
      expect(PCallkeepConnectionState.stateDialing.toCallkeep(), CallkeepConnectionState.stateDialing);
    });

    test('stateActive', () {
      expect(PCallkeepConnectionState.stateActive.toCallkeep(), CallkeepConnectionState.stateActive);
    });

    test('stateHolding', () {
      expect(PCallkeepConnectionState.stateHolding.toCallkeep(), CallkeepConnectionState.stateHolding);
    });

    test('stateDisconnected', () {
      expect(PCallkeepConnectionState.stateDisconnected.toCallkeep(), CallkeepConnectionState.stateDisconnected);
    });

    test('statePullingCall', () {
      expect(PCallkeepConnectionState.statePullingCall.toCallkeep(), CallkeepConnectionState.statePullingCall);
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepDisconnectCauseTypeConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepDisconnectCauseTypeConverter.toCallkeep()', () {
    test(
      'unknown',
      () => expect(PCallkeepDisconnectCauseType.unknown.toCallkeep(), CallkeepDisconnectCauseType.unknown),
    );
    test('error', () => expect(PCallkeepDisconnectCauseType.error.toCallkeep(), CallkeepDisconnectCauseType.error));
    test('local', () => expect(PCallkeepDisconnectCauseType.local.toCallkeep(), CallkeepDisconnectCauseType.local));
    test('remote', () => expect(PCallkeepDisconnectCauseType.remote.toCallkeep(), CallkeepDisconnectCauseType.remote));
    test('canceled', () {
      expect(PCallkeepDisconnectCauseType.canceled.toCallkeep(), CallkeepDisconnectCauseType.canceled);
    });
    test('missed', () => expect(PCallkeepDisconnectCauseType.missed.toCallkeep(), CallkeepDisconnectCauseType.missed));
    test('rejected', () {
      expect(PCallkeepDisconnectCauseType.rejected.toCallkeep(), CallkeepDisconnectCauseType.rejected);
    });
    test('busy', () => expect(PCallkeepDisconnectCauseType.busy.toCallkeep(), CallkeepDisconnectCauseType.busy));
    test('restricted', () {
      expect(PCallkeepDisconnectCauseType.restricted.toCallkeep(), CallkeepDisconnectCauseType.restricted);
    });
    test('other', () => expect(PCallkeepDisconnectCauseType.other.toCallkeep(), CallkeepDisconnectCauseType.other));
    test('connectionManagerNotSupported', () {
      expect(
        PCallkeepDisconnectCauseType.connectionManagerNotSupported.toCallkeep(),
        CallkeepDisconnectCauseType.connectionManagerNotSupported,
      );
    });
    test('answeredElsewhere', () {
      expect(
        PCallkeepDisconnectCauseType.answeredElsewhere.toCallkeep(),
        CallkeepDisconnectCauseType.answeredElsewhere,
      );
    });
    test('callPulled', () {
      expect(PCallkeepDisconnectCauseType.callPulled.toCallkeep(), CallkeepDisconnectCauseType.callPulled);
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepDisconnectCauseConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepDisconnectCauseConverter.toCallkeep()', () {
    test('type and reason are mapped', () {
      final cause = PCallkeepDisconnectCause(type: PCallkeepDisconnectCauseType.local, reason: 'user action');
      final result = cause.toCallkeep();
      expect(result.type, CallkeepDisconnectCauseType.local);
      expect(result.reason, 'user action');
    });

    test('null reason stays null', () {
      final cause = PCallkeepDisconnectCause(type: PCallkeepDisconnectCauseType.remote, reason: null);
      final result = cause.toCallkeep();
      expect(result.type, CallkeepDisconnectCauseType.remote);
      expect(result.reason, isNull);
    });
  });

  // ---------------------------------------------------------------------------
  // PCallkeepConnectionConverter
  // ---------------------------------------------------------------------------

  group('PCallkeepConnectionConverter.toCallkeep()', () {
    test('callId, state and disconnectCause are all mapped', () {
      final connection = PCallkeepConnection(
        callId: 'abc-123',
        state: PCallkeepConnectionState.stateActive,
        disconnectCause: PCallkeepDisconnectCause(type: PCallkeepDisconnectCauseType.unknown, reason: null),
      );
      final result = connection.toCallkeep();
      expect(result.callId, 'abc-123');
      expect(result.state, CallkeepConnectionState.stateActive);
      expect(result.disconnectCause!.type, CallkeepDisconnectCauseType.unknown);
      expect(result.disconnectCause!.reason, isNull);
    });

    test('disconnected state with reason', () {
      final connection = PCallkeepConnection(
        callId: 'xyz',
        state: PCallkeepConnectionState.stateDisconnected,
        disconnectCause: PCallkeepDisconnectCause(type: PCallkeepDisconnectCauseType.local, reason: 'hung up'),
      );
      final result = connection.toCallkeep();
      expect(result.state, CallkeepConnectionState.stateDisconnected);
      expect(result.disconnectCause!.type, CallkeepDisconnectCauseType.local);
      expect(result.disconnectCause!.reason, 'hung up');
    });
  });
}
