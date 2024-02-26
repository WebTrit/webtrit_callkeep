import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';
import 'package:webtrit_callkeep_web/webtrit_callkeep_web.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('WebtritCallkeepWeb', () {
    const kPlatformName = 'Web';
    late WebtritCallkeepWeb webtritCallkeep;

    setUp(() async {
      webtritCallkeep = WebtritCallkeepWeb();
    });

    test('can be registered', () {
      WebtritCallkeepWeb.registerWith();
      expect(WebtritCallkeepPlatform.instance, isA<WebtritCallkeepWeb>());
    });

    test('getPlatformName returns correct name', () async {
      final name = await webtritCallkeep.getPlatformName();
      expect(name, equals(kPlatformName));
    });
  });
}
