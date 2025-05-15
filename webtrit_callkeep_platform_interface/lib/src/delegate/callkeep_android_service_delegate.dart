/// Callkeep background call delegate
abstract class CallkeepBackgroundServiceDelegate {
  /// Perform background answer
  void performAnswerCall(String callId);

  /// Perform background call end
  void performEndCall(String callId);

  /// On call end received
  void performReceivedCall(
    String callId,
    String number,
    DateTime createdTime,
    String? displayName,
    DateTime? acceptedTime,
    DateTime? hungUpTime, {
    bool video = false,
  });
}
