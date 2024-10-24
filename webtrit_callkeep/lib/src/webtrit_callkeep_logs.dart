import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

// TODO
// - convert to static abstract

/// The [WebtritCallkeepLogs] class is used to set the logs delegate.
/// The logs delegate is used to receive logs from the native side.
class WebtritCallkeepLogs {
  /// The singleton constructor of [WebtritCallkeepLogs].
  factory WebtritCallkeepLogs() => _instance;

  WebtritCallkeepLogs._();

  static final _instance = WebtritCallkeepLogs._();

  /// Sets the logs delegate.
  /// [CallkeepLogsDelegate] needs to be implemented to receive logs.
  void setLogsDelegate(CallkeepLogsDelegate? delegate) {
    if (kIsWeb) {
      return;
    }

    if (!Platform.isAndroid) {
      return;
    }

    WebtritCallkeepPlatform.instance.setLogsDelegate(delegate);
  }
}
