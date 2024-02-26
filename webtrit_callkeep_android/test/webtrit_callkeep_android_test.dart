import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_android/webtrit_callkeep_android.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('WebtritCallkeepAndroid', () {
    const kPlatformName = 'Android';
    late WebtritCallkeepAndroid webtritCallkeep;
    late List<MethodCall> log;

    setUp(() async {
      webtritCallkeep = WebtritCallkeepAndroid();

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
      WebtritCallkeepAndroid.registerWith();
      expect(WebtritCallkeepPlatform.instance, isA<WebtritCallkeepAndroid>());
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
