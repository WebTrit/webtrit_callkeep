import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// Stands in for a real platform implementation: this package is the umbrella,
/// it has no platform code of its own to talk to.
class _FakeCallkeepPlatform extends WebtritCallkeepPlatform with MockPlatformInterfaceMixin {
  CallkeepOptions? receivedOptions;
  var tearDownCount = 0;

  var _setUp = false;

  @override
  Future<bool> isSetUp() async => _setUp;

  @override
  Future<void> setUp(CallkeepOptions options) async {
    receivedOptions = options;
    _setUp = true;
  }

  @override
  Future<void> tearDown() async {
    tearDownCount++;
    _setUp = false;
  }
}

const _options = CallkeepOptions(
  ios: CallkeepIOSOptions(
    localizedName: 'Test',
    maximumCallGroups: 1,
    maximumCallsPerCallGroup: 1,
    supportedHandleTypes: {CallkeepHandleType.number},
  ),
  android: CallkeepAndroidOptions(),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _FakeCallkeepPlatform platform;

  setUp(() {
    platform = _FakeCallkeepPlatform();
    WebtritCallkeepPlatform.instance = platform;
  });

  test('is not set up before setUp is called', () async {
    expect(await WebtritCallkeepPlatform.instance.isSetUp(), false);
  });

  test('isSetUp turns true after setUp and false again after tearDown', () async {
    await WebtritCallkeepPlatform.instance.setUp(_options);
    expect(await WebtritCallkeepPlatform.instance.isSetUp(), true);

    await WebtritCallkeepPlatform.instance.tearDown();
    expect(await WebtritCallkeepPlatform.instance.isSetUp(), false);
    expect(platform.tearDownCount, 1);
  });

  test('setUp hands the options through to the platform untouched', () async {
    await WebtritCallkeepPlatform.instance.setUp(_options);

    expect(platform.receivedOptions, same(_options));
  });
}
