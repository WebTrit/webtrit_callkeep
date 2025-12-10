import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

/// A utility class for gathering system diagnostics and reports.
///
/// This includes device information, service states, permission statuses,
/// and logs of recent failed calls.
class CallkeepDiagnostics {
  /// The singleton constructor of [CallkeepDiagnostics].
  factory CallkeepDiagnostics() => _instance;

  CallkeepDiagnostics._();

  static final _instance = CallkeepDiagnostics._();

  /// The [WebtritCallkeepPlatform] instance used to perform platform specific operations.
  static WebtritCallkeepPlatform get platform => WebtritCallkeepPlatform.instance;

  /// Retrieves a detailed diagnostic report from the native side.
  ///
  /// Includes device info, permissions status, telecom registration status, etc.
  ///
  /// [callId] is optional. If provided, the report may include context-specific info.
  ///
  /// Returns `null` on non-Android platforms or Web.
  Future<Map<String, dynamic>> getDiagnosticReport() {
    if (kIsWeb || !Platform.isAndroid) {
      return Future.value({});
    }

    return platform.getDiagnosticReport();
  }
}
