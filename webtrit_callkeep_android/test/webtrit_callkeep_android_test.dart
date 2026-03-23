import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_android/webtrit_callkeep_android.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

void _mockVoidChannel(String channelName) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
    channelName,
    (message) async => const StandardMessageCodec().encodeMessage([null]),
  );
}

void _mockChannel(String channelName, Object? returnValue) {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMessageHandler(
    channelName,
    (message) async => const StandardMessageCodec().encodeMessage([returnValue]),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    WebtritCallkeepAndroid.registerWith();
    _mockVoidChannel('dev.flutter.pigeon.webtrit_callkeep_android.PHostApi.setUp');
    _mockVoidChannel('dev.flutter.pigeon.webtrit_callkeep_android.PHostApi.tearDown');
    _mockChannel('dev.flutter.pigeon.webtrit_callkeep_android.PHostApi.isSetUp', true);
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

  test('registers instance', () {
    expect(WebtritCallkeepPlatform.instance, isA<WebtritCallkeepAndroid>());
  });

  test('isSetUp', () async {
    expect(await WebtritCallkeepPlatform.instance.isSetUp(), true);
  });
}
