import 'package:mockito/mockito.dart';
import 'package:webtrit_callkeep_platform_interface/src/delegate/delegate.dart';

class WebtritCallkeepDelegateAndroidRelayMock extends Mock implements CallkeepBackgroundServiceDelegate {
  WebtritCallkeepDelegateAndroidRelayMock({this.performServiceEndCallListener, this.endCallReceivedListener});

  final void Function(
    String callId,
  )? performServiceEndCallListener;

  final void Function(
    String callId,
    String number,
    bool video,
    DateTime createdTime,
    DateTime? acceptedTime,
    DateTime? hungUpTime,
  )? endCallReceivedListener;

  void performServiceEndCall(
    String callId,
  ) {
    performServiceEndCallListener?.call(callId);
  }

  void endCallReceived(
    String callId,
    String number,
    DateTime createdTime,
    DateTime? acceptedTime,
    DateTime? hungUpTime, {
    bool video = false,
  }) {
    endCallReceivedListener?.call(callId, number, video, createdTime, acceptedTime, hungUpTime);
  }
}
