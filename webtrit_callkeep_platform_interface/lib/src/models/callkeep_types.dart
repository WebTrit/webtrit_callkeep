import 'callkeep_incoming_call_metadata.dart';
import 'callkeep_service_status.dart';
import 'callkeep_push_notification_status_sync.dart';

/// A callback function that gets triggered when the foreground service starts.
///
/// [callkeepServiceStatus] - Provides the current status of the Callkeep service.
/// [metadata] - Provides the metadata of the incoming call that started the isolate, if available.
/// Returns a [Future] that completes when the service has started successfully.
typedef ForegroundStartServiceHandle =
    Future<void> Function(CallkeepServiceStatus callkeepServiceStatus, CallkeepIncomingCallMetadata? metadata);

/// A callback function that gets triggered when there is a change in the push notification sync status.
///
/// [status] - Provides the current status of the Callkeep push notification sync.
/// [metadata] - Provides the metadata of the incoming call that started the isolate, if available.
///
/// Returns a [Future] that completes after handling the status change.
typedef CallKeepPushNotificationSyncStatusHandle =
    Future<void> Function(CallkeepPushNotificationSyncStatus status, CallkeepIncomingCallMetadata? metadata);
