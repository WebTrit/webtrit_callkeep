import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_android/src/common/callkeep.pigeon.dart';
import 'package:webtrit_callkeep_android/webtrit_callkeep_android.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

const _prefix = 'dev.flutter.pigeon.webtrit_callkeep_android';
final _codec = PHostApi.pigeonChannelCodec;

void _mockVoid(String channel) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
    channel,
    (message) async => const StandardMessageCodec().encodeMessage([null]),
  );
}

void _mockValue(String channel, Object? value) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
    channel,
    (message) async => const StandardMessageCodec().encodeMessage([value]),
  );
}

void _mockPigeon(String channel, Object? pigeonValue) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
    channel,
    (message) async => _codec.encodeMessage([pigeonValue]),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    WebtritCallkeepAndroid.registerWith();
    _mockVoid('$_prefix.PHostApi.setUp');
    _mockVoid('$_prefix.PHostApi.tearDown');
    _mockVoid('$_prefix.PHostApi.onDelegateSet');
    await WebtritCallkeepPlatform.instance.setUp(
      const CallkeepOptions(
        ios: CallkeepIOSOptions(
          localizedName: 'Test',
          maximumCallGroups: 1,
          maximumCallsPerCallGroup: 1,
          supportedHandleTypes: {CallkeepHandleType.number},
        ),
        android: CallkeepAndroidOptions(),
      ),
    );
  });

  tearDown(() async {
    await WebtritCallkeepPlatform.instance.tearDown();
  });

  // ---------------------------------------------------------------------------
  // PHostApi
  // ---------------------------------------------------------------------------

  group('WebtritCallkeepAndroid — PHostApi', () {
    test('isSetUp returns true', () async {
      _mockValue('$_prefix.PHostApi.isSetUp', true);
      expect(await WebtritCallkeepPlatform.instance.isSetUp(), true);
    });

    test('isSetUp returns false', () async {
      _mockValue('$_prefix.PHostApi.isSetUp', false);
      expect(await WebtritCallkeepPlatform.instance.isSetUp(), false);
    });

    test('reportNewIncomingCall returns null when channel replies null', () async {
      _mockValue('$_prefix.PHostApi.reportNewIncomingCall', null);
      final result = await WebtritCallkeepPlatform.instance.reportNewIncomingCall(
        'call-1',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        'Alice',
        false,
      );
      expect(result, isNull);
    });

    test('reportNewIncomingCall returns CallkeepIncomingCallError.unknown', () async {
      _mockPigeon('$_prefix.PHostApi.reportNewIncomingCall', PIncomingCallError(value: PIncomingCallErrorEnum.unknown));
      final result = await WebtritCallkeepPlatform.instance.reportNewIncomingCall(
        'call-2',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        null,
        false,
      );
      expect(result, CallkeepIncomingCallError.unknown);
    });

    test('reportNewIncomingCall returns CallkeepIncomingCallError.callRejectedBySystem', () async {
      _mockPigeon(
        '$_prefix.PHostApi.reportNewIncomingCall',
        PIncomingCallError(value: PIncomingCallErrorEnum.callRejectedBySystem),
      );
      final result = await WebtritCallkeepPlatform.instance.reportNewIncomingCall(
        'call-3',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        null,
        false,
      );
      expect(result, CallkeepIncomingCallError.callRejectedBySystem);
    });

    test('reportNewIncomingCall returns CallkeepIncomingCallError.callIdAlreadyExists', () async {
      _mockPigeon(
        '$_prefix.PHostApi.reportNewIncomingCall',
        PIncomingCallError(value: PIncomingCallErrorEnum.callIdAlreadyExists),
      );
      final result = await WebtritCallkeepPlatform.instance.reportNewIncomingCall(
        'call-4',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        null,
        false,
      );
      expect(result, CallkeepIncomingCallError.callIdAlreadyExists);
    });

    test('reportConnectingOutgoingCall completes', () async {
      _mockVoid('$_prefix.PHostApi.reportConnectingOutgoingCall');
      await expectLater(WebtritCallkeepPlatform.instance.reportConnectingOutgoingCall('call-1'), completes);
    });

    test('reportConnectedOutgoingCall completes', () async {
      _mockVoid('$_prefix.PHostApi.reportConnectedOutgoingCall');
      await expectLater(WebtritCallkeepPlatform.instance.reportConnectedOutgoingCall('call-1'), completes);
    });

    test('reportUpdateCall completes', () async {
      _mockVoid('$_prefix.PHostApi.reportUpdateCall');
      await expectLater(WebtritCallkeepPlatform.instance.reportUpdateCall('call-1', null, null, null, null), completes);
    });

    test('reportEndCall completes', () async {
      _mockVoid('$_prefix.PHostApi.reportEndCall');
      await expectLater(
        WebtritCallkeepPlatform.instance.reportEndCall('call-1', 'Alice', CallkeepEndCallReason.remoteEnded),
        completes,
      );
    });

    test('startCall returns null when no error', () async {
      _mockValue('$_prefix.PHostApi.startCall', null);
      final result = await WebtritCallkeepPlatform.instance.startCall(
        'call-1',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        null,
        false,
        false,
      );
      expect(result, isNull);
    });

    test('startCall returns CallkeepCallRequestError.unknown', () async {
      _mockPigeon('$_prefix.PHostApi.startCall', PCallRequestError(value: PCallRequestErrorEnum.unknown));
      final result = await WebtritCallkeepPlatform.instance.startCall(
        'call-1',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        null,
        false,
        false,
      );
      expect(result, CallkeepCallRequestError.unknown);
    });

    test('startCall returns CallkeepCallRequestError.selfManagedPhoneAccountNotRegistered', () async {
      _mockPigeon(
        '$_prefix.PHostApi.startCall',
        PCallRequestError(value: PCallRequestErrorEnum.selfManagedPhoneAccountNotRegistered),
      );
      final result = await WebtritCallkeepPlatform.instance.startCall(
        'call-1',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        null,
        false,
        false,
      );
      expect(result, CallkeepCallRequestError.selfManagedPhoneAccountNotRegistered);
    });

    test('startCall returns CallkeepCallRequestError.timeout', () async {
      _mockPigeon('$_prefix.PHostApi.startCall', PCallRequestError(value: PCallRequestErrorEnum.timeout));
      final result = await WebtritCallkeepPlatform.instance.startCall(
        'call-1',
        const CallkeepHandle(type: CallkeepHandleType.number, value: '555'),
        null,
        false,
        false,
      );
      expect(result, CallkeepCallRequestError.timeout);
    });

    test('answerCall returns null when no error', () async {
      _mockValue('$_prefix.PHostApi.answerCall', null);
      expect(await WebtritCallkeepPlatform.instance.answerCall('call-1'), isNull);
    });

    test('answerCall returns CallkeepCallRequestError.unknownCallUuid', () async {
      _mockPigeon('$_prefix.PHostApi.answerCall', PCallRequestError(value: PCallRequestErrorEnum.unknownCallUuid));
      expect(await WebtritCallkeepPlatform.instance.answerCall('call-1'), CallkeepCallRequestError.unknownCallUuid);
    });

    test('endCall returns null when successful', () async {
      _mockValue('$_prefix.PHostApi.endCall', null);
      expect(await WebtritCallkeepPlatform.instance.endCall('call-1'), isNull);
    });

    test('endCall returns CallkeepCallRequestError.internal', () async {
      _mockPigeon('$_prefix.PHostApi.endCall', PCallRequestError(value: PCallRequestErrorEnum.internal));
      expect(await WebtritCallkeepPlatform.instance.endCall('call-1'), CallkeepCallRequestError.internal);
    });

    test('setHeld returns null when successful', () async {
      _mockValue('$_prefix.PHostApi.setHeld', null);
      expect(await WebtritCallkeepPlatform.instance.setHeld('call-1', true), isNull);
    });

    test('setMuted returns null when successful', () async {
      _mockValue('$_prefix.PHostApi.setMuted', null);
      expect(await WebtritCallkeepPlatform.instance.setMuted('call-1', true), isNull);
    });

    test('setSpeaker returns null when successful', () async {
      _mockValue('$_prefix.PHostApi.setSpeaker', null);
      expect(await WebtritCallkeepPlatform.instance.setSpeaker('call-1', false), isNull);
    });

    test('sendDTMF returns null when successful', () async {
      _mockValue('$_prefix.PHostApi.sendDTMF', null);
      expect(await WebtritCallkeepPlatform.instance.sendDTMF('call-1', '5'), isNull);
    });

    test('setAudioDevice returns null when successful', () async {
      _mockValue('$_prefix.PHostApi.setAudioDevice', null);
      final result = await WebtritCallkeepPlatform.instance.setAudioDevice(
        'call-1',
        CallkeepAudioDevice(type: CallkeepAudioDeviceType.bluetooth, id: 'bt-1', name: 'Headset'),
      );
      expect(result, isNull);
    });
  });

  // ---------------------------------------------------------------------------
  // PHostPermissionsApi
  // ---------------------------------------------------------------------------

  group('WebtritCallkeepAndroid — PHostPermissionsApi', () {
    test('getFullScreenIntentPermissionStatus returns granted', () async {
      _mockPigeon(
        '$_prefix.PHostPermissionsApi.getFullScreenIntentPermissionStatus',
        PSpecialPermissionStatusTypeEnum.granted,
      );
      expect(
        await WebtritCallkeepPlatform.instance.getFullScreenIntentPermissionStatus(),
        CallkeepSpecialPermissionStatus.granted,
      );
    });

    test('getFullScreenIntentPermissionStatus returns denied', () async {
      _mockPigeon(
        '$_prefix.PHostPermissionsApi.getFullScreenIntentPermissionStatus',
        PSpecialPermissionStatusTypeEnum.denied,
      );
      expect(
        await WebtritCallkeepPlatform.instance.getFullScreenIntentPermissionStatus(),
        CallkeepSpecialPermissionStatus.denied,
      );
    });

    test('getFullScreenIntentPermissionStatus returns unknown', () async {
      _mockPigeon(
        '$_prefix.PHostPermissionsApi.getFullScreenIntentPermissionStatus',
        PSpecialPermissionStatusTypeEnum.unknown,
      );
      expect(
        await WebtritCallkeepPlatform.instance.getFullScreenIntentPermissionStatus(),
        CallkeepSpecialPermissionStatus.unknown,
      );
    });

    test('openFullScreenIntentSettings completes', () async {
      _mockVoid('$_prefix.PHostPermissionsApi.openFullScreenIntentSettings');
      await expectLater(WebtritCallkeepPlatform.instance.openFullScreenIntentSettings(), completes);
    });

    test('openSettings completes', () async {
      _mockVoid('$_prefix.PHostPermissionsApi.openSettings');
      await expectLater(WebtritCallkeepPlatform.instance.openSettings(), completes);
    });

    test('getBatteryMode returns unrestricted', () async {
      _mockPigeon('$_prefix.PHostPermissionsApi.getBatteryMode', PCallkeepAndroidBatteryMode.unrestricted);
      expect(await WebtritCallkeepPlatform.instance.getBatteryMode(), CallkeepAndroidBatteryMode.unrestricted);
    });

    test('getBatteryMode returns optimized', () async {
      _mockPigeon('$_prefix.PHostPermissionsApi.getBatteryMode', PCallkeepAndroidBatteryMode.optimized);
      expect(await WebtritCallkeepPlatform.instance.getBatteryMode(), CallkeepAndroidBatteryMode.optimized);
    });

    test('getBatteryMode returns restricted', () async {
      _mockPigeon('$_prefix.PHostPermissionsApi.getBatteryMode', PCallkeepAndroidBatteryMode.restricted);
      expect(await WebtritCallkeepPlatform.instance.getBatteryMode(), CallkeepAndroidBatteryMode.restricted);
    });

    test('requestPermissions maps readPhoneState to granted', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
        '$_prefix.PHostPermissionsApi.requestPermissions',
        (message) async => _codec.encodeMessage([
          [
            PPermissionResult(
              permission: PCallkeepPermission.readPhoneState,
              status: PSpecialPermissionStatusTypeEnum.granted,
            ),
          ],
        ]),
      );
      final results = await WebtritCallkeepPlatform.instance.requestPermissions([CallkeepPermission.readPhoneState]);
      expect(results[CallkeepPermission.readPhoneState], CallkeepSpecialPermissionStatus.granted);
    });

    test('requestPermissions with two permissions returns both mapped', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
        '$_prefix.PHostPermissionsApi.requestPermissions',
        (message) async => _codec.encodeMessage([
          [
            PPermissionResult(
              permission: PCallkeepPermission.readPhoneState,
              status: PSpecialPermissionStatusTypeEnum.granted,
            ),
            PPermissionResult(
              permission: PCallkeepPermission.readPhoneNumbers,
              status: PSpecialPermissionStatusTypeEnum.denied,
            ),
          ],
        ]),
      );
      final results = await WebtritCallkeepPlatform.instance.requestPermissions([
        CallkeepPermission.readPhoneState,
        CallkeepPermission.readPhoneNumbers,
      ]);
      expect(results[CallkeepPermission.readPhoneState], CallkeepSpecialPermissionStatus.granted);
      expect(results[CallkeepPermission.readPhoneNumbers], CallkeepSpecialPermissionStatus.denied);
    });

    test('checkPermissionsStatus maps results correctly', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
        '$_prefix.PHostPermissionsApi.checkPermissionsStatus',
        (message) async => _codec.encodeMessage([
          [
            PPermissionResult(
              permission: PCallkeepPermission.readPhoneNumbers,
              status: PSpecialPermissionStatusTypeEnum.unknown,
            ),
          ],
        ]),
      );
      final results = await WebtritCallkeepPlatform.instance.checkPermissionsStatus([
        CallkeepPermission.readPhoneNumbers,
      ]);
      expect(results[CallkeepPermission.readPhoneNumbers], CallkeepSpecialPermissionStatus.unknown);
    });
  });

  // ---------------------------------------------------------------------------
  // PHostConnectionsApi
  // ---------------------------------------------------------------------------

  group('WebtritCallkeepAndroid — PHostConnectionsApi', () {
    test('getConnection returns null when channel replies null', () async {
      _mockValue('$_prefix.PHostConnectionsApi.getConnection', null);
      expect(await WebtritCallkeepPlatform.instance.getConnection('call-1'), isNull);
    });

    test('getConnection returns CallkeepConnection', () async {
      _mockPigeon(
        '$_prefix.PHostConnectionsApi.getConnection',
        PCallkeepConnection(
          callId: 'call-1',
          state: PCallkeepConnectionState.stateActive,
          disconnectCause: PCallkeepDisconnectCause(type: PCallkeepDisconnectCauseType.unknown, reason: null),
        ),
      );
      final result = await WebtritCallkeepPlatform.instance.getConnection('call-1');
      expect(result, isNotNull);
      expect(result!.callId, 'call-1');
      expect(result.state, CallkeepConnectionState.stateActive);
    });

    test('getConnections returns empty list', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
        '$_prefix.PHostConnectionsApi.getConnections',
        (message) async => _codec.encodeMessage([<PCallkeepConnection>[]]),
      );
      final result = await WebtritCallkeepPlatform.instance.getConnections();
      expect(result, isEmpty);
    });

    test('getConnections returns two connections', () async {
      final conn1 = PCallkeepConnection(
        callId: 'c1',
        state: PCallkeepConnectionState.stateRinging,
        disconnectCause: PCallkeepDisconnectCause(type: PCallkeepDisconnectCauseType.unknown, reason: null),
      );
      final conn2 = PCallkeepConnection(
        callId: 'c2',
        state: PCallkeepConnectionState.stateActive,
        disconnectCause: PCallkeepDisconnectCause(type: PCallkeepDisconnectCauseType.unknown, reason: null),
      );
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
        '$_prefix.PHostConnectionsApi.getConnections',
        (message) async => _codec.encodeMessage([
          [conn1, conn2],
        ]),
      );
      final result = await WebtritCallkeepPlatform.instance.getConnections();
      expect(result.length, 2);
      expect(result[0].callId, 'c1');
      expect(result[0].state, CallkeepConnectionState.stateRinging);
      expect(result[1].callId, 'c2');
      expect(result[1].state, CallkeepConnectionState.stateActive);
    });

    test('cleanConnections completes', () async {
      _mockVoid('$_prefix.PHostConnectionsApi.cleanConnections');
      await expectLater(WebtritCallkeepPlatform.instance.cleanConnections(), completes);
    });

    test('updateActivitySignalingStatus with connect completes', () async {
      _mockVoid('$_prefix.PHostConnectionsApi.updateActivitySignalingStatus');
      await expectLater(
        WebtritCallkeepPlatform.instance.updateActivitySignalingStatus(CallkeepSignalingStatus.connect),
        completes,
      );
    });
  });

  // ---------------------------------------------------------------------------
  // PHostSoundApi
  // ---------------------------------------------------------------------------

  group('WebtritCallkeepAndroid — PHostSoundApi', () {
    test('playRingbackSound completes', () async {
      _mockVoid('$_prefix.PHostSoundApi.playRingbackSound');
      await expectLater(WebtritCallkeepPlatform.instance.playRingbackSound(), completes);
    });

    test('stopRingbackSound completes', () async {
      _mockVoid('$_prefix.PHostSoundApi.stopRingbackSound');
      await expectLater(WebtritCallkeepPlatform.instance.stopRingbackSound(), completes);
    });
  });

  // ---------------------------------------------------------------------------
  // PHostActivityControlApi
  // ---------------------------------------------------------------------------

  group('WebtritCallkeepAndroid — PHostActivityControlApi', () {
    test('showOverLockscreen(true) completes', () async {
      _mockVoid('$_prefix.PHostActivityControlApi.showOverLockscreen');
      await expectLater(WebtritCallkeepPlatform.instance.showOverLockscreen(true), completes);
    });

    test('showOverLockscreen defaults to true', () async {
      _mockVoid('$_prefix.PHostActivityControlApi.showOverLockscreen');
      await expectLater(WebtritCallkeepPlatform.instance.showOverLockscreen(), completes);
    });

    test('wakeScreenOnShow completes', () async {
      _mockVoid('$_prefix.PHostActivityControlApi.wakeScreenOnShow');
      await expectLater(WebtritCallkeepPlatform.instance.wakeScreenOnShow(), completes);
    });

    test('sendToBackground returns true', () async {
      _mockValue('$_prefix.PHostActivityControlApi.sendToBackground', true);
      expect(await WebtritCallkeepPlatform.instance.sendToBackground(), true);
    });

    test('sendToBackground returns false', () async {
      _mockValue('$_prefix.PHostActivityControlApi.sendToBackground', false);
      expect(await WebtritCallkeepPlatform.instance.sendToBackground(), false);
    });

    test('isDeviceLocked returns false', () async {
      _mockValue('$_prefix.PHostActivityControlApi.isDeviceLocked', false);
      expect(await WebtritCallkeepPlatform.instance.isDeviceLocked(), false);
    });

    test('isDeviceLocked returns true', () async {
      _mockValue('$_prefix.PHostActivityControlApi.isDeviceLocked', true);
      expect(await WebtritCallkeepPlatform.instance.isDeviceLocked(), true);
    });
  });

  // ---------------------------------------------------------------------------
  // PHostDiagnosticsApi
  // ---------------------------------------------------------------------------

  group('WebtritCallkeepAndroid — PHostDiagnosticsApi', () {
    test('getDiagnosticReport returns typed map', () async {
      _mockValue('$_prefix.PHostDiagnosticsApi.getDiagnosticReport', {'key': 'value', 'count': 42});
      final report = await WebtritCallkeepPlatform.instance.getDiagnosticReport();
      expect(report, isA<Map<String, dynamic>>());
      expect(report['key'], 'value');
      expect(report['count'], 42);
    });

    test('getDiagnosticReport returns empty map', () async {
      _mockValue('$_prefix.PHostDiagnosticsApi.getDiagnosticReport', <String, Object?>{});
      final report = await WebtritCallkeepPlatform.instance.getDiagnosticReport();
      expect(report, isEmpty);
    });
  });
}
