// TODO: Rename to CallkeepBackgroundDelegate
abstract class CallkeepAndroidServiceDelegate {
  void performServiceEndCall(
    String callId,
  );

  void endCallReceived(
    String callId,
    final String number,
    final bool video,
    final DateTime createdTime,
    final DateTime? acceptedTime,
    final DateTime? hungUpTime,
  );
}
