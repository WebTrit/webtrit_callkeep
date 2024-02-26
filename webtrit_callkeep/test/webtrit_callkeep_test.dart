import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

class MockWebtritCallkeepPlatform extends Mock
    with MockPlatformInterfaceMixin
    implements WebtritCallkeepPlatform {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('WebtritCallkeep', () {
    late WebtritCallkeepPlatform webtritCallkeepPlatform;

    setUp(() {
      webtritCallkeepPlatform = MockWebtritCallkeepPlatform();
      WebtritCallkeepPlatform.instance = webtritCallkeepPlatform;
    });

    group('getPlatformName', () {
      test('returns correct name when platform implementation exists',
          () async {
        const platformName = '__test_platform__';
        when(
          () => webtritCallkeepPlatform.getPlatformName(),
        ).thenAnswer((_) async => platformName);

        final actualPlatformName = await getPlatformName();
        expect(actualPlatformName, equals(platformName));
      });

      test('throws exception when platform implementation is missing',
          () async {
        when(
          () => webtritCallkeepPlatform.getPlatformName(),
        ).thenAnswer((_) async => null);

        expect(getPlatformName, throwsException);
      });
    });
  });
}
