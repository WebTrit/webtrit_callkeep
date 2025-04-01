import 'package:flutter_test/flutter_test.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    WebtritCallkeepPlatform.instance.setUp(
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

  tearDown(() {
    WebtritCallkeepPlatform.instance.tearDown();
  });

  test('isSetUp', () async {
    expect(await WebtritCallkeepPlatform.instance.isSetUp(), true);
  });
}
