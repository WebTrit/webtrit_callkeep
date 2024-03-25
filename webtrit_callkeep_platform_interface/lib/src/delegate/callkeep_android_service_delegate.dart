// TODO: Rename to CallkeepBackgroundDelegate
abstract class CallkeepAndroidServiceDelegate {
  void performServiceEndCall(
    String callId,
  );

  void endCallReceived(
    String callId,
    String number,
    DateTime createdTime,
    DateTime? acceptedTime,
    DateTime? hungUpTime, {
    bool video = false,
  });
}
