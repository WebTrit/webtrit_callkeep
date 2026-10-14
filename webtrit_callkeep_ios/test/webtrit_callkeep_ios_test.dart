import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:webtrit_callkeep_ios/webtrit_callkeep_ios.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

const _prefix = 'dev.flutter.pigeon.webtrit_callkeep_ios';

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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    WebtritCallkeep.registerWith();
    _mockVoid('$_prefix.PHostApi.setUp');
    _mockVoid('$_prefix.PHostApi.tearDown');
  });

  test('registers instance', () {
    expect(WebtritCallkeepPlatform.instance, isA<WebtritCallkeep>());
  });

  test('isSetUp reports what the platform answers', () async {
    _mockValue('$_prefix.PHostApi.isSetUp', true);

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

    expect(await WebtritCallkeepPlatform.instance.isSetUp(), true);

    await WebtritCallkeepPlatform.instance.tearDown();

    _mockValue('$_prefix.PHostApi.isSetUp', false);
    expect(await WebtritCallkeepPlatform.instance.isSetUp(), false);
  });
}
