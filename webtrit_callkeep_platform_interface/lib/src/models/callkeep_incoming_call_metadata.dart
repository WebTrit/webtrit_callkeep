import 'callkeep_handle.dart';

/// Metadata describing an incoming call that was received via push notification.
class CallkeepIncomingCallMetadata {
  const CallkeepIncomingCallMetadata({required this.callId, this.handle, this.displayName, this.hasVideo = false});

  /// Unique identifier of the incoming call.
  final String callId;

  /// The handle (e.g. phone number) of the incoming call.
  final CallkeepHandle? handle;

  /// Optional display name of the caller.
  final String? displayName;

  /// Whether the incoming call has video.
  final bool hasVideo;

  @override
  String toString() =>
      'CallkeepIncomingCallMetadata(callId: $callId, handle: $handle, displayName: $displayName, hasVideo: $hasVideo)';
}
