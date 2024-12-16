import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// The [CallkeepConnections] class is used to set the sound playback delegate.
class CallkeepConnections {
  /// The singleton constructor of [CallkeepConnections].
  factory CallkeepConnections() => _instance;

  CallkeepConnections._();

  static final _instance = CallkeepConnections._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Get the connection details for the given [callId].
  ///
  /// Returns a [Future] resolving to a [CallkeepConnection] if found, or null otherwise.
  Future<CallkeepConnection?> getConnection(String callId) {
    if (kIsWeb || !Platform.isAndroid) {
      return Future.value();
    }

    return platform.getConnection(callId);
  }
}
