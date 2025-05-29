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

  /// Cleans up all Callkeep connections.
  ///
  /// If the platform is web or not Android, it returns immediately.
  /// Otherwise, it performs the platform-specific cleanup of connections.
  Future<void> cleanConnections() {
    if (kIsWeb || !Platform.isAndroid) {
      return Future.value();
    }

    return platform.cleanConnections();
  }

  /// Retrieves a list of all active Callkeep connections.
  ///
  /// Returns a [Future] that resolves to a list of [CallkeepConnection] objects representing
  /// the active connections.
  Future<List<CallkeepConnection>> getConnections() {
    if (kIsWeb || !Platform.isAndroid) {
      return Future.value([]);
    }

    return platform.getConnections();
  }

  /// Updates the signaling status of the activity connection.
  ///
  /// Set the signaling status for the current activity connection,
  /// represented by the [CallkeepSignalingStatus] enum.
  Future<void> updateActivitySignalingStatus(CallkeepSignalingStatus status) {
    if (kIsWeb || !Platform.isAndroid) {
      return Future.value();
    }

    return platform.updateActivitySignalingStatus(status);
  }
}
