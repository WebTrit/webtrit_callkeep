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
  ///
  /// Deprecated: pass [CallkeepAndroidOptions.nativeLogFilePath] to [Callkeep.setUp] instead.
  @Deprecated('Use CallkeepAndroidOptions.nativeLogFilePath in setUp() instead.')
  void setLogsDelegate(CallkeepLogsDelegate? delegate) {
    if (kIsWeb || !Platform.isAndroid) {
      return;
    }

    // ignore: deprecated_member_use
    WebtritCallkeepPlatform.instance.setLogsDelegate(delegate);
  }
}
