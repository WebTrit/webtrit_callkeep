import 'callkeep_incoming_call_metadata.dart';
import 'callkeep_service_status.dart';

/// A callback function that gets triggered when the foreground service starts.
///
/// [callkeepServiceStatus] - Provides the current status of the Callkeep service.
/// [metadata] - Provides the metadata of the incoming call that started the isolate, if available.
/// Returns a [Future] that completes when the service has started successfully.
typedef ForegroundStartServiceHandle =
    Future<void> Function(CallkeepServiceStatus callkeepServiceStatus, CallkeepIncomingCallMetadata? metadata);

/// A callback function that gets triggered when a push notification sync is requested.
///
/// [metadata] - Provides the metadata of the incoming call that started the isolate, if available.
///
/// Returns a [Future] that completes after handling the sync, including all cleanup.
typedef CallKeepPushNotificationSyncStatusHandle = Future<void> Function(CallkeepIncomingCallMetadata? metadata);

/// A callback function that gets triggered when there is a change in the lifecycle of the foreground service.
///
/// [callkeepServiceStatus] - Provides the current status of the Callkeep service when the lifecycle changes.
///
/// Returns a [Future] that completes after handling the lifecycle change.
typedef ForegroundChangeLifecycleHandle = Future<void> Function(CallkeepServiceStatus callkeepServiceStatus);
