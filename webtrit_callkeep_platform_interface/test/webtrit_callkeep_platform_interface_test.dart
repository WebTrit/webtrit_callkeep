import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

import 'webtrit_callkeep_platform_interface_android_delegate.dart';
import 'webtrit_callkeep_platform_interface_delegate.dart';
import 'webtrit_callkeep_platform_interface_mock.dart';

void main() {
  const callId = 'WaxFX9878iWkQxhGy3e3rbAF';
  const handlerMock = CallkeepHandle.number('380000000000');
  const displayName = 'Display Name';
  const hasVideoMock = false;

  const initialMainRout = '/main';
  const initialCallRout = '/main/call';

  test('$WebtritCallkeepPlatform is the default instance', () {
    expect(WebtritCallkeepPlatform.instance, isInstanceOf<WebtritCallkeepPlatform>());
  });

  setUp(() {
    WebtritCallkeepPlatform.instance = MockWebtritCallkeepPlatformInterfacePlatform();
  });

  test('$WebtritCallkeepPlatform is the mock instance', () {
    expect(WebtritCallkeepPlatform.instance, isInstanceOf<MockWebtritCallkeepPlatformInterfacePlatform>());
  });

  test('Webtrit callkeep answer call', () async {
    final completerUUID = Completer<String>();
    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performAnswerCallListener: (uuid) {
        completerUUID.complete(uuid);
      },
    );
    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.answerCall(callId);

    expect(await completerUUID.future, equals(callId));
  });

  test('Webtrit callkeep end call', () async {
    final completerUUID = Completer<String>();
    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performEndCallListener: (uuid) {
        completerUUID.complete(uuid);
      },
    );
    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.endCall(callId);

    expect(await completerUUID.future, equals(callId));
  });

  test('Webtrit callkeep end call android service', () async {
    final completerUUID = Completer<String>();
    final callkeepRelayMock = WebtritCallkeepDelegateAndroidRelayMock(
      performServiceEndCallListener: completerUUID.complete,
      endCallReceivedListener: (
        String callId,
        String number,
        bool video,
        DateTime createdTime,
        DateTime? acceptedTime,
        DateTime? hungUpTime,
      ) {
        completerUUID.complete(callId);
      },
    );
    WebtritCallkeepPlatform.instance.setAndroidDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.endCallAndroidService(callId);

    expect(await completerUUID.future, equals(callId));
  });

  test('Webtrit callkeep incoming call android service', () async {
    expect(
      await WebtritCallkeepPlatform.instance.incomingCallAndroidService(
        callId,
        handlerMock,
        displayName,
        false,
      ),
      null,
    );
  });

  // TODO: remove, action deprecated

  // test('Webtrit callkeep is look screen android service', () async {
  //   expect(await WebtritCallkeepPlatform.instance.isLockScreenAndroidService(), false);
  // });

  test('Webtrit callkeep is setup', () async {
    expect(await WebtritCallkeepPlatform.instance.isSetUp(), true);
  });

  test('Webtrit callkeep push token for push type VoIP', () async {
    expect(await WebtritCallkeepPlatform.instance.pushTokenForPushTypeVoIP(), 'token');
  });

  test('Webtrit callkeep report connected outgoing call', () async {
    expect(() async => WebtritCallkeepPlatform.instance.reportConnectedOutgoingCall(callId), isA<void>());
  });

  test('Webtrit callkeep report connecting outgoing call', () async {
    expect(() async => WebtritCallkeepPlatform.instance.reportConnectingOutgoingCall(callId), isA<void>());
  });

  test('Webtrit callkeep tear down', () async {
    expect(() async => WebtritCallkeepPlatform.instance.tearDown(), isA<void>());
  });

  // TODO: remove, action deprecated

  // test('Webtrit callkeep wake uo android service', () async {
  //   expect(() async => await WebtritCallkeepPlatform.instance.wakeUpAppAndroidService(path: initialCallRout), isA<void>());
  // });

  test('Webtrit callkeep report end call', () async {
    final completerUUID = Completer<String>();
    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performEndCallListener: (uuid) {
        completerUUID.complete(uuid);
      },
    );
    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.reportEndCall(callId, CallkeepEndCallReason.answeredElsewhere);

    expect(await completerUUID.future, equals(callId));
  });

  test('Webtrit callkeep report new incoming call', () async {
    expect(
      await WebtritCallkeepPlatform.instance.reportNewIncomingCall(
        callId,
        handlerMock,
        displayName,
        false,
      ),
      null,
    );
  });

  test('Webtrit callkeep report update call', () async {
    expect(
      () async => WebtritCallkeepPlatform.instance.reportUpdateCall(callId, handlerMock, displayName, false),
      isA<void>(),
    );
  });

  test('Webtrit callkeep send dtmf', () async {
    final completerUUID = Completer<String>();
    final completerDTMF = Completer<String>();
    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performSendDTMFListener: (uuid, dtmf) {
        completerUUID.complete(uuid);
        completerDTMF.complete(dtmf);
      },
    );
    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.sendDTMF(callId, 'A');

    expect(await completerUUID.future, equals(callId));
    expect(await completerDTMF.future, equals('A'));
  });

  test('Webtrit callkeep set held', () async {
    final completerUUID = Completer<String>();
    final completerHeld = Completer<bool>();
    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performHeldListener: (uuid, held) {
        completerUUID.complete(uuid);
        completerHeld.complete(held);
      },
    );
    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.setHeld(callId, true);

    expect(await completerUUID.future, equals(callId));
    expect(await completerHeld.future, equals(true));
  });

  test('Webtrit callkeep set speaker', () async {
    final completerUUID = Completer<String>();
    final completerSpeaker = Completer<bool>();
    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performSpeakerListener: (uuid, enabled) {
        completerUUID.complete(uuid);
        completerSpeaker.complete(enabled);
      },
    );
    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.setSpeaker(callId, true);

    expect(await completerUUID.future, equals(callId));
    expect(await completerSpeaker.future, equals(true));
  });

  test('Webtrit callkeep set muted', () async {
    final completerUUID = Completer<String>();
    final completerMuted = Completer<bool>();
    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performMuteListener: (uuid, muted) {
        completerUUID.complete(uuid);
        completerMuted.complete(muted);
      },
    );
    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.setMuted(callId, true);

    expect(await completerUUID.future, equals(callId));
    expect(await completerMuted.future, equals(true));
  });

  test('Webtrit callkeep set up', () async {
    expect(
      () async => WebtritCallkeepPlatform.instance.setUp(
        const CallkeepOptions(
          ios: CallkeepIOSOptions(
            localizedName: 'en',
            maximumCallGroups: 2,
            maximumCallsPerCallGroup: 1,
            supportedHandleTypes: {CallkeepHandleType.number},
          ),
          android: CallkeepAndroidOptions(
            incomingPath: initialCallRout,
            rootPath: initialMainRout,
          ),
        ),
      ),
      isA<void>(),
    );
  });

  test('Webtrit callkeep start call', () async {
    final completerUUID = Completer<String>();
    final completerHandler = Completer<CallkeepHandle>();
    final completerDisplayName = Completer<String>();
    final completerHasVideo = Completer<bool>();

    final callkeepRelayMock = WebtritCallkeepDelegateRelayMock(
      performStartCallListener: (uuid, handle, displayNameOrContactIdentifier, video) {
        completerUUID.complete(uuid);
        completerHandler.complete(handle);
        completerDisplayName.complete(displayNameOrContactIdentifier);
        completerHasVideo.complete(video);
      },
    );

    WebtritCallkeepPlatform.instance.setDelegate(callkeepRelayMock);
    await WebtritCallkeepPlatform.instance.startCall(callId, handlerMock, displayName, hasVideoMock);

    expect(await completerUUID.future, equals(callId));
    expect(await completerHandler.future, equals(handlerMock));
    expect(await completerDisplayName.future, equals(displayName));
    expect(await completerHasVideo.future, equals(hasVideoMock));
  });
}
