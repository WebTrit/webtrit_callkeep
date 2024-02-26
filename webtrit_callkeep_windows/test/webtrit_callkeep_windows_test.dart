import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';
import 'package:webtrit_callkeep_windows/webtrit_callkeep_windows.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('WebtritCallkeepWindows', () {
    const kPlatformName = 'Windows';
    late WebtritCallkeepWindows webtritCallkeep;
    late List<MethodCall> log;

    setUp(() async {
      webtritCallkeep = WebtritCallkeepWindows();

      log = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(webtritCallkeep.methodChannel, (methodCall) async {
        log.add(methodCall);
        switch (methodCall.method) {
          case 'getPlatformName':
            return kPlatformName;
          default:
            return null;
        }
      });
    });

    test('can be registered', () {
      WebtritCallkeepWindows.registerWith();
      expect(WebtritCallkeepPlatform.instance, isA<WebtritCallkeepWindows>());
    });

    test('getPlatformName returns correct name', () async {
      final name = await webtritCallkeep.getPlatformName();
      expect(
        log,
        <Matcher>[isMethodCall('getPlatformName', arguments: null)],
      );
      expect(name, equals(kPlatformName));
    });
  });
}
