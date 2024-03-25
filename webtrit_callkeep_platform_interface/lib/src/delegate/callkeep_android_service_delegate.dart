// TODO: Rename to CallkeepBackgroundDelegate

/// Callkeep background call delegate
abstract class CallkeepAndroidServiceDelegate {
  /// Perform background call end
  void performServiceEndCall(String callId);

  /// On call end received
  void endCallReceived(
    String callId,
    String number,
    DateTime createdTime,
    DateTime? acceptedTime,
    DateTime? hungUpTime, {
    bool video = false,
  });
}
