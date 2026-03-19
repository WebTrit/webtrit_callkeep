import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const _options = CallkeepOptions(
  ios: CallkeepIOSOptions(
    localizedName: 'Integration Tests',
    maximumCallGroups: 2,
    maximumCallsPerCallGroup: 1,
    supportedHandleTypes: {CallkeepHandleType.number},
  ),
  android: CallkeepAndroidOptions(),
);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late Callkeep callkeep;
  var globalTearDownNeeded = true;

  setUp(() async {
    globalTearDownNeeded = true;
    callkeep = Callkeep();
    for (var attempt = 0; attempt < 10; attempt++) {
      try {
        await callkeep.setUp(_options);
        break;
      } catch (_) {
        if (attempt == 9) rethrow;
        await Future.delayed(const Duration(milliseconds: 300));
      }
    }
  });

  tearDown(() async {
    callkeep.setDelegate(null);
    if (globalTearDownNeeded) {
      try {
        await callkeep.tearDown().timeout(const Duration(seconds: 15));
      } catch (_) {}
    }
    await Future.delayed(const Duration(milliseconds: 300));
  });

  // -------------------------------------------------------------------------
  // isSetUp state machine
  // -------------------------------------------------------------------------

  group('isSetUp state machine', () {
    test('isSetUp returns true after setUp', () async {
      final result = await callkeep.isSetUp();
      expect(result, isTrue);
    });

    test('isSetUp returns false after tearDown', () async {
      // Android: ForegroundService persists after tearDown, so isSetUp() may
      // remain true. Only assert on iOS where tearDown fully stops CallKit.
      if (!kIsWeb && Platform.isAndroid) {
        markTestSkipped('Android ForegroundService persists; isSetUp always true');
        return;
      }
      globalTearDownNeeded = false;
      await callkeep.tearDown();
      final result = await callkeep.isSetUp();
      expect(result, isFalse);
    });

    test('isSetUp returns false after re-tearDown in second cycle', () async {
      // Android: same as above — ForegroundService keeps isSetUp true.
      if (!kIsWeb && Platform.isAndroid) {
        markTestSkipped('Android ForegroundService persists; isSetUp always true');
        return;
      }
      globalTearDownNeeded = false;
      // First tearDown
      await callkeep.tearDown();
      // Re-setUp
      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }
      expect(await callkeep.isSetUp(), isTrue);
      // Second tearDown
      await callkeep.tearDown();
      expect(await callkeep.isSetUp(), isFalse);
    });
  });

  // -------------------------------------------------------------------------
  // statusStream transitions
  // -------------------------------------------------------------------------

  group('statusStream transitions', () {
    test('setUp emits configuring then active', () async {
      // Must subscribe BEFORE tearDown/setUp cycle to capture events
      globalTearDownNeeded = false;
      await callkeep.tearDown();

      final events = <CallkeepStatus>[];
      final sub = callkeep.statusStream.listen(events.add);

      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }

      // Allow async event delivery before cancelling
      await Future.delayed(const Duration(milliseconds: 50));
      await sub.cancel();
      await callkeep.tearDown();

      expect(events, containsAll([CallkeepStatus.configuring, CallkeepStatus.active]));
      expect(
        events.indexOf(CallkeepStatus.configuring),
        lessThan(events.indexOf(CallkeepStatus.active)),
      );
    });

    test('tearDown emits terminating then uninitialized', () async {
      globalTearDownNeeded = false;

      final events = <CallkeepStatus>[];
      final sub = callkeep.statusStream.listen(events.add);

      await callkeep.tearDown();
      // Allow async event delivery before cancelling
      await Future.delayed(const Duration(milliseconds: 50));
      await sub.cancel();

      expect(events, containsAll([CallkeepStatus.terminating, CallkeepStatus.uninitialized]));
      expect(
        events.indexOf(CallkeepStatus.terminating),
        lessThan(events.indexOf(CallkeepStatus.uninitialized)),
      );
    });

    test('full setUp+tearDown cycle emits [configuring, active, terminating, uninitialized] in order', () async {
      globalTearDownNeeded = false;
      await callkeep.tearDown();
      await Future.delayed(const Duration(milliseconds: 50));

      final events = <CallkeepStatus>[];
      final sub = callkeep.statusStream.listen(events.add);

      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }
      await callkeep.tearDown();
      // Allow async event delivery before cancelling
      await Future.delayed(const Duration(milliseconds: 50));
      await sub.cancel();

      expect(
          events,
          containsAll([
            CallkeepStatus.configuring,
            CallkeepStatus.active,
            CallkeepStatus.terminating,
            CallkeepStatus.uninitialized,
          ]));

      final idxConfiguring = events.indexOf(CallkeepStatus.configuring);
      final idxActive = events.indexOf(CallkeepStatus.active);
      final idxTerminating = events.indexOf(CallkeepStatus.terminating);
      final idxUninitialized = events.indexOf(CallkeepStatus.uninitialized);

      expect(idxConfiguring, lessThan(idxActive));
      expect(idxActive, lessThan(idxTerminating));
      expect(idxTerminating, lessThan(idxUninitialized));
    });
  });

  // -------------------------------------------------------------------------
  // currentStatus sync getter
  // -------------------------------------------------------------------------

  group('currentStatus sync getter', () {
    test('currentStatus is active after setUp', () async {
      expect(callkeep.currentStatus, CallkeepStatus.active);
    });

    test('currentStatus is uninitialized after tearDown', () async {
      globalTearDownNeeded = false;
      await callkeep.tearDown();
      expect(callkeep.currentStatus, CallkeepStatus.uninitialized);
    });

    test('currentStatus is uninitialized after re-tearDown in second cycle', () async {
      globalTearDownNeeded = false;
      await callkeep.tearDown();
      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }
      expect(callkeep.currentStatus, CallkeepStatus.active);
      await callkeep.tearDown();
      expect(callkeep.currentStatus, CallkeepStatus.uninitialized);
    });
  });

  // -------------------------------------------------------------------------
  // Multiple setUp/tearDown cycles
  // -------------------------------------------------------------------------

  group('multiple setUp/tearDown cycles', () {
    test('tearDown then setUp restores active status', () async {
      globalTearDownNeeded = false;
      await callkeep.tearDown();
      expect(callkeep.currentStatus, CallkeepStatus.uninitialized);

      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }
      expect(callkeep.currentStatus, CallkeepStatus.active);
      await callkeep.tearDown();
    });

    test('two cycles without error', () async {
      globalTearDownNeeded = false;

      // Cycle 1
      await callkeep.tearDown();
      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }

      // Cycle 2
      await callkeep.tearDown();
      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }
      expect(callkeep.currentStatus, CallkeepStatus.active);
      await callkeep.tearDown();
    });

    test('isSetUp returns true after each re-setUp', () async {
      globalTearDownNeeded = false;

      await callkeep.tearDown();
      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }
      expect(await callkeep.isSetUp(), isTrue);

      await callkeep.tearDown();
      for (var attempt = 0; attempt < 10; attempt++) {
        try {
          await callkeep.setUp(_options);
          break;
        } catch (_) {
          if (attempt == 9) rethrow;
          await Future.delayed(const Duration(milliseconds: 300));
        }
      }
      expect(await callkeep.isSetUp(), isTrue);
      await callkeep.tearDown();
    });
  });
}
