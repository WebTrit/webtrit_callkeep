import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_android/src/common/callkeep.pigeon.dart';
import 'package:webtrit_callkeep_android/webtrit_callkeep_android.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

const _prefix = 'dev.flutter.pigeon.webtrit_callkeep_android';
final _codec = PHostApi.pigeonChannelCodec;

// ── Fake delegates ───────────────────────────────────────────────────────────

class _FakeCallkeepDelegate implements CallkeepDelegate {
  // Recorded invocations: method name -> list of argument lists
  final Map<String, List<List<dynamic>>> calls = {};

  void _record(String method, List<dynamic> args) => (calls[method] ??= []).add(args);

  @override
  void continueStartCallIntent(CallkeepHandle handle, String? displayName, bool video) {
    _record('continueStartCallIntent', [handle, displayName, video]);
  }

  @override
  void didPushIncomingCall(
    CallkeepHandle handle,
    String? displayName,
    bool video,
    String callId,
    CallkeepIncomingCallError? error,
  ) {
    _record('didPushIncomingCall', [handle, displayName, video, callId, error]);
  }

  @override
  Future<bool> performStartCall(
    String callId,
    CallkeepHandle handle,
    String? displayNameOrContactIdentifier,
    bool video,
  ) async {
    _record('performStartCall', [callId, handle, displayNameOrContactIdentifier, video]);
    return true;
  }

  @override
  Future<bool> performAnswerCall(String callId) async {
    _record('performAnswerCall', [callId]);
    return true;
  }

  @override
  Future<bool> performEndCall(String callId) async {
    _record('performEndCall', [callId]);
    return false;
  }

  @override
  Future<bool> performSetHeld(String callId, bool onHold) async {
    _record('performSetHeld', [callId, onHold]);
    return true;
  }

  @override
  Future<bool> performSetMuted(String callId, bool muted) async {
    _record('performSetMuted', [callId, muted]);
    return true;
  }

  @override
  Future<bool> performSendDTMF(String callId, String key) async {
    _record('performSendDTMF', [callId, key]);
    return true;
  }

  @override
  Future<bool> performAudioDeviceSet(String callId, CallkeepAudioDevice device) async {
    _record('performAudioDeviceSet', [callId, device]);
    return true;
  }

  @override
  Future<bool> performAudioDevicesUpdate(String callId, List<CallkeepAudioDevice> devices) async {
    _record('performAudioDevicesUpdate', [callId, devices]);
    return true;
  }

  @override
  void didActivateAudioSession() => _record('didActivateAudioSession', []);

  @override
  void didDeactivateAudioSession() => _record('didDeactivateAudioSession', []);

  @override
  void didReset() => _record('didReset', []);

  void performIncomingCall(String callId, CallkeepHandle handle, String? displayName, bool video) {
    _record('performIncomingCall', [callId, handle, displayName, video]);
  }

  void performConnecting(String callId) => _record('performConnecting', [callId]);

  void performConnected(String callId) => _record('performConnected', [callId]);

  Future<bool> performEndCallWithUUID(String callId) async {
    _record('performEndCallWithUUID', [callId]);
    return true;
  }
}

class _FakeLogsDelegate implements CallkeepLogsDelegate {
  final List<List<dynamic>> calls = [];

  @override
  void onLog(CallkeepLogType type, String tag, String message) {
    calls.add([type, tag, message]);
  }
}

class _FakePushRegistryDelegate implements PushRegistryDelegate {
  final List<String?> tokens = [];

  @override
  void didUpdatePushTokenForPushTypeVoIP(String? token) {
    tokens.add(token);
  }
}

class _FakeBackgroundServiceDelegate implements CallkeepBackgroundServiceDelegate {
  final Map<String, List<String>> calls = {};

  @override
  Future<void> performAnswerCall(String callId) async {
    (calls['performAnswerCall'] ??= []).add(callId);
  }

  @override
  Future<void> performEndCall(String callId) async {
    (calls['performEndCall'] ??= []).add(callId);
  }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/// Simulates native side sending a message on a Pigeon Flutter-API channel.
Future<ByteData?> _send(String channelName, List<Object?> args) {
  final completer = Completer<ByteData?>();
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.handlePlatformMessage(
    channelName,
    _codec.encodeMessage(args),
    (reply) => completer.complete(reply),
  );
  return completer.future;
}

void _mockVoid(String channel) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
    channel,
    (message) async => const StandardMessageCodec().encodeMessage([null]),
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    WebtritCallkeepAndroid.registerWith();
    _mockVoid('$_prefix.PHostApi.onDelegateSet');
  });

  // ---------------------------------------------------------------------------
  // _CallkeepDelegateRelay
  // ---------------------------------------------------------------------------

  group('_CallkeepDelegateRelay', () {
    late _FakeCallkeepDelegate fake;

    setUp(() {
      fake = _FakeCallkeepDelegate();
      WebtritCallkeepPlatform.instance.setDelegate(fake);
    });

    tearDown(() {
      WebtritCallkeepPlatform.instance.setDelegate(null);
    });

    test('setDelegate(null) clears handlers without error', () {
      expect(() => WebtritCallkeepPlatform.instance.setDelegate(null), returnsNormally);
    });

    test('continueStartCallIntent forwards converted handle and args', () async {
      await _send('$_prefix.PDelegateFlutterApi.continueStartCallIntent', [
        PHandle(type: PHandleTypeEnum.number, value: '+1234'),
        'Alice',
        true,
      ]);
      expect(fake.calls['continueStartCallIntent'], hasLength(1));
      final args = fake.calls['continueStartCallIntent']![0];
      expect((args[0] as CallkeepHandle).type, CallkeepHandleType.number);
      expect((args[0] as CallkeepHandle).value, '+1234');
      expect(args[1], 'Alice');
      expect(args[2], true);
    });

    test('continueStartCallIntent forwards null displayName', () async {
      await _send('$_prefix.PDelegateFlutterApi.continueStartCallIntent', [
        PHandle(type: PHandleTypeEnum.email, value: 'a@b.com'),
        null,
        false,
      ]);
      final args = fake.calls['continueStartCallIntent']![0];
      expect(args[1], isNull);
    });

    test('didPushIncomingCall with null error forwards null error', () async {
      await _send('$_prefix.PDelegateFlutterApi.didPushIncomingCall', [
        PHandle(type: PHandleTypeEnum.number, value: '555'),
        'Bob',
        false,
        'call-42',
        null,
      ]);
      final args = fake.calls['didPushIncomingCall']![0];
      expect(args[3], 'call-42');
      expect(args[4], isNull);
    });

    test('didPushIncomingCall converts PIncomingCallError to CallkeepIncomingCallError', () async {
      await _send('$_prefix.PDelegateFlutterApi.didPushIncomingCall', [
        PHandle(type: PHandleTypeEnum.number, value: '555'),
        null,
        false,
        'call-43',
        PIncomingCallError(value: PIncomingCallErrorEnum.callRejectedBySystem),
      ]);
      final args = fake.calls['didPushIncomingCall']![0];
      expect(args[4], CallkeepIncomingCallError.callRejectedBySystem);
    });

    test('performStartCall calls delegate and returns bool', () async {
      final replyData = await _send('$_prefix.PDelegateFlutterApi.performStartCall', [
        'call-1',
        PHandle(type: PHandleTypeEnum.email, value: 'a@b.com'),
        null,
        false,
      ]);
      expect(fake.calls['performStartCall'], hasLength(1));
      final args = fake.calls['performStartCall']![0];
      expect(args[0], 'call-1');
      expect((args[1] as CallkeepHandle).type, CallkeepHandleType.email);
      // Pigeon encodes wrapResponse(result: true) which the relay returned
      final reply = _codec.decodeMessage(replyData) as List<Object?>;
      expect(reply[0], true);
    });

    test('performAnswerCall forwards callId and returns true', () async {
      final replyData = await _send('$_prefix.PDelegateFlutterApi.performAnswerCall', ['call-99']);
      expect(fake.calls['performAnswerCall']![0][0], 'call-99');
      final reply = _codec.decodeMessage(replyData) as List<Object?>;
      expect(reply[0], true);
    });

    test('performEndCall forwards callId and returns false', () async {
      final replyData = await _send('$_prefix.PDelegateFlutterApi.performEndCall', ['call-77']);
      expect(fake.calls['performEndCall']![0][0], 'call-77');
      final reply = _codec.decodeMessage(replyData) as List<Object?>;
      expect(reply[0], false);
    });

    test('performSetHeld forwards callId and onHold=true', () async {
      await _send('$_prefix.PDelegateFlutterApi.performSetHeld', ['call-1', true]);
      final args = fake.calls['performSetHeld']![0];
      expect(args[0], 'call-1');
      expect(args[1], true);
    });

    test('performSetHeld forwards onHold=false', () async {
      await _send('$_prefix.PDelegateFlutterApi.performSetHeld', ['call-1', false]);
      expect(fake.calls['performSetHeld']![0][1], false);
    });

    test('performSetMuted forwards callId and muted', () async {
      await _send('$_prefix.PDelegateFlutterApi.performSetMuted', ['call-2', false]);
      final args = fake.calls['performSetMuted']![0];
      expect(args[0], 'call-2');
      expect(args[1], false);
    });

    test('performSendDTMF forwards callId and key', () async {
      await _send('$_prefix.PDelegateFlutterApi.performSendDTMF', ['call-3', '5']);
      final args = fake.calls['performSendDTMF']![0];
      expect(args[0], 'call-3');
      expect(args[1], '5');
    });

    test('performAudioDeviceSet converts PAudioDevice by name mapping', () async {
      await _send('$_prefix.PDelegateFlutterApi.performAudioDeviceSet', [
        'call-1',
        PAudioDevice(type: PAudioDeviceType.bluetooth, id: 'bt1', name: 'Headset'),
      ]);
      final args = fake.calls['performAudioDeviceSet']![0];
      expect(args[0], 'call-1');
      final device = args[1] as CallkeepAudioDevice;
      expect(device.type, CallkeepAudioDeviceType.bluetooth);
      expect(device.id, 'bt1');
      expect(device.name, 'Headset');
    });

    test('performAudioDevicesUpdate converts list of PAudioDevice', () async {
      await _send('$_prefix.PDelegateFlutterApi.performAudioDevicesUpdate', [
        'call-1',
        [
          PAudioDevice(type: PAudioDeviceType.earpiece, id: null, name: null),
          PAudioDevice(type: PAudioDeviceType.speaker, id: null, name: null),
        ],
      ]);
      final args = fake.calls['performAudioDevicesUpdate']![0];
      expect(args[0], 'call-1');
      final devices = args[1] as List<CallkeepAudioDevice>;
      expect(devices.length, 2);
      expect(devices[0].type, CallkeepAudioDeviceType.earpiece);
      expect(devices[1].type, CallkeepAudioDeviceType.speaker);
    });

    test('didActivateAudioSession calls delegate', () async {
      await _send('$_prefix.PDelegateFlutterApi.didActivateAudioSession', []);
      expect(fake.calls['didActivateAudioSession'], hasLength(1));
    });

    test('didDeactivateAudioSession calls delegate', () async {
      await _send('$_prefix.PDelegateFlutterApi.didDeactivateAudioSession', []);
      expect(fake.calls['didDeactivateAudioSession'], hasLength(1));
    });

    test('didReset calls delegate', () async {
      await _send('$_prefix.PDelegateFlutterApi.didReset', []);
      expect(fake.calls['didReset'], hasLength(1));
    });
  });

  // ---------------------------------------------------------------------------
  // _LogsDelegateRelay
  // ---------------------------------------------------------------------------

  group('_LogsDelegateRelay', () {
    late _FakeLogsDelegate fake;

    setUp(() {
      fake = _FakeLogsDelegate();
      WebtritCallkeepPlatform.instance.setLogsDelegate(fake);
    });

    tearDown(() {
      WebtritCallkeepPlatform.instance.setLogsDelegate(null);
    });

    test('setLogsDelegate(null) clears without error', () {
      expect(() => WebtritCallkeepPlatform.instance.setLogsDelegate(null), returnsNormally);
    });

    test('onLog converts PLogTypeEnum and forwards all fields', () async {
      await _send('$_prefix.PDelegateLogsFlutterApi.onLog', [PLogTypeEnum.warn, 'TAG', 'some message']);
      expect(fake.calls.length, 1);
      expect(fake.calls[0][0], CallkeepLogType.warn);
      expect(fake.calls[0][1], 'TAG');
      expect(fake.calls[0][2], 'some message');
    });

    test('onLog with debug type', () async {
      await _send('$_prefix.PDelegateLogsFlutterApi.onLog', [PLogTypeEnum.debug, 'DEBUG_TAG', 'debug msg']);
      expect(fake.calls[0][0], CallkeepLogType.debug);
    });

    test('onLog with error type', () async {
      await _send('$_prefix.PDelegateLogsFlutterApi.onLog', [PLogTypeEnum.error, 'ERR', 'err msg']);
      expect(fake.calls[0][0], CallkeepLogType.error);
    });
  });

  // ---------------------------------------------------------------------------
  // _PushRegistryDelegateRelay
  // ---------------------------------------------------------------------------

  group('_PushRegistryDelegateRelay', () {
    late _FakePushRegistryDelegate fake;

    setUp(() {
      fake = _FakePushRegistryDelegate();
      WebtritCallkeepPlatform.instance.setPushRegistryDelegate(fake);
    });

    tearDown(() {
      WebtritCallkeepPlatform.instance.setPushRegistryDelegate(null);
    });

    test('setPushRegistryDelegate(null) clears without error', () {
      expect(() => WebtritCallkeepPlatform.instance.setPushRegistryDelegate(null), returnsNormally);
    });

    test('didUpdatePushTokenForPushTypeVoIP forwards token', () async {
      await _send('$_prefix.PPushRegistryDelegateFlutterApi.didUpdatePushTokenForPushTypeVoIP', ['abc-token-123']);
      expect(fake.tokens.length, 1);
      expect(fake.tokens[0], 'abc-token-123');
    });

    test('didUpdatePushTokenForPushTypeVoIP forwards null token', () async {
      await _send('$_prefix.PPushRegistryDelegateFlutterApi.didUpdatePushTokenForPushTypeVoIP', [null]);
      expect(fake.tokens[0], isNull);
    });
  });

  // ---------------------------------------------------------------------------
  // _CallkeepBackgroundServiceDelegateRelay
  // ---------------------------------------------------------------------------

  group('_CallkeepBackgroundServiceDelegateRelay', () {
    late _FakeBackgroundServiceDelegate fake;

    setUp(() {
      fake = _FakeBackgroundServiceDelegate();
      WebtritCallkeepPlatform.instance.setBackgroundServiceDelegate(fake);
    });

    tearDown(() {
      WebtritCallkeepPlatform.instance.setBackgroundServiceDelegate(null);
    });

    test('setBackgroundServiceDelegate(null) clears without error', () {
      expect(() => WebtritCallkeepPlatform.instance.setBackgroundServiceDelegate(null), returnsNormally);
    });

    test('performAnswerCall forwards callId', () async {
      await _send('$_prefix.PDelegateBackgroundServiceFlutterApi.performAnswerCall', ['call-bg-1']);
      expect(fake.calls['performAnswerCall'], contains('call-bg-1'));
    });

    test('performEndCall forwards callId', () async {
      await _send('$_prefix.PDelegateBackgroundServiceFlutterApi.performEndCall', ['call-bg-2']);
      expect(fake.calls['performEndCall'], contains('call-bg-2'));
    });
  });
}
