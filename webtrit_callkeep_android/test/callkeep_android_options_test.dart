// ignore_for_file: public_member_api_docs

import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_android/src/common/converters.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

void main() {
  group('CallkeepAndroidOptions', () {
    // -------------------------------------------------------------------------
    // Default construction
    // -------------------------------------------------------------------------

    test('ringtoneSound defaults to null', () {
      const options = CallkeepAndroidOptions();
      expect(options.ringtoneSound, isNull);
    });

    test('ringbackSound defaults to null', () {
      const options = CallkeepAndroidOptions();
      expect(options.ringbackSound, isNull);
    });

    // -------------------------------------------------------------------------
    // Field assignment
    // -------------------------------------------------------------------------

    test('ringtoneSound is set correctly', () {
      const options = CallkeepAndroidOptions(ringtoneSound: '/assets/ring.mp3');
      expect(options.ringtoneSound, equals('/assets/ring.mp3'));
    });

    test('ringbackSound is set correctly', () {
      const options = CallkeepAndroidOptions(ringbackSound: '/assets/ringback.mp3');
      expect(options.ringbackSound, equals('/assets/ringback.mp3'));
    });

    // -------------------------------------------------------------------------
    // Equality (Equatable)
    // -------------------------------------------------------------------------

    test('two default instances are equal', () {
      const a = CallkeepAndroidOptions();
      const b = CallkeepAndroidOptions();
      expect(a, equals(b));
    });

    test('instances with identical fields are equal', () {
      const a = CallkeepAndroidOptions(ringtoneSound: '/assets/ring.mp3', ringbackSound: '/assets/ringback.mp3');
      const b = CallkeepAndroidOptions(ringtoneSound: '/assets/ring.mp3', ringbackSound: '/assets/ringback.mp3');
      expect(a, equals(b));
    });

    test('instances differ when ringtoneSound differs', () {
      const a = CallkeepAndroidOptions(ringtoneSound: '/assets/ring.mp3');
      const b = CallkeepAndroidOptions(ringtoneSound: '/assets/other.mp3');
      expect(a, isNot(equals(b)));
    });

    test('instances differ when ringbackSound differs', () {
      const a = CallkeepAndroidOptions(ringbackSound: '/assets/ringback.mp3');
      const b = CallkeepAndroidOptions(ringbackSound: null);
      expect(a, isNot(equals(b)));
    });

    test('instances differ when one has ringtoneSound and the other does not', () {
      const a = CallkeepAndroidOptions(ringtoneSound: '/assets/ring.mp3');
      const b = CallkeepAndroidOptions();
      expect(a, isNot(equals(b)));
    });
  });

  group('CallkeepAndroidOptionsConverter', () {
    // -------------------------------------------------------------------------
    // toPigeon — field mapping
    // -------------------------------------------------------------------------

    test('toPigeon maps null ringtoneSound', () {
      const options = CallkeepAndroidOptions();
      final pigeon = options.toPigeon();
      expect(pigeon.ringtoneSound, isNull);
    });

    test('toPigeon maps null ringbackSound', () {
      const options = CallkeepAndroidOptions();
      final pigeon = options.toPigeon();
      expect(pigeon.ringbackSound, isNull);
    });

    test('toPigeon maps ringtoneSound correctly', () {
      const options = CallkeepAndroidOptions(ringtoneSound: '/assets/ring.mp3');
      final pigeon = options.toPigeon();
      expect(pigeon.ringtoneSound, equals('/assets/ring.mp3'));
    });

    test('toPigeon maps ringbackSound correctly', () {
      const options = CallkeepAndroidOptions(ringbackSound: '/assets/ringback.mp3');
      final pigeon = options.toPigeon();
      expect(pigeon.ringbackSound, equals('/assets/ringback.mp3'));
    });

    test('toPigeon maps both fields together', () {
      const options = CallkeepAndroidOptions(ringtoneSound: '/assets/ring.mp3', ringbackSound: '/assets/ringback.mp3');
      final pigeon = options.toPigeon();
      expect(pigeon.ringtoneSound, equals('/assets/ring.mp3'));
      expect(pigeon.ringbackSound, equals('/assets/ringback.mp3'));
    });
  });
}
