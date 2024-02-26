import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

class WebtritCallkeepMock extends WebtritCallkeepPlatform {
  static const mockPlatformName = 'Mock';

  @override
  Future<String?> getPlatformName() async => mockPlatformName;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  group('WebtritCallkeepPlatformInterface', () {
    late WebtritCallkeepPlatform webtritCallkeepPlatform;

    setUp(() {
      webtritCallkeepPlatform = WebtritCallkeepMock();
      WebtritCallkeepPlatform.instance = webtritCallkeepPlatform;
    });

    group('getPlatformName', () {
      test('returns correct name', () async {
        expect(
          await WebtritCallkeepPlatform.instance.getPlatformName(),
          equals(WebtritCallkeepMock.mockPlatformName),
        );
      });
    });
  });
}
