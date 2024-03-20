import 'package:webtrit_callkeep_platform_interface/src/delegate/delegate.dart';

class WebtritCallkeepDelegateAndroidRelayMock implements CallkeepAndroidServiceDelegate {
  WebtritCallkeepDelegateAndroidRelayMock({
    this.performServiceEndCallListener,
    this.endCallReceivedListener,
  });

  final Function(
    String callId,
  )? performServiceEndCallListener;

  final Function(
    String callId,
    String number,
    bool video,
    DateTime createdTime,
    DateTime? acceptedTime,
    DateTime? hungUpTime,
  )? endCallReceivedListener;

  @override
  void performServiceEndCall(
    String callId,
  ) {
    performServiceEndCallListener?.call(callId);
  }

  @override
  void endCallReceived(
    String callId,
    String number,
    bool video,
    DateTime createdTime,
    DateTime? acceptedTime,
    DateTime? hungUpTime,
  ) {
    endCallReceivedListener?.call(callId, number, video, createdTime, acceptedTime, hungUpTime);
  }
}
