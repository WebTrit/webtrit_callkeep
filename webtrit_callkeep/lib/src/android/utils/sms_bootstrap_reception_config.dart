import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// Configures the background push notification service on Android.
class SmsBootstrapReceptionConfig {
  /// Returns the singleton instance.
  factory SmsBootstrapReceptionConfig() => _instance;

  SmsBootstrapReceptionConfig._();

  static final _instance = SmsBootstrapReceptionConfig._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Configures the SMS receiver (Android only) with the given prefix and regex pattern.
  ///
  /// [prefix] is used to filter incoming messages, and [regexPattern] is used to extract metadata.
  Future<void> configureReceivedSms({
    required String prefix,
    required String regexPattern,
  }) async {
    if (kIsWeb || !Platform.isAndroid) return Future.value();
    return platform.initializeSmsReception(
      messagePrefix: prefix,
      regexPattern: regexPattern,
    );
  }

  /// Reports a new incoming call triggered by a push notification.
  ///
  /// Returns a [CallkeepIncomingCallError] if reporting fails.
  Future<CallkeepIncomingCallError?> reportNewIncomingCall(
    String callId,
    CallkeepHandle handle, {
    String? displayName,
    bool hasVideo = false,
  }) {
    return platform.incomingCallPushNotificationService(callId, handle, displayName, hasVideo);
  }
}
