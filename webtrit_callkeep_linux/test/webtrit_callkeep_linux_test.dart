import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_linux/webtrit_callkeep_linux.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('WebtritCallkeepLinux', () {
    const kPlatformName = 'Linux';
    late WebtritCallkeepLinux webtritCallkeep;
    late List<MethodCall> log;

    setUp(() async {
      webtritCallkeep = WebtritCallkeepLinux();

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
      WebtritCallkeepLinux.registerWith();
      expect(WebtritCallkeepPlatform.instance, isA<WebtritCallkeepLinux>());
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
