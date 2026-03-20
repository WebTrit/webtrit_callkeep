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

    test('incomingCallFullScreen defaults to null', () {
      const options = CallkeepAndroidOptions();
      expect(options.incomingCallFullScreen, isNull);
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

    test('incomingCallFullScreen is set correctly when true', () {
      const options = CallkeepAndroidOptions(incomingCallFullScreen: true);
      expect(options.incomingCallFullScreen, isTrue);
    });

    test('incomingCallFullScreen is set correctly when false', () {
      const options = CallkeepAndroidOptions(incomingCallFullScreen: false);
      expect(options.incomingCallFullScreen, isFalse);
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

    test('instances differ when incomingCallFullScreen differs', () {
      const a = CallkeepAndroidOptions(incomingCallFullScreen: true);
      const b = CallkeepAndroidOptions(incomingCallFullScreen: false);
      expect(a, isNot(equals(b)));
    });

    test('instance with incomingCallFullScreen set differs from default', () {
      const a = CallkeepAndroidOptions(incomingCallFullScreen: true);
      const b = CallkeepAndroidOptions();
      expect(a, isNot(equals(b)));
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

    test('toPigeon maps incomingCallFullScreen true', () {
      const options = CallkeepAndroidOptions(incomingCallFullScreen: true);
      final pigeon = options.toPigeon();
      expect(pigeon.incomingCallFullScreen, isTrue);
    });

    test('toPigeon maps incomingCallFullScreen false', () {
      const options = CallkeepAndroidOptions(incomingCallFullScreen: false);
      final pigeon = options.toPigeon();
      expect(pigeon.incomingCallFullScreen, isFalse);
    });

    test('toPigeon maps null incomingCallFullScreen', () {
      const options = CallkeepAndroidOptions();
      final pigeon = options.toPigeon();
      expect(pigeon.incomingCallFullScreen, isNull);
    });
  });
}
