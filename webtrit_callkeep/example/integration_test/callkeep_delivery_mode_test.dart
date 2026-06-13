import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  // ---------------------------------------------------------------------------
  // getCallDeliveryMode (Android only)
  // ---------------------------------------------------------------------------

  group(
    'getCallDeliveryMode (Android only)',
    skip: kIsWeb || defaultTargetPlatform != TargetPlatform.android,
    () {
      final permissions = WebtritCallkeepPermissions();

      testWidgets('returns a valid CallkeepAndroidCallDeliveryMode', (WidgetTester _) async {
        final mode = await permissions.getCallDeliveryMode();
        expect(CallkeepAndroidCallDeliveryMode.values, contains(mode));
      });

      testWidgets('returns telecom or standalone on a real Android device', (WidgetTester _) async {
        final mode = await permissions.getCallDeliveryMode();
        expect(
          mode == CallkeepAndroidCallDeliveryMode.telecom || mode == CallkeepAndroidCallDeliveryMode.standalone,
          isTrue,
          reason: 'unknown is reserved for non-Android platforms; a real device must report telecom or standalone',
        );
      });

      testWidgets('is stable — same value on two consecutive calls', (WidgetTester _) async {
        final first = await permissions.getCallDeliveryMode();
        final second = await permissions.getCallDeliveryMode();
        expect(second, equals(first));
      });

      testWidgets('does not require Callkeep.setUp — callable without a running callkeep instance',
          (WidgetTester _) async {
        // Deliberately no Callkeep().setUp() call — permissions API is standalone
        final mode = await permissions.getCallDeliveryMode();
        expect(mode, isNotNull);
      });
    },
  );

  // ---------------------------------------------------------------------------
  // non-Android behaviour
  // ---------------------------------------------------------------------------

  group('getCallDeliveryMode non-Android', skip: !kIsWeb && defaultTargetPlatform == TargetPlatform.android, () {
    testWidgets('returns unknown on non-Android platforms', (WidgetTester _) async {
      final mode = await WebtritCallkeepPermissions().getCallDeliveryMode();
      expect(mode, CallkeepAndroidCallDeliveryMode.unknown);
    });
  });
}
