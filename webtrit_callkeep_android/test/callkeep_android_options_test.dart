// ignore_for_file: public_member_api_docs

import 'package:flutter_test/flutter_test.dart';
import 'package:webtrit_callkeep_android/src/common/converters.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

void main() {
  group('CallkeepAndroidOptions', () {
    test('incomingCallFullScreen defaults to true', () {
      const options = CallkeepAndroidOptions();
      expect(options.incomingCallFullScreen, isTrue);
    });

    test('incomingCallFullScreen can be set to false', () {
      const options = CallkeepAndroidOptions(incomingCallFullScreen: false);
      expect(options.incomingCallFullScreen, isFalse);
    });

    test('equality considers incomingCallFullScreen', () {
      const a = CallkeepAndroidOptions(incomingCallFullScreen: true);
      const b = CallkeepAndroidOptions(incomingCallFullScreen: false);
      expect(a, isNot(equals(b)));
    });

    test('same incomingCallFullScreen value is equal', () {
      const a = CallkeepAndroidOptions(incomingCallFullScreen: false);
      const b = CallkeepAndroidOptions(incomingCallFullScreen: false);
      expect(a, equals(b));
    });
  });

  group('CallkeepAndroidOptionsConverter', () {
    test('toPigeon maps incomingCallFullScreen true', () {
      const options = CallkeepAndroidOptions();
      final pigeon = options.toPigeon();
      expect(pigeon.incomingCallFullScreen, isTrue);
    });

    test('toPigeon maps incomingCallFullScreen false', () {
      const options = CallkeepAndroidOptions(incomingCallFullScreen: false);
      final pigeon = options.toPigeon();
      expect(pigeon.incomingCallFullScreen, isFalse);
    });

    test('toPigeon preserves ringtoneSound alongside incomingCallFullScreen', () {
      const options = CallkeepAndroidOptions(
        ringtoneSound: '/path/to/ringtone.mp3',
        incomingCallFullScreen: false,
      );
      final pigeon = options.toPigeon();
      expect(pigeon.ringtoneSound, equals('/path/to/ringtone.mp3'));
      expect(pigeon.incomingCallFullScreen, isFalse);
    });
  });
}
