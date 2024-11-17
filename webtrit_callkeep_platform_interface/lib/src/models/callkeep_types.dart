import 'callkeep_service_status.dart';

/// A callback function that gets triggered when the foreground service starts.
///
/// [callkeepServiceStatus] - Provides the current status of the Callkeep service.
/// Returns a [Future] that completes when the service has started successfully.
typedef ForegroundStartServiceHandle = Future<void> Function(CallkeepServiceStatus callkeepServiceStatus);

/// A callback function that gets triggered when there is a change in the lifecycle of the foreground service.
///
/// [callkeepServiceStatus] - Provides the current status of the Callkeep service when the lifecycle changes.
///
/// Returns a [Future] that completes after handling the lifecycle change.
typedef ForegroundChangeLifecycleHandle = Future<void> Function(
  CallkeepServiceStatus callkeepServiceStatus,
);
