/// Callkeep background call delegate
abstract class CallkeepBackgroundServiceDelegate {
  /// Perform background answer
  void performAnswerCall(String callId);

  /// Perform background call end
  void performEndCall(String callId);
}
