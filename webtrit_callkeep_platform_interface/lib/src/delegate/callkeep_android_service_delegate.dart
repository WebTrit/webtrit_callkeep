/// Callkeep background call delegate
abstract class CallkeepBackgroundServiceDelegate {
  /// Perform background call end
  void performServiceEndCall(String callId);

  /// Perform background answer
  void performServiceAnswerCall(String callId);

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
